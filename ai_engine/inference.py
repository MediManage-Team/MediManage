import os
import logging
import time

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

try:
    from hardware_detect import detect_hardware, get_hardware_info
except ImportError:
    detect_hardware = None
    get_hardware_info = None


class AIInferenceEngine:
    def __init__(self):
        self.model = None
        self.tokenizer = None
        self.provider = None
        self.framework = None
        self._current_model_path = None

    @staticmethod
    def _find_genai_model_path(model_path, hardware_config="auto"):
        """
        Find the best model variant directory containing genai_config.json,
        ranked by available hardware acceleration.

        Priority (when GPU is available):
          1. gpu/ subdirectory (for CUDA or DirectML)
          2. cpu_and_mobile/ subdirectory
          3. Direct path with genai_config.json

        Priority (CPU-only or explicit CPU config):
          1. cpu_and_mobile/ subdirectory
          2. Direct path with genai_config.json
        """
        # Direct check — if genai_config.json is right here, use it
        if os.path.isfile(os.path.join(model_path, "genai_config.json")):
            return model_path

        # Collect all variant directories containing genai_config.json
        variants = []
        for root, dirs, files in os.walk(model_path):
            depth = root.replace(model_path, "").count(os.sep)
            if depth > 3:
                continue
            if "genai_config.json" in files:
                rel = os.path.relpath(root, model_path).lower()
                # Classify variant
                if "gpu" in rel and "cpu" not in rel:
                    kind = "gpu"
                elif "cpu" in rel or "mobile" in rel:
                    kind = "cpu"
                else:
                    kind = "other"
                variants.append((kind, root))

        if not variants:
            return None

        # Detect if GPU acceleration is possible
        gpu_available = False
        try:
            import onnxruntime as ort
            eps = ort.get_available_providers()
            gpu_available = ("CUDAExecutionProvider" in eps or
                            "DmlExecutionProvider" in eps)
        except ImportError:
            pass

        # Rank variants: GPU first if GPU available, CPU otherwise
        if gpu_available and hardware_config != "cpu":
            priority = {"gpu": 0, "other": 1, "cpu": 2}
        else:
            priority = {"cpu": 0, "other": 1, "gpu": 2}

        variants.sort(key=lambda v: priority.get(v[0], 99))
        best = variants[0][1]
        logger.info(f"Selected model variant: {os.path.relpath(best, model_path)} "
                    f"(gpu_available={gpu_available}, "
                    f"variants={[v[0] for v in variants]})")
        return best

    def load_model(self, model_path, hardware_config="auto"):
        """
        Loads the model based on the hardware configuration.
        hardware_config: "auto", "openvino", "directml", "cuda", "cpu", "bitnet"
        """
        logger.info(f"Loading model from {model_path} with config: {hardware_config}")

        if not os.path.exists(model_path):
            raise FileNotFoundError(f"Model file not found: {model_path}")

        self._current_model_path = model_path

        # --- Auto-detect hardware if needed ---
        if hardware_config == "auto" and detect_hardware:
            hw = detect_hardware()
            hardware_config = hw["backend"]
            logger.info(f"Auto-detected hardware: {hw['reason']}")

        # --- Framework Detection ---
        if model_path.endswith(".gguf") or (os.path.isdir(model_path) and any(
                f.endswith(".gguf") for f in os.listdir(model_path))):
            self.framework = "gguf"  # BitNet.cpp / llama.cpp
        elif model_path.endswith(".xml"):
            self.framework = "openvino"
        elif os.path.isdir(model_path) and self._find_genai_model_path(model_path, hardware_config):
            self.framework = "genai"
            # Resolve to the actual subdirectory containing genai_config.json
            model_path = self._find_genai_model_path(model_path, hardware_config)
            self._current_model_path = model_path
        elif model_path.endswith(".onnx") or os.path.isdir(model_path):
            self.framework = "onnx_standard"
        else:
            raise ValueError("Unsupported model format. Supported: GGUF, ONNX GenAI, OpenVINO (.xml), standard ONNX.")

        try:
            # --- GGUF / BitNet.cpp (CPU) ---
            if self.framework == "gguf":
                self._load_gguf(model_path, hardware_config)

            # --- ONNX Runtime GenAI (DirectML / CUDA / CPU) ---
            elif self.framework == "genai":
                self._load_genai(model_path, hardware_config)

            # --- OpenVINO (Intel CPU / GPU / NPU) ---
            elif self.framework == "openvino":
                self._load_openvino(model_path, hardware_config)

            # --- Standard ONNX (Fallback) ---
            elif self.framework == "onnx_standard":
                self._load_onnx_standard(model_path, hardware_config)

        except Exception as e:
            logger.error(f"Failed to load model: {e}")
            self._current_model_path = None
            raise e

    def _load_gguf(self, model_path, hardware_config):
        """Load GGUF model using llama-cpp-python (BitNet.cpp / llama.cpp)."""
        from llama_cpp import Llama

        # Find the .gguf file
        gguf_file = model_path
        if os.path.isdir(model_path):
            for f in os.listdir(model_path):
                if f.endswith(".gguf"):
                    gguf_file = os.path.join(model_path, f)
                    break

        logger.info(f"Loading GGUF model: {gguf_file}")

        # Detect thread count for CPU
        n_threads = os.cpu_count() or 4
        # Use half of available cores for inference
        n_threads = max(2, n_threads // 2)

        self.model = Llama(
            model_path=gguf_file,
            n_ctx=4096,          # Context window
            n_threads=n_threads, # CPU threads
            n_gpu_layers=0,      # Pure CPU (BitNet)
            verbose=False
        )

        self.tokenizer = None  # llama.cpp handles tokenization internally
        self.provider = f"llama.cpp:CPU({n_threads}t)"
        logger.info(f"GGUF model loaded on CPU with {n_threads} threads")

    def _load_genai(self, model_path, hardware_config):
        """Load model using ONNX Runtime GenAI (AMD NPU/VitisAI, DirectML, CUDA, CPU)."""
        import onnxruntime_genai as og

        logger.info("Using ONNX Runtime GenAI...")

        # Detect available execution providers via onnxruntime
        available_eps = []
        try:
            import onnxruntime as ort
            available_eps = ort.get_available_providers()
            logger.info(f"Available ONNX Runtime EPs: {available_eps}")
        except ImportError:
            logger.warning("onnxruntime not available for EP detection")

        # Determine execution provider priority based on model variant
        # Check if this is a GPU-compiled model (from the path)
        model_lower = model_path.lower()
        is_gpu_model = "gpu" in model_lower and "cpu" not in os.path.basename(model_lower)

        ep = "cpu"  # Default fallback
        if is_gpu_model:
            # GPU model variant — use actual GPU acceleration
            if "CUDAExecutionProvider" in available_eps:
                ep = "cuda"
                logger.info("CUDA EP detected — using NVIDIA GPU acceleration")
            elif "DmlExecutionProvider" in available_eps:
                ep = "dml"
                logger.info("DirectML detected — using GPU acceleration")
            else:
                logger.warning("GPU model variant selected but no GPU EP available, falling back to CPU")
        else:
            # CPU model variant — GPU EPs won't help, just run on CPU
            # VitisAI EP is registered but won't accelerate CPU-compiled models
            if "VitisAIExecutionProvider" in available_eps:
                logger.info("VitisAI EP available (NPU registered, model runs on CPU)")
            logger.info("CPU model variant — using CPU execution")

        logger.info(f"Loading GenAI model from: {model_path} with provider: {ep}")
        self.model = og.Model(model_path)
        self.tokenizer = og.Tokenizer(self.model)
        self.provider = f"GenAI:{ep}"
        logger.info(f"Model device type: {self.model.device_type}")

        self.gen_params = og.GeneratorParams(self.model)
        self.gen_params.set_search_options(do_sample=False)

    def _load_openvino(self, model_path, hardware_config):
        """Load model using OpenVINO (Intel CPU / GPU / NPU)."""
        from openvino.runtime import Core
        from transformers import AutoTokenizer

        core = Core()
        devices = core.available_devices
        logger.info(f"Available OpenVINO devices: {devices}")

        device = "CPU"
        if hardware_config == "openvino" or hardware_config == "auto":
            if "NPU" in devices:
                device = "NPU"
            elif "GPU" in devices:
                device = "GPU"

        logger.info(f"Loading OpenVINO model on {device}...")
        self.model = core.read_model(model_path)
        self.compiled_model = core.compile_model(self.model, device_name=device)
        self.provider = f"OpenVINO:{device}"

        model_dir = os.path.dirname(model_path)
        try:
            self.tokenizer = AutoTokenizer.from_pretrained(model_dir)
        except Exception:
            logger.warning("Could not load local tokenizer, will use base behaviour.")

    def _load_onnx_standard(self, model_path, hardware_config):
        """Load model using standard ONNX Runtime (DirectML / CUDA / CPU)."""
        import onnxruntime as ort
        from transformers import AutoTokenizer

        providers = []
        if hardware_config == "directml" or hardware_config == "auto":
            providers.append("DmlExecutionProvider")
        if hardware_config == "cuda" or hardware_config == "auto":
            providers.append("CUDAExecutionProvider")
        providers.append("CPUExecutionProvider")

        self.session = ort.InferenceSession(model_path, providers=providers)
        self.provider = self.session.get_providers()[0]

        try:
            self.tokenizer = AutoTokenizer.from_pretrained(os.path.dirname(model_path))
        except Exception:
            pass

    def search_web(self, query):
        """Search the web using DuckDuckGo for context augmentation."""
        try:
            from duckduckgo_search import DDGS
            with DDGS() as ddgs:
                results = list(ddgs.text(query, max_results=3))
            return "\n".join([f"- {r['title']}: {r['body']}" for r in results])
        except Exception as e:
            logger.error(f"Search failed: {e}")
            return "Search unavailable."

    def generate(self, prompt, max_tokens=2000, use_search=False):
        """Runs inference using the loaded model."""
        context = ""
        if use_search:
            logger.info(f"Performing web search for: {prompt}")
            search_results = self.search_web(prompt)
            context = f"\n\n[Web Search Results]:\n{search_results}\n"

        full_prompt = f"User: {prompt}\n{context}\nAssistant:"

        if not self.provider:
            return "Error: Model not loaded. Please load a model from Settings or Model Store."

        try:
            # --- GGUF / BitNet / llama.cpp Inference ---
            if self.framework == "gguf":
                response = self.model.create_completion(
                    full_prompt,
                    max_tokens=max_tokens,
                    temperature=0.7,
                    top_p=0.9,
                    stop=["User:", "\n\n"]
                )
                return response["choices"][0]["text"].strip()

            # --- GenAI Inference ---
            elif self.framework == "genai":
                import onnxruntime_genai as og
                import numpy as np

                input_ids = self.tokenizer.encode(full_prompt)

                # Re-create params each call to avoid stale state
                params = og.GeneratorParams(self.model)
                params.set_search_options(do_sample=False, max_length=max_tokens)

                # OGA 0.7.x uses set_model_input + Generator streaming loop
                # OGA 0.10+ uses set_input_sequences + model.generate
                if hasattr(params, 'set_input_sequences'):
                    # --- OGA 0.10+ path ---
                    params.set_input_sequences(input_ids)
                    output_ids = self.model.generate(params)[0]
                else:
                    # --- OGA 0.7.x path (streaming Generator) ---
                    generator = og.Generator(self.model, params)
                    generator.append_tokens(input_ids)
                    while not generator.is_done():
                        generator.generate_next_token()  # OGA 0.7.x: does logits + sampling
                    output_ids = generator.get_sequence(0)

                decoded_output = self.tokenizer.decode(output_ids)

                if decoded_output.startswith(full_prompt):
                    return decoded_output[len(full_prompt):].strip()
                return decoded_output

            # --- OpenVINO / Standard ONNX Inference ---
            else:
                return (f"Backend '{self.framework}' loaded on {self.provider}, "
                        f"but raw inference loop not implemented. "
                        f"Please use ONNX Runtime GenAI or GGUF format for full LLM support.")

        except Exception as e:
            logger.error(f"Inference failed: {e}")
            return f"Error during generation: {str(e)}"


def list_local_models(models_dir):
    """
    Scan the models directory and return a list of model entries.
    Each entry contains: name, path, format, size_mb.
    """
    models = []
    if not os.path.exists(models_dir):
        return models

    for entry in os.listdir(models_dir):
        entry_path = os.path.join(models_dir, entry)
        info = get_model_info(entry_path)
        if info:
            models.append(info)

    return models


def get_model_info(model_path):
    """
    Get metadata about a model at the given path.
    Returns dict with: name, path, format, size_mb.
    """
    if not os.path.exists(model_path):
        return None

    name = os.path.basename(model_path)
    info = {
        "name": name,
        "path": model_path,
        "format": "unknown",
        "size_mb": 0
    }

    if os.path.isdir(model_path):
        # Calculate total size
        total_size = 0
        all_files = []
        for root, dirs, files in os.walk(model_path):
            for f in files:
                fp = os.path.join(root, f)
                total_size += os.path.getsize(fp)
                all_files.append(f)

        info["size_mb"] = round(total_size / 1024 / 1024, 2)

        # Detect format by file contents
        if "genai_config.json" in all_files:
            info["format"] = "ONNX GenAI"
        elif any(f.endswith(".xml") for f in all_files):
            info["format"] = "OpenVINO"
        elif any(f.endswith(".onnx") for f in all_files):
            info["format"] = "ONNX"
        elif any(f.endswith(".gguf") for f in all_files):
            info["format"] = "GGUF"
        else:
            info["format"] = "HuggingFace"

    elif os.path.isfile(model_path):
        info["size_mb"] = round(os.path.getsize(model_path) / 1024 / 1024, 2)
        ext = os.path.splitext(model_path)[1].lower()
        format_map = {".xml": "OpenVINO", ".onnx": "ONNX", ".gguf": "GGUF"}
        info["format"] = format_map.get(ext, "unknown")

    return info


# Singleton engine instance
engine = AIInferenceEngine()
