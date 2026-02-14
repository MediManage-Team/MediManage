"""
Hardware detection for MediManage AI Engine.

Auto-detects: NVIDIA GPU (CUDA), AMD GPU/NPU (DirectML), Intel NPU (OpenVINO), CPU.
Returns the recommended backend configuration.
"""

import os
import platform
import logging
import subprocess

logger = logging.getLogger(__name__)


def detect_hardware():
    """
    Auto-detect available hardware and return the best backend config.
    
    Returns dict with:
        - backend: "cuda" | "directml" | "openvino" | "cpu"
        - device_name: e.g. "NVIDIA RTX 4090"
        - vram_mb: GPU memory in MB (0 for CPU)
        - reason: why this backend was chosen
    """
    # 1. Check NVIDIA GPU (CUDA)
    nvidia = _detect_nvidia()
    if nvidia:
        return {
            "backend": "cuda",
            "device_name": nvidia["name"],
            "vram_mb": nvidia["vram_mb"],
            "reason": f"NVIDIA GPU detected: {nvidia['name']} ({nvidia['vram_mb']} MB VRAM)"
        }

    # 2. Check AMD GPU / NPU (DirectML)
    amd = _detect_amd()
    if amd:
        return {
            "backend": "directml",
            "device_name": amd["name"],
            "vram_mb": amd.get("vram_mb", 0),
            "reason": f"AMD GPU/NPU detected: {amd['name']}"
        }

    # 3. Check Intel NPU (OpenVINO)
    intel = _detect_intel_npu()
    if intel:
        return {
            "backend": "openvino",
            "device_name": intel["name"],
            "vram_mb": 0,
            "reason": f"Intel NPU detected: {intel['name']}"
        }

    # 4. CPU fallback (BitNet.cpp / llama.cpp)
    cpu = _get_cpu_info()
    return {
        "backend": "cpu",
        "device_name": cpu["name"],
        "vram_mb": 0,
        "ram_mb": cpu["ram_mb"],
        "reason": f"No GPU/NPU found. Using CPU: {cpu['name']} (BitNet.cpp)"
    }


def get_hardware_info():
    """Get full system hardware info for display in the UI."""
    info = {
        "os": f"{platform.system()} {platform.release()}",
        "arch": platform.machine(),
        "python": platform.python_version(),
    }

    # CPU
    cpu = _get_cpu_info()
    info["cpu"] = cpu["name"]
    info["ram_mb"] = cpu["ram_mb"]
    info["cpu_cores"] = os.cpu_count() or 0

    # GPU
    nvidia = _detect_nvidia()
    if nvidia:
        info["gpu"] = nvidia["name"]
        info["gpu_vram_mb"] = nvidia["vram_mb"]
        info["gpu_type"] = "NVIDIA (CUDA)"
    else:
        amd = _detect_amd()
        if amd:
            info["gpu"] = amd["name"]
            info["gpu_vram_mb"] = amd.get("vram_mb", 0)
            info["gpu_type"] = "AMD (DirectML)"

    # Intel NPU
    intel = _detect_intel_npu()
    if intel:
        info["npu"] = intel["name"]
        info["npu_type"] = "Intel (OpenVINO)"

    # Best backend
    best = detect_hardware()
    info["recommended_backend"] = best["backend"]
    info["backend_reason"] = best["reason"]

    # Available backends
    backends = ["cpu"]
    try:
        import onnxruntime_genai
        backends.append("genai")
    except ImportError:
        pass
    try:
        import openvino
        backends.append("openvino")
    except ImportError:
        pass
    try:
        from llama_cpp import Llama
        backends.append("llama_cpp")
    except ImportError:
        pass
    info["available_backends"] = backends

    return info


# ==================== Internal Detectors ====================

def _detect_nvidia():
    """Detect NVIDIA GPU using nvidia-smi."""
    try:
        result = subprocess.run(
            ["nvidia-smi", "--query-gpu=name,memory.total", "--format=csv,noheader,nounits"],
            capture_output=True, text=True, timeout=5
        )
        if result.returncode == 0 and result.stdout.strip():
            line = result.stdout.strip().split("\n")[0]
            parts = line.split(",")
            name = parts[0].strip()
            vram = int(float(parts[1].strip())) if len(parts) > 1 else 0
            return {"name": name, "vram_mb": vram}
    except (FileNotFoundError, subprocess.TimeoutExpired, Exception):
        pass
    return None


