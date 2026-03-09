"""
Multi-source download manager with real progress tracking.

Supports:
- HuggingFace direct file downloads (NOT snapshot_download - that breaks progress)
- Ollama registry model pulls
- Direct HTTP/HTTPS URLs

Features:
- Real byte-level progress with percentage, speed, ETA
- Multi-threaded chunk downloads for speed
- Resume support
- Cancellation support
"""

import os
import time
import hashlib
import requests
import threading
import logging
from concurrent.futures import ThreadPoolExecutor, as_completed

logger = logging.getLogger(__name__)

# Shared cancellation event (set by server.py)
_cancel_event = None


def set_cancel_event(event):
    """Set the cancellation threading.Event from server.py."""
    global _cancel_event
    _cancel_event = event


def _check_cancel():
    if _cancel_event and _cancel_event.is_set():
        raise InterruptedError("Download cancelled by user")


class DownloadProgress:
    """Thread-safe progress tracker."""
    def __init__(self, callback):
        self._lock = threading.Lock()
        self._callback = callback
        self.total_bytes = 0
        self.downloaded_bytes = 0
        self.file_count = 0
        self.files_done = 0
        self.current_file = ""
        self.start_time = time.time()

    def set_total(self, total_bytes, file_count=1):
        with self._lock:
            self.total_bytes = total_bytes
            self.file_count = file_count
            self.start_time = time.time()

    def update(self, chunk_size, filename=""):
        with self._lock:
            self.downloaded_bytes += chunk_size
            if filename:
                self.current_file = filename
            self._report()

    def file_done(self):
        with self._lock:
            self.files_done += 1
            self._report()

    def _report(self):
        elapsed = time.time() - self.start_time
        speed = self.downloaded_bytes / elapsed if elapsed > 0 else 0

        if self.total_bytes > 0:
            pct = (self.downloaded_bytes / self.total_bytes) * 100
            eta = (self.total_bytes - self.downloaded_bytes) / speed if speed > 0 else 0
            eta_str = f"{int(eta)}s" if eta < 120 else f"{int(eta/60)}m"
        else:
            pct = 0
            eta_str = "?"

        speed_str = _format_speed(speed)
        size_str = f"{self.downloaded_bytes / 1024 / 1024:.1f} MB / {self.total_bytes / 1024 / 1024:.1f} MB"
        file_str = f" | File {self.files_done + 1}/{self.file_count}" if self.file_count > 1 else ""

        self._callback({
            "status": "downloading",
            "percent": round(float(pct), 1),
            "message": f"{round(float(pct), 1)}%  •  {size_str}{file_str}  •  {speed_str}  •  ETA: {eta_str}",
            "speed": speed_str
        })


def _format_speed(bps):
    if bps > 1024 * 1024:
        return f"{bps / 1024 / 1024:.1f} MB/s"
    elif bps > 1024:
        return f"{bps / 1024:.0f} KB/s"
    else:
        return f"{bps:.0f} B/s"


# ======================== HUGGINGFACE DIRECT DOWNLOAD ========================

def download_hf_model(repo_id, filename, local_dir, progress_callback):
    """Download from HuggingFace using direct HTTP (not snapshot_download).
    
    Args:
        repo_id: e.g. "microsoft/Phi-3-mini-4k-instruct-onnx"
        filename: e.g. "*.onnx" or specific file, or "" for all
        local_dir: where to save
        progress_callback: function(dict) for progress updates
    """
    progress = DownloadProgress(progress_callback)

    # Step 1: Get file list from HF API
    progress_callback({"status": "downloading", "percent": 0, "message": "Fetching model info from HuggingFace...", "speed": ""})
    files = _hf_list_files(repo_id, filename)

    if not files:
        raise Exception(f"No files found for {repo_id} matching '{filename}'")

    # Step 2: Resolve missing sizes via HEAD requests (HF API often returns 0)
    unknown_sizes = [f for f in files if f["size"] == 0]
    if unknown_sizes:
        progress_callback({"status": "downloading", "percent": 0,
                          "message": f"Resolving file sizes ({len(unknown_sizes)} files)...", "speed": ""})
                          
        hf_token = os.environ.get("HF_TOKEN", "").strip()
        headers = {}
        if hf_token:
            headers["Authorization"] = f"Bearer {hf_token}"
            
        for f in unknown_sizes:
            _check_cancel()
            try:
                head_url = f"https://huggingface.co/{repo_id}/resolve/main/{f['filename']}"
                head_resp = requests.head(head_url, headers=headers, allow_redirects=True, timeout=10)
                cl = int(head_resp.headers.get("content-length", 0))
                if cl > 0:
                    f["size"] = cl
                    logger.info(f"  Resolved size: {f['filename']} = {cl / 1024 / 1024:.1f} MB")
            except Exception:
                pass  # Will get real size during download

    # Step 3: Calculate total size
    total_size = sum(f["size"] for f in files)
    progress.set_total(total_size, len(files))

    model_name = repo_id.split("/")[-1]
    target_dir = os.path.join(local_dir, model_name)
    os.makedirs(target_dir, exist_ok=True)

    logger.info(f"Downloading {len(files)} files ({total_size / 1024 / 1024:.1f} MB) to {target_dir}")

    # Step 4: Download files (concurrent for small files, sequential for large)
    large_files = [f for f in files if f["size"] > 50 * 1024 * 1024]  # > 50MB
    small_files = [f for f in files if f["size"] <= 50 * 1024 * 1024]

    # Download small files concurrently
    if small_files:
        with ThreadPoolExecutor(max_workers=4) as executor:
            futures = {
                executor.submit(_download_single_file, repo_id, f, target_dir, progress): f  # type: ignore
                for f in small_files
            }
            for future in as_completed(futures):
                _check_cancel()
                future.result()  # Raise any exceptions

    # Download large files sequentially (to avoid memory issues)
    for f in large_files:
        _check_cancel()
        _download_single_file(repo_id, f, target_dir, progress)

    return target_dir


