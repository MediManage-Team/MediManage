import os
import logging
import numpy as np
import time

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class AIInferenceEngine:
    def __init__(self):
        self.model = None
        self.tokenizer = None
        self.provider = None
        self.framework = None

    def load_model(self, model_path, hardware_config="auto"):
        """
        Loads the model based on the hardware configuration.
        hardware_config: "auto", "openvino", "directml", "cuda", "cpu"
        """
        logger.info(f"Loading model from {model_path} with config: {hardware_config}")
        
        if not os.path.exists(model_path):
            raise FileNotFoundError(f"Model file not found: {model_path}")

        # --- Framework Detection ---
        if model_path.endswith(".xml"):
            self.framework = "openvino"
        # Check if directory contains genai_config.json (GenAI) vs simple .onnx file
        elif os.path.isdir(model_path) and os.path.exists(os.path.join(model_path, "genai_config.json")):
             self.framework = "genai"
        elif model_path.endswith(".onnx") or os.path.isdir(model_path):
            # Fallback for standard ONNX or directory without genai config
            self.framework = "onnx_standard"
        else:
             raise ValueError("Unsupported model format. Use OpenVINO (.xml) or ONNX GenAI folder.")

        try:
            # --- ONNX Runtime GenAI (DirectML / CUDA / CPU) ---
            if self.framework == "genai":
                import onnxruntime_genai as og
                
                logger.info("Using ONNX Runtime GenAI...")
                
                # Execution Provider Selection
                ep = "cpu" # Default
                if hardware_config == "directml" or (hardware_config == "auto" and "dml" in og.Model.available_providers()):
                     ep = "dml"
                elif hardware_config == "cuda" or (hardware_config == "auto" and "cuda" in og.Model.available_providers()):
                     ep = "cuda"
                
                logger.info(f"Loading GenAI model with provider: {ep}")
                self.model = og.Model(model_path)
                self.tokenizer = og.Tokenizer(self.model)
                self.provider = f"GenAI:{ep}"
                
                # Create generator params
                self.gen_params = og.GeneratorParams(self.model)
                self.gen_params.set_search_options(do_sample=False) # Greedy for speed/determinism
                
            # --- OpenVINO (Intel CPU / GPU / NPU) ---
            elif self.framework == "openvino":
                from openvino.runtime import Core
                from transformers import AutoTokenizer
                
                core = Core()
                devices = core.available_devices
                logger.info(f"Available OpenVINO devices: {devices}")
                
                device = "CPU"
                if hardware_config == "openvino" or hardware_config == "auto":
                    if "NPU" in devices: device = "NPU"
                    elif "GPU" in devices: device = "GPU"
                
                # Note: For full LLM in OpenVINO, we typically use optimum-intel or openvino-genai.
                # Here we stick to basic OpenVINO Runtime + HuggingFace Tokenizer for simplicity if using raw .xml,
                # BUT standard .xml export usually means separate encoder/decoder.
                # Assuming the user provides a directory with openvino_model.xml optimized via optimum.
                # For this implementation, strictly pointing to a locally available LLM is tricky without optimum.
                # We will support a simplified version or assume 'optimum-intel' usage if we had that dependency.
                # Given requirements.txt doesn't have optimum, we'll try basic loading.
                # REVISION: To support "Ryzen AI" fully via OpenVINO, we need the NPU plugin.
                
                logger.info(f"Loading OpenVINO model on {device}...")
                self.model = core.read_model(model_path)
                self.compiled_model = core.compile_model(self.model, device_name=device)
                self.provider = f"OpenVINO:{device}"
                
                # Tokenizer usually lives with the model files in HF format
                # We assume model_path is a file, we need directory for tokenizer
                model_dir = os.path.dirname(model_path)
                try:
                    self.tokenizer = AutoTokenizer.from_pretrained(model_dir)
                except:
                    logger.warning("Could not load local tokenizer, defaulting to base behaviour (might fail).")

            # --- Standard ONNX (Fallback) ---
            elif self.framework == "onnx_standard":
                import onnxruntime as ort
                from transformers import AutoTokenizer # Needed for tokenization
                
                providers = []
                if hardware_config == "directml" or hardware_config == "auto":
                    providers.append("DmlExecutionProvider")
                providers.append("CPUExecutionProvider")
                
                self.session = ort.InferenceSession(model_path, providers=providers)
                self.provider = self.session.get_providers()[0]
                
                # Tokenizer guess
                try:
                    self.tokenizer = AutoTokenizer.from_pretrained(os.path.dirname(model_path))
                except:
                    pass

        except Exception as e:
            logger.error(f"Failed to load model: {e}")
            raise e

    def search_web(self, query):
        try:
            from duckduckgo_search import DDGS
            with DDGS() as ddgs:
                results = list(ddgs.text(query, max_results=3))
            return "\n".join([f"- {r['title']}: {r['body']}" for r in results])
        except Exception as e:
            logger.error(f"Search failed: {e}")
            return "Search unavailable."

    def generate(self, prompt, max_tokens=2000, use_search=False):
        """
        Runs inference.
        """
        context = ""
        if use_search:
            logger.info(f"Performing web search for: {prompt}")
            search_results = self.search_web(prompt)
            context = f"\n\n[Web Search Results]:\n{search_results}\n"
        
        full_prompt = f"User: {prompt}\n{context}\nAssistant:"

        if not self.provider:
             return "Error: Model not loaded."

        try:
            # --- GenAI Inference ---
            if self.framework == "genai":
                import onnxruntime_genai as og
                
                input_ids = self.tokenizer.encode(full_prompt)
                self.gen_params.set_input_sequences(input_ids)
                self.gen_params.set_max_length(max_tokens)
                
                output_ids = self.model.generate(self.gen_params)[0]
                decoded_output = self.tokenizer.decode(output_ids)
                
                # Remove prompt from output if GenAI includes it
                if decoded_output.startswith(full_prompt):
                    return decoded_output[len(full_prompt):].strip()
                return decoded_output

            # --- OpenVINO / Standard ONNX Inference (Simplified) ---
            # Implementing full decoding loop for raw ONNX/OpenVINO is complex (greedy search loop).
            # For this MVP, we acknowledge GenAI is the preferred path for Local LLM.
            # We will return a placeholder for non-GenAI paths unless using HuggingFace pipeline which is heavy.
            else:
                 return f"Backend {self.framework} loaded, but raw inference loop not implemented. Please use ONNX Runtime GenAI format for full LLM support."
                 
        except Exception as e:
            logger.error(f"Inference failed: {e}")
            return f"Error during generation: {str(e)}"

engine = AIInferenceEngine()
