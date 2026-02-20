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
        # Verify DirectML EP is actually available in the runtime
        dml_available = False
        try:
            import onnxruntime as ort
            dml_available = "DmlExecutionProvider" in ort.get_available_providers()
        except ImportError:
            pass

        return {
            "backend": "directml" if dml_available else "cpu",
            "device_name": amd["name"],
            "vram_mb": amd.get("vram_mb", 0),
            "dml_available": dml_available,
            "reason": f"AMD iGPU detected: {amd['name']}" + (
                " (DirectML accelerated)" if dml_available else " (CPU fallback — DirectML not installed)")
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
             "Get-CimInstance Win32_VideoController | Where-Object {$_.Name -like '*AMD*' -or $_.Name -like '*Radeon*'} | Select-Object -First 1 -ExpandProperty Name"],
            capture_output=True, text=True, timeout=15
        )
        if result.returncode == 0 and result.stdout.strip():
            return {"name": result.stdout.strip()}
    except Exception:
        pass

    # Check for Ryzen AI NPU
    try:
        result = subprocess.run(
            ["powershell", "-Command",
             "Get-CimInstance Win32_PnPEntity | Where-Object {$_.Name -like '*NPU*' -or $_.Name -like '*Ryzen AI*'} | Select-Object -First 1 -ExpandProperty Name"],
            capture_output=True, text=True, timeout=15
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
                 "(Get-CimInstance Win32_Processor).Name"],
                capture_output=True, text=True, timeout=15
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


# ======================== AMD NPU GENERATION DETECTION ========================

# XDNA 1: Ryzen 7x40, 8x40 series (Phoenix, Hawk Point) — ~15 TOPS NPU
# XDNA 2: Ryzen AI 300 series (Strix Point, Krackan Point) — 45+ TOPS NPU

_XDNA1_PATTERNS = [
    "7640", "7640HS", "7640U",
    "7840", "7840HS", "7840U",
    "7940", "7940HS",
    "8640", "8640HS", "8640U",
    "8840", "8840HS", "8840U",
    "8845", "8845HS",
    "8940", "8940HS",
    "Z1",  # ASUS ROG Ally (Phoenix)
]

_XDNA2_PATTERNS = [
    "AI 9 HX 370", "AI 9 HX 375", "AI 9 365", "AI 9 HX 395",
    "AI 7 PRO 360", "AI 7 350",
    "AI 5 PRO 340", "AI 5 340",
    "Ryzen AI 300",  # Generic match
    "Strix",         # Codename match
    "Krackan",       # Codename match
]


def detect_npu_generation():
    """
    Detect AMD XDNA NPU generation from CPU model name.

    Returns dict with:
        - generation: "xdna1" | "xdna2" | None
        - python_version: "3.10" | "3.12"
        - oga_package: pip package name + version
        - numpy_constraint: numpy version constraint
        - recommended_model: HuggingFace repo ID for best model
        - npu_tops: approximate NPU performance
        - cpu_name: detected CPU model
    """
    cpu_name = ""
    if platform.system() == "Windows":
        try:
            result = subprocess.run(
                ["powershell", "-Command",
                 "(Get-CimInstance Win32_Processor).Name"],
                capture_output=True, text=True, timeout=15
            )
            if result.returncode == 0:
                cpu_name = result.stdout.strip()
        except Exception:
            pass

    if not cpu_name:
        cpu_name = platform.processor() or ""

    # Check XDNA 2 first (newer, more specific patterns)
    for pattern in _XDNA2_PATTERNS:
        if pattern.lower() in cpu_name.lower():
            return {
                "generation": "xdna2",
                "python_version": "3.12",
                "oga_package": "onnxruntime-genai-directml-ryzenai==0.11.2",
                "numpy_constraint": "numpy",
                "recommended_model": "amd/Phi-4-mini-instruct-awq-g128-int4-asym-fp16-onnx-hybrid",
                "npu_tops": 50,
                "cpu_name": cpu_name,
                "requirements_suffix": "xdna2",
            }

    # Check XDNA 1
    for pattern in _XDNA1_PATTERNS:
        if pattern.lower() in cpu_name.lower():
            return {
                "generation": "xdna1",
                "python_version": "3.10",
                "oga_package": "onnxruntime-genai-directml-ryzenai==0.7.0.3",
                "numpy_constraint": "numpy<2",
                "recommended_model": "microsoft/Phi-4-mini-instruct-onnx",
                "npu_tops": 15,
                "cpu_name": cpu_name,
                "requirements_suffix": "xdna1",
            }

    # Not an AMD AI processor
    return {
        "generation": None,
        "python_version": "3.10",
        "oga_package": "onnxruntime-genai-directml",
        "numpy_constraint": "numpy",
        "recommended_model": None,
        "npu_tops": 0,
        "cpu_name": cpu_name,
        "requirements_suffix": None,
    }


def get_npu_setup_info():
    """
    Get full NPU setup information for the /npu_info endpoint.
    Combines NPU generation detection with existing hardware detection.
    """
    npu_gen = detect_npu_generation()
    hw = detect_hardware()

    # Check if VitisAI EP is already available
    vitisai_available = False
    oga_version = None
    try:
        import onnxruntime as ort
        vitisai_available = "VitisAIExecutionProvider" in ort.get_available_providers()
    except ImportError:
        pass
    try:
        import onnxruntime_genai as og
        oga_version = og.__version__
    except ImportError:
        pass

    return {
        **npu_gen,
        "hardware_backend": hw["backend"],
        "device_name": hw["device_name"],
        "vitisai_available": vitisai_available,
        "oga_installed_version": oga_version,
        "setup_complete": vitisai_available and oga_version is not None,
    }