def _hf_list_files(repo_id, pattern=""):
    """Get file list with sizes from HuggingFace API."""
    api_url = f"https://huggingface.co/api/models/{repo_id}"

    hf_token = os.environ.get("HF_TOKEN", "").strip()
    headers = {}
    if hf_token:
        headers["Authorization"] = f"Bearer {hf_token}"

    try:
        resp = requests.get(api_url, headers=headers, timeout=15)
        resp.raise_for_status()
        data = resp.json()
    except Exception as e:
        logger.error(f"HF API error: {e}")
        raise Exception(f"Could not fetch model info from HuggingFace: {e}")

    siblings = data.get("siblings", [])
    files = []

    for s in siblings:
        fname = s.get("rfilename", "")
        size = s.get("size", 0) or 0

        # Filter by pattern
        if pattern and pattern != "*":
            if "*" in pattern:
                # Glob-like pattern match
                import fnmatch
                if not fnmatch.fnmatch(fname, pattern):
                    continue
            else:
                if fname != pattern:
                    continue

        files.append({
            "filename": fname,
            "size": size
        })

    return files


def _download_single_file(repo_id, file_info, target_dir, progress):
    """Download a single file from HuggingFace with streaming progress."""
    filename = file_info["filename"]
    _check_cancel()

    # Create subdirectories if needed
    filepath = os.path.join(target_dir, filename)
    os.makedirs(os.path.dirname(filepath), exist_ok=True)

    # Check resume
    existing_size = 0
    if os.path.exists(filepath):
        existing_size = os.path.getsize(filepath)
        if existing_size == file_info["size"] and file_info["size"] > 0:
            logger.info(f"  ⏭ Already downloaded: {filename}")
            progress.update(file_info["size"], filename)
            progress.file_done()
            return

    # Direct download URL
    url = f"https://huggingface.co/{repo_id}/resolve/main/{filename}"

    headers = {}
    hf_token = os.environ.get("HF_TOKEN", "").strip()
    if hf_token:
        headers["Authorization"] = f"Bearer {hf_token}"
        
    if existing_size > 0:
        headers["Range"] = f"bytes={existing_size}-"

    try:
        resp = requests.get(url, stream=True, timeout=30, headers=headers, allow_redirects=True)
        resp.raise_for_status()
    except requests.exceptions.HTTPError as e:
        if e.response.status_code == 416 and existing_size > 0:
            logger.warning(f"  ⚠ 416 Range Not Satisfiable for {filename}. Retrying full download.")
            headers.pop("Range", None)
            existing_size = 0
            resp = requests.get(url, stream=True, timeout=30, headers=headers, allow_redirects=True)
            resp.raise_for_status()
        else:
            logger.error(f"  ✗ Failed to download {filename}: {e}")
            raise
    except Exception as e:
        logger.error(f"  ✗ Failed to download {filename}: {e}")
        raise

    # If we still don't know the size, use Content-Length from the response
    content_length = int(resp.headers.get("content-length", 0))
    if file_info["size"] == 0 and content_length > 0:
        file_info["size"] = content_length
        # Dynamically increase total so percentage stays accurate
        with progress._lock:
            progress.total_bytes += content_length
        logger.info(f"  Resolved size on-the-fly: {filename} = {content_length / 1024 / 1024:.1f} MB")

    mode = "ab" if existing_size > 0 else "wb"
    if existing_size > 0:
        progress.update(existing_size, filename)

    with open(filepath, mode) as f:
        for chunk in resp.iter_content(chunk_size=1024 * 1024):  # 1MB chunks
            _check_cancel()
            if chunk:
                f.write(chunk)
                progress.update(len(chunk), filename)  # type: ignore

    progress.file_done()
    logger.info(f"  ✓ {filename} ({file_info['size'] / 1024 / 1024:.1f} MB)")