def _detect_amd():
    """Detect AMD GPU / NPU via Windows WMI or environment."""
    if platform.system() != "Windows":
        return None

    try:
        # Check DirectML availability via DX adapter
        result = subprocess.run(
            ["powershell", "-Command",
             "Get-WmiObject Win32_VideoController | Where-Object {$_.Name -like '*AMD*' -or $_.Name -like '*Radeon*'} | Select-Object -First 1 -ExpandProperty Name"],
            capture_output=True, text=True, timeout=5
        )
        if result.returncode == 0 and result.stdout.strip():
            return {"name": result.stdout.strip()}
    except Exception:
        pass

    # Check for Ryzen AI NPU
    try:
        result = subprocess.run(
            ["powershell", "-Command",
             "Get-WmiObject Win32_PnPEntity | Where-Object {$_.Name -like '*NPU*' -or $_.Name -like '*Ryzen AI*'} | Select-Object -First 1 -ExpandProperty Name"],
            capture_output=True, text=True, timeout=5
        )
        if result.returncode == 0 and result.stdout.strip():
            return {"name": result.stdout.strip()}
    except Exception:
        pass

    return None


def _detect_intel_npu():
    """Detect Intel NPU for OpenVINO."""
    try:
        from openvino.runtime import Core
        core = Core()
        devices = core.available_devices
        if "NPU" in devices:
            return {"name": "Intel NPU (OpenVINO)"}
    except ImportError:
        pass

    # Fallback: check Windows device manager
    if platform.system() == "Windows":
        try:
            result = subprocess.run(
                ["powershell", "-Command",
                 "Get-WmiObject Win32_PnPEntity | Where-Object {$_.Name -like '*Intel*NPU*'} | Select-Object -First 1 -ExpandProperty Name"],
                capture_output=True, text=True, timeout=5
            )
            if result.returncode == 0 and result.stdout.strip():
                return {"name": result.stdout.strip()}
        except Exception:
            pass

    return None


def _get_cpu_info():
    """Get CPU name and RAM info."""
    cpu_name = platform.processor() or "Unknown CPU"

    # Try to get a better name on Windows
    if platform.system() == "Windows" and cpu_name in ("", "AMD64 Family", "Intel64 Family"):
        try:
            result = subprocess.run(
                ["powershell", "-Command",
                 "(Get-WmiObject Win32_Processor).Name"],
                capture_output=True, text=True, timeout=5
            )
            if result.returncode == 0 and result.stdout.strip():
                cpu_name = result.stdout.strip()
        except Exception:
            pass

    # RAM
    ram_mb = 0
    try:
        if platform.system() == "Windows":
            import ctypes
            kernel32 = ctypes.windll.kernel32
            class MEMORYSTATUSEX(ctypes.Structure):
                _fields_ = [
                    ("dwLength", ctypes.c_ulong),
                    ("dwMemoryLoad", ctypes.c_ulong),
                    ("ullTotalPhys", ctypes.c_ulonglong),
                    ("ullAvailPhys", ctypes.c_ulonglong),
                    ("ullTotalPageFile", ctypes.c_ulonglong),
                    ("ullAvailPageFile", ctypes.c_ulonglong),
                    ("ullTotalVirtual", ctypes.c_ulonglong),
                    ("ullAvailVirtual", ctypes.c_ulonglong),
                    ("ullAvailExtendedVirtual", ctypes.c_ulonglong),
                ]
            mem = MEMORYSTATUSEX()
            mem.dwLength = ctypes.sizeof(MEMORYSTATUSEX)
            kernel32.GlobalMemoryStatusEx(ctypes.byref(mem))
            ram_mb = int(mem.ullTotalPhys / 1024 / 1024)
        else:
            with open("/proc/meminfo") as f:
                for line in f:
                    if "MemTotal" in line:
                        ram_mb = int(line.split()[1]) // 1024
                        break
    except Exception:
        pass

    return {"name": cpu_name, "ram_mb": ram_mb}
