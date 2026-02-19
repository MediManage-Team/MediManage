"""
download_gpu_model.py — One-click GPU model download for Phi-4 DirectML acceleration.

Usage:
  python download_gpu_model.py

Optional: Set HF_TOKEN environment variable for faster (authenticated) download.
  set HF_TOKEN=hf_xxxxxxx
  python download_gpu_model.py
"""
import os
import sys
import time


def main():
    try:
        from huggingface_hub import hf_hub_download, list_repo_tree
    except ImportError:
        print("Installing huggingface_hub...")
        os.system(f'"{sys.executable}" -m pip install huggingface_hub')
        from huggingface_hub import hf_hub_download, list_repo_tree

    repo_id = "microsoft/Phi-4-mini-instruct-onnx"
    subfolder = "gpu/gpu-int4-rtn-block-32"
    local_dir = os.path.join(os.path.expanduser("~"), "MediManage", "models",
                             "Phi-4-mini-instruct-onnx")

    # List GPU model files
    print(f"📦 Downloading GPU model variant from {repo_id}...")
    print(f"   Target: {local_dir}")
    print()

    files = list(list_repo_tree(repo_id, path_in_repo=subfolder))
    total_size = sum(f.size for f in files if hasattr(f, 'size') and f.size)
    print(f"   Total files: {len(files)}")
    print(f"   Total size:  {total_size / 1e9:.2f} GB")
    print()

    if os.environ.get("HF_TOKEN"):
        print("✅ HF_TOKEN detected — using authenticated download (faster)")
    else:
        print("⚠️  No HF_TOKEN set — unauthenticated download (slower)")
        print("   Tip: Get a free token at https://huggingface.co/settings/tokens")
        print(f"   Then: set HF_TOKEN=hf_your_token")
    print()

    # Download each file
    for i, f in enumerate(files, 1):
        if not hasattr(f, 'rfilename'):
            continue
        fname = f.rfilename
        size_mb = f.size / 1e6 if f.size else 0
        print(f"   [{i}/{len(files)}] {os.path.basename(fname)} ({size_mb:.1f} MB)...",
              end=" ", flush=True)
        t0 = time.time()
        hf_hub_download(repo_id, fname, local_dir=local_dir)
        elapsed = time.time() - t0
        print(f"✅ ({elapsed:.1f}s)")

    print()
    print("🎉 GPU model download complete!")
    print(f"   Path: {os.path.join(local_dir, subfolder)}")
    print()
    print("To use GPU acceleration, restart the AI engine server.")
    print("It will automatically select the GPU variant.")


if __name__ == "__main__":
    main()