# ======================== OLLAMA REGISTRY DOWNLOAD ========================

def download_ollama_model(model_name, local_dir, progress_callback):
    """Download a model from Ollama registry.
    
    Ollama models are stored as layers (blobs) referenced by a manifest.
    Registry: https://registry.ollama.ai
    """
    progress = DownloadProgress(progress_callback)

    progress_callback({"status": "downloading", "percent": 0, "message": f"Fetching Ollama manifest for {model_name}...", "speed": ""})

    # Parse model name (e.g., "tinyllama:latest", "phi3:3.8b-mini-instruct-4k-q4_K_M")
    parts = model_name.split(":")
    name = parts[0]
    tag = parts[1] if len(parts) > 1 else "latest"

    # Add library/ prefix if needed
    if "/" not in name:
        name = f"library/{name}"

    # Step 1: Get manifest
    manifest_url = f"https://registry.ollama.ai/v2/{name}/manifests/{tag}"
    headers = {"Accept": "application/vnd.docker.distribution.manifest.v2+json"}

    try:
        resp = requests.get(manifest_url, headers=headers, timeout=15)
        resp.raise_for_status()
        manifest = resp.json()
    except Exception as e:
        raise Exception(f"Could not fetch Ollama manifest for '{model_name}': {e}")

    layers = manifest.get("layers", [])
    if not layers:
        raise Exception(f"No layers found in Ollama manifest for '{model_name}'")

    # Step 2: Calculate total and find model layer
    total_size = sum(layer.get("size", 0) for layer in layers)
    progress.set_total(total_size, len(layers))

    # Create target directory
    safe_name = model_name.replace(":", "_").replace("/", "_")
    target_dir = os.path.join(local_dir, safe_name)
    os.makedirs(target_dir, exist_ok=True)

    logger.info(f"Downloading Ollama model: {model_name} ({len(layers)} layers, {total_size / 1024 / 1024:.1f} MB)")

    # Step 3: Download each layer
    for layer in layers:
        _check_cancel()
        digest = layer["digest"]
        media_type = layer.get("mediaType", "")
        layer_size = layer.get("size", 0)

        # Determine filename from media type
        if "model" in media_type:
            out_filename = "model.gguf"
        elif "params" in media_type or "json" in media_type:
            out_filename = "params.json"
        elif "template" in media_type:
            out_filename = "template.txt"
        elif "system" in media_type:
            out_filename = "system.txt"
        elif "license" in media_type:
            out_filename = "LICENSE"
        else:
            out_filename = digest.replace(":", "_")

        filepath = os.path.join(target_dir, out_filename)

        # Check if already downloaded
        if os.path.exists(filepath) and os.path.getsize(filepath) == layer_size:
            logger.info(f"  ⏭ Already downloaded: {out_filename}")
            progress.update(layer_size, out_filename)
            progress.file_done()
            continue

        # Download blob
        blob_url = f"https://registry.ollama.ai/v2/{name}/blobs/{digest}"

        try:
            resp = requests.get(blob_url, stream=True, timeout=30)
            resp.raise_for_status()
        except Exception as e:
            logger.error(f"  ✗ Failed to download layer {out_filename}: {e}")
            raise

        with open(filepath, "wb") as f:
            for chunk in resp.iter_content(chunk_size=1024 * 1024):  # 1MB chunks
                _check_cancel()
                if chunk:
                    f.write(chunk)
                    progress.update(len(chunk), out_filename)  # type: ignore

        progress.file_done()
        logger.info(f"  ✓ {out_filename} ({layer_size / 1024 / 1024:.1f} MB)")

    return target_dir


# ======================== DIRECT URL DOWNLOAD ========================

def download_direct_url(url, local_dir, progress_callback):
    """Download a file from a direct URL with progress."""
    progress = DownloadProgress(progress_callback)

    filename = url.split("/")[-1].split("?")[0]
    filepath = os.path.join(local_dir, filename)
    os.makedirs(local_dir, exist_ok=True)

    progress_callback({"status": "downloading", "percent": 0, "message": f"Connecting to {url[:60]}...", "speed": ""})

    resp = requests.get(url, stream=True, timeout=30, allow_redirects=True)
    resp.raise_for_status()

    total = int(resp.headers.get("content-length", 0))
    progress.set_total(total)

    with open(filepath, "wb") as f:
        for chunk in resp.iter_content(chunk_size=1024 * 1024):
            _check_cancel()
            if chunk:
                f.write(chunk)
                progress.update(len(chunk), filename)  # type: ignore

    progress.file_done()
    return filepath
