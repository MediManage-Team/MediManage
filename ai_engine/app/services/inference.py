import os
import logging
import time
import sys
import platform
from app.db.connector import get_readonly_connection, placeholder, fetchall_as_dicts # type: ignore
import re
from app.core.logger import configure_structured_logging # type: ignore

# Configure structured logging
configure_structured_logging()
logger = logging.getLogger(__name__)

try:
    from app.core.hardware import detect_hardware, get_hardware_info # type: ignore
except ImportError:
    detect_hardware = None
    get_hardware_info = None


def _ensure_cuda_dlls_on_path():
    """
    On Windows, NVIDIA pip packages (nvidia-cublas-cu12, nvidia-cudnn-cu12, etc.)
    install DLLs into site-packages/nvidia/*/bin/ which is NOT on os.environ['PATH'].
    Conda cuda-toolkit installs them into Library/bin/.
    OGA needs these DLLs at load time, so we add both locations to PATH.
    """
    if platform.system() != "Windows":
        return

    added = []
    # 1. Conda env Library/bin (cudart, cublas, cusolver, etc.)
    env_lib_bin = os.path.join(os.path.dirname(sys.executable), "Library", "bin")
    if os.path.isdir(env_lib_bin) and env_lib_bin not in os.environ.get("PATH", ""):
        os.environ["PATH"] = env_lib_bin + os.pathsep + os.environ["PATH"]
        added.append(env_lib_bin)

    # 2. pip nvidia packages (cudnn, cublas, etc.)
    site_packages = os.path.join(os.path.dirname(sys.executable), "Lib", "site-packages")
    nvidia_dir = os.path.join(site_packages, "nvidia")
    if os.path.isdir(nvidia_dir):
        for pkg in os.listdir(nvidia_dir):
            bin_dir = os.path.join(nvidia_dir, pkg, "bin")
            if os.path.isdir(bin_dir) and bin_dir not in os.environ.get("PATH", ""):
                os.environ["PATH"] = bin_dir + os.pathsep + os.environ["PATH"]
                added.append(bin_dir)

    if added:
        logger.info(f"Added {len(added)} CUDA DLL paths to PATH")


class AIInferenceEngine:
    def __init__(self):
        self.model = None
        self.tokenizer = None
        self.provider = None
        self.framework = ""
        self._current_model_path = ""
        self.session = None
        self.gen_params = None
        self.cancel_flag = False
        # Performance tracking
        self._last_tokens_generated = 0
        self._last_generation_time = 0.0
        self._last_tokens_per_sec = 0.0
        self._total_tokens_generated = 0
        self._total_generation_time = 0.0

    def clear_loaded_model(self):
        self.model = None
        self.tokenizer = None
        self.provider = None
        self.framework = ""
        self._current_model_path = ""
        self.session = None
        self.gen_params = None

    @staticmethod
    def _find_genai_model_path(model_path, hardware_config="auto"):
        """
        Find the best model variant directory containing genai_config.json,
        ranked by available hardware acceleration.

        Priority (when GPU is available):
          1. gpu/ subdirectory (for CUDA)
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
            import onnxruntime as ort  # type: ignore
            eps = ort.get_available_providers()
            gpu_available = "CUDAExecutionProvider" in eps
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
        hardware_config: "auto", "cuda", "cpu", "bitnet"
        """
        logger.info(f"Loading model from {model_path} with config: {hardware_config}")

        if not os.path.exists(model_path):
            raise FileNotFoundError(f"Model file not found: {model_path}")

        previous_state = (
            self.model,
            self.tokenizer,
            self.provider,
            self.framework,
            self._current_model_path,
            self.session,
            self.gen_params,
        )
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
        elif os.path.isdir(model_path) and self._find_genai_model_path(model_path, hardware_config):
            self.framework = "genai"
            # Resolve to the actual subdirectory containing genai_config.json
            model_path = self._find_genai_model_path(model_path, hardware_config)
            self._current_model_path = str(model_path)
        elif model_path.endswith(".onnx") or os.path.isdir(model_path):
            self.framework = "onnx_standard"
        else:
            raise ValueError("Unsupported model format. Supported: GGUF, ONNX GenAI, standard ONNX.")

        try:
            # --- GGUF / BitNet.cpp (CPU) ---
            if self.framework == "gguf":
                self._load_gguf(model_path, hardware_config)

            # --- ONNX Runtime GenAI (CUDA / CPU) ---
            elif self.framework == "genai":
                self._load_genai(model_path, hardware_config)

            # --- Standard ONNX (Fallback) ---
            elif self.framework == "onnx_standard":
                self._load_onnx_standard(model_path, hardware_config)

        except Exception as e:
            logger.error(f"Failed to load model: {e}")
            (
                self.model,
                self.tokenizer,
                self.provider,
                self.framework,
                self._current_model_path,
                self.session,
                self.gen_params,
            ) = previous_state
            raise e

    def _load_gguf(self, model_path, hardware_config):
        """Load GGUF model using llama-cpp-python (BitNet.cpp / llama.cpp)."""
        from llama_cpp import Llama  # type: ignore

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
        """Load model using ONNX Runtime GenAI (CUDA / DirectML / CPU)."""
        import onnxruntime_genai as og  # type: ignore

        logger.info("Using ONNX Runtime GenAI...")

        # Detect available execution providers via onnxruntime
        available_eps = []
        try:
            import onnxruntime as ort  # type: ignore
            available_eps = ort.get_available_providers()
            logger.info(f"Available ONNX Runtime EPs: {available_eps}")
        except ImportError:
            logger.warning("onnxruntime not available for EP detection")

        # Determine execution provider priority based on model variant
        model_lower = model_path.lower()
        is_gpu_model = "gpu" in model_lower and "cpu" not in os.path.basename(model_lower)

        ep = "cpu"  # Default fallback
        use_cuda = False
        use_dml = False

        if is_gpu_model or hardware_config == "cuda":
            if "CUDAExecutionProvider" in available_eps:
                ep = "cuda"
                use_cuda = True
                logger.info("CUDA EP detected — using NVIDIA GPU acceleration")
            elif "DmlExecutionProvider" in available_eps:
                ep = "directml"
                use_dml = True
                logger.info("DirectML EP detected — using AMD GPU acceleration")
            else:
                logger.warning("GPU model variant selected but no GPU EP available, falling back to CPU")
        elif hardware_config == "directml":
            if "DmlExecutionProvider" in available_eps:
                ep = "directml"
                use_dml = True
                logger.info("DirectML EP selected — using AMD GPU acceleration")
            else:
                logger.warning("DirectML requested but not available, falling back to CPU")
        else:
            logger.info("CPU model variant — using CPU execution")

        logger.info(f"Loading GenAI model from: {model_path} with provider: {ep}")

        # Use Config API to explicitly set the execution provider
        if use_cuda:
            _ensure_cuda_dlls_on_path()
            config = og.Config(model_path)
            config.clear_providers()
            config.append_provider('cuda')
            self.model = og.Model(config)
        elif use_dml:
            try:
                config = og.Config(model_path)
                config.clear_providers()
                config.append_provider('dml')
                self.model = og.Model(config)
            except Exception as dml_err:
                logger.warning(f"DirectML config failed ({dml_err}), trying default load")
                self.model = og.Model(model_path)
        else:
            self.model = og.Model(model_path)

        self.tokenizer = og.Tokenizer(self.model)
        self.provider = f"GenAI:{ep}"
        logger.info(f"Model device type: {getattr(self.model, 'device_type', 'unknown')}")

        self.gen_params = og.GeneratorParams(self.model)
        self.gen_params.set_search_options(do_sample=False)  # type: ignore


    def _load_onnx_standard(self, model_path, hardware_config):
        """Load model using standard ONNX Runtime (CUDA / DirectML / CPU)."""
        import onnxruntime as ort  # type: ignore
        from transformers import AutoTokenizer  # type: ignore

        providers = []
        if hardware_config == "cuda" or hardware_config == "auto":
            providers.append("CUDAExecutionProvider")
        if hardware_config == "directml" or hardware_config == "auto":
            providers.append("DmlExecutionProvider")
        providers.append("CPUExecutionProvider")

        self.session = ort.InferenceSession(model_path, providers=providers)
        self.provider = self.session.get_providers()[0]  # type: ignore

        try:
            self.tokenizer = AutoTokenizer.from_pretrained(os.path.dirname(model_path))
        except Exception:
            pass

    def search_web(self, query):
        """Search the web using DuckDuckGo for context augmentation."""
        try:
            from duckduckgo_search import DDGS  # type: ignore
            with DDGS() as ddgs:
                results = list(ddgs.text(query, max_results=3))
            return "\n".join([f"- {r['title']}: {r['body']}" for r in results])
        except Exception as e:
            logger.error(f"Search failed: {e}")
            return "Search unavailable."

    def search_pharmacy_db(self, query):
        """
        Query the MediManage database for pharmacy-related data.
        Supports both SQLite and PostgreSQL via db_connector.
        Returns formatted context string, or empty string if no relevant data.
        """
        q = query.lower()
        results: list[str] = list()
        ph = placeholder()  # '?' for SQLite, '%s' for PostgreSQL

        try:
            with get_readonly_connection() as conn:
                cur = conn.cursor()

                # --- Medicine / Inventory queries ---
                med_keywords = ['medicine', 'tablet', 'capsule', 'syrup', 'drug',
                                'stock', 'inventory', 'available', 'paracetamol',
                                'amoxicillin', 'how many', 'quantity', 'check']
                if any(kw in q for kw in med_keywords):
                    stop = {'how', 'many', 'check', 'show', 'list', 'get', 'find',
                            'me', 'the', 'a', 'an', 'of', 'in', 'is', 'are',
                            'do', 'we', 'have', 'what', 'tablets', 'tablet',
                            'capsules', 'medicine', 'medicines', 'drug', 'drugs',
                            'stock', 'inventory', 'available', 'our'}
                    words = re.findall(r'[a-zA-Z]+', q)
                    search_terms = [w for w in words if w not in stop and len(w) > 2]

                    if search_terms:
                        like_clauses = ' OR '.join(
                            [f"m.name LIKE {ph} OR m.generic_name LIKE {ph}"
                             for _ in search_terms])
                        params = []
                        for t in search_terms:
                            params.extend([f"%{t}%", f"%{t}%"])

                        cur.execute(f"""
                            SELECT m.name, m.generic_name, m.company,
                                   m.price, m.expiry_date,
                                   COALESCE(s.quantity, 0) as stock_qty
                            FROM medicines m
                            LEFT JOIN stock s ON m.medicine_id = s.medicine_id
                            WHERE {like_clauses}
                            LIMIT 20
                        """, params)
                    else:
                        cur.execute("""
                            SELECT m.name, m.generic_name, m.company,
                                   m.price, m.expiry_date,
                                   COALESCE(s.quantity, 0) as stock_qty
                            FROM medicines m
                            LEFT JOIN stock s ON m.medicine_id = s.medicine_id
                            LIMIT 20
                        """)

                    rows = fetchall_as_dicts(cur)
                    if rows:
                        results.append(f"[Inventory Data - {len(rows)} medicines found]:")
                        for r in rows:
                            results.append(
                                f"  - {r['name']} ({r.get('generic_name') or 'N/A'}) | "
                                f"Company: {r.get('company') or 'N/A'} | "
                                f"Price: {r.get('price')} | "
                                f"Stock: {r.get('stock_qty')} | "
                                f"Expiry: {r.get('expiry_date') or 'N/A'}")

                # --- Low stock queries ---
                if any(kw in q for kw in ['low stock', 'reorder', 'running out',
                                           'shortage', 'out of stock']):
                    cur.execute("""
                        SELECT m.name, m.generic_name,
                               COALESCE(s.quantity, 0) as stock_qty
                        FROM medicines m
                        LEFT JOIN stock s ON m.medicine_id = s.medicine_id
                        WHERE COALESCE(s.quantity, 0) < 10
                        ORDER BY stock_qty ASC
                        LIMIT 20
                    """)
                    rows = fetchall_as_dicts(cur)
                    if rows:
                        results.append(f"[Low Stock Alert - {len(rows)} medicines below 10 units]:")
                        for r in rows:
                            results.append(
                                f"  - {r['name']} ({r.get('generic_name') or 'N/A'}) | "
                                f"Stock: {r.get('stock_qty')}")

                # --- Expiry queries ---
                if any(kw in q for kw in ['expir', 'expired', 'expiring',
                                           'shelf life', 'validity']):
                    cur.execute("""
                        SELECT m.name, m.expiry_date,
                               COALESCE(s.quantity, 0) as stock_qty
                        FROM medicines m
                        LEFT JOIN stock s ON m.medicine_id = s.medicine_id
                        WHERE m.expiry_date IS NOT NULL
                          AND m.expiry_date <= CURRENT_DATE + INTERVAL '90 days'
                        ORDER BY m.expiry_date ASC
                        LIMIT 20
                    """) if ph == '%s' else cur.execute("""
                        SELECT m.name, m.expiry_date,
                               COALESCE(s.quantity, 0) as stock_qty
                        FROM medicines m
                        LEFT JOIN stock s ON m.medicine_id = s.medicine_id
                        WHERE m.expiry_date IS NOT NULL
                          AND m.expiry_date <= date('now', '+90 days')
                        ORDER BY m.expiry_date ASC
                        LIMIT 20
                    """)
                    rows = fetchall_as_dicts(cur)
                    if rows:
                        results.append(f"[Expiring Soon - {len(rows)} medicines within 90 days]:")
                        for r in rows:
                            results.append(
                                f"  - {r['name']} | Expiry: {r.get('expiry_date')} | "
                                f"Stock: {r.get('stock_qty')}")

                # --- Customer queries ---
                if any(kw in q for kw in ['customer', 'balance', 'debt',
                                           'debtor', 'owe', 'credit']):
                    cur.execute("""
                        SELECT name, phone, current_balance
                        FROM customers
                        WHERE current_balance > 0
                        ORDER BY current_balance DESC
                        LIMIT 15
                    """)
                    rows = fetchall_as_dicts(cur)
                    if rows:
                        results.append(f"[Customer Balances - {len(rows)} with outstanding debt]:")
                        for r in rows:
                            results.append(
                                f"  - {r['name']} | Phone: {r.get('phone') or 'N/A'} | "
                                f"Balance: {r.get('current_balance')}")

                # --- Sales / Bills queries ---
                if any(kw in q for kw in ['sale', 'sales', 'revenue', 'bill',
                                           'today', 'income', 'profit', 'earning']):
                    today_sql = "CURRENT_DATE" if ph == '%s' else "date('now')"
                    month_sql = "CURRENT_DATE - INTERVAL '30 days'" if ph == '%s' else "date('now', '-30 days')"

                    cur.execute(f"""
                        SELECT COUNT(*) as bill_count,
                               COALESCE(SUM(total_amount), 0) as total_revenue
                        FROM bills
                        WHERE date(bill_date) = {today_sql}
                    """)
                    rows = fetchall_as_dicts(cur)
                    if rows and rows[0]:
                        r = rows[0]
                        results.append(
                            f"[Today's Sales]: {r.get('bill_count')} bills, "
                            f"Total Revenue: {r.get('total_revenue')}")

                    cur.execute(f"""
                        SELECT COUNT(*) as bill_count,
                               COALESCE(SUM(total_amount), 0) as total_revenue
                        FROM bills
                        WHERE bill_date >= {month_sql}
                    """)
                    rows = fetchall_as_dicts(cur)
                    if rows and rows[0]:
                        r = rows[0]
                        results.append(
                            f"[Last 30 Days]: {r.get('bill_count')} bills, "
                            f"Total Revenue: {r.get('total_revenue')}")

        except Exception as e:
            logger.error(f"Pharmacy DB query failed: {e}")
            return ""

        if results:
            logger.info(f"Pharmacy DB returned {len(results)} lines of context")
            return "\n".join(results)
        return ""

    def generate(self, prompt, max_tokens=2000, use_search=False):
        """Runs inference using the loaded model. Tracks performance metrics."""
        self.cancel_flag = False
        context = ""
        system_prompt = ""

        if use_search:
            # First, try the pharmacy database
            logger.info(f"Querying pharmacy database for: {prompt}")
            db_context = self.search_pharmacy_db(prompt)

            if db_context:
                context = f"\n\n[MediManage Database Results]:\n{db_context}\n"
                system_prompt = (
                    "You are the MediManage Pharmacy AI Assistant. "
                    "You have access to the pharmacy's real inventory database. "
                    "Use the database results provided below to answer the user's question accurately. "
                    "Present the data clearly in a structured format. "
                    "If the data shows stock quantities, prices, or expiry dates, highlight them. "
                    "Be concise and helpful.\n\n"
                )
            else:
                # Fall back to web search for non-pharmacy queries
                logger.info(f"No pharmacy data found, performing web search for: {prompt}")
                search_results = self.search_web(prompt)
                context = f"\n\n[Web Search Results]:\n{search_results}\n"
                system_prompt = (
                    "You are a helpful AI assistant. "
                    "Use the search results below to answer the user's question. "
                    "Be concise and accurate.\n\n"
                )

        full_prompt = f"{system_prompt}User: {prompt}\n{context}\nAssistant:"

        if not self.provider:
            return "Error: Model not loaded. Please load a model from Settings or Model Store."

        try:
            gen_start = time.time()
            tokens_generated = 0
            result_text = ""

            # --- GGUF / BitNet / llama.cpp Inference ---
            if self.framework == "gguf":
                response_stream = self.model.create_completion(
                    full_prompt,
                    max_tokens=max_tokens,
                    temperature=0.7,
                    top_p=0.9,
                    stop=["User:"],
                    stream=True
                )
                for chunk in response_stream:
                    if getattr(self, "cancel_flag", False):
                        logger.info("GGUF Inference cancelled mid-thought.")
                        break
                    text_chunk = chunk["choices"][0].get("text", "")
                    result_text += text_chunk
                    tokens_generated += 1
                
                result_text = result_text.strip()

            # --- GenAI Inference (CUDA / CPU) ---
            elif self.framework == "genai":
                import onnxruntime_genai as og  # type: ignore

                input_ids = self.tokenizer.encode(full_prompt)
                input_len = len(input_ids) if hasattr(input_ids, '__len__') else 0

                # Re-create params each call to avoid stale state
                params = og.GeneratorParams(self.model)
                params.set_search_options(do_sample=False, max_length=max_tokens)

                # Streaming Generator loop (works on both OGA 0.7.x and 0.10.x)
                generator = og.Generator(self.model, params)
                generator.append_tokens(input_ids)
                while not generator.is_done():
                    if getattr(self, "cancel_flag", False):
                        logger.info("Inference cancelled mid-thought.")
                        break
                    generator.generate_next_token()
                    tokens_generated += 1
                output_ids = generator.get_sequence(0)

                decoded_output = self.tokenizer.decode(output_ids)

                if decoded_output.startswith(full_prompt):
                    result_text = decoded_output[len(full_prompt):].strip()
                else:
                    result_text = decoded_output

            # --- Standard ONNX Inference ---
            else:
                return (f"Backend '{self.framework}' loaded on {self.provider}, "
                        f"but raw inference loop not implemented. "
                        f"Please use ONNX Runtime GenAI or GGUF format for full LLM support.")

            # --- Track performance ---
            gen_elapsed = time.time() - gen_start
            tok_per_sec = tokens_generated / gen_elapsed if gen_elapsed > 0 else 0
            self._last_tokens_generated = int(tokens_generated)  # type: ignore
            self._last_generation_time = float(gen_elapsed)
            self._last_tokens_per_sec = float(tok_per_sec)  # type: ignore
            self._total_tokens_generated = self._total_tokens_generated + int(tokens_generated)  # type: ignore
            self._total_generation_time += float(gen_elapsed)
            logger.info(f"Generated {tokens_generated} tokens in {gen_elapsed:.2f}s "
                        f"({tok_per_sec:.1f} tok/s) on {self.provider}")

            # Clean up hallucinated conversation turns or EOS tokens that slipped through
            result_text_str = str(result_text)
            for stop_word in ["\nUser:", "User:", "<|im_end|>", "<|eot_id|>", "<|endoftext|>", "<|end|>"]:
                if stop_word in result_text_str:
                    result_text_str = result_text_str.split(stop_word)[0].strip()
            
            return result_text_str

        except Exception as e:
            logger.error(f"Inference failed: {e}")
            return f"Error during generation: {str(e)}"

    def get_performance_stats(self):
        """Return performance metrics from inference runs."""
        avg_tok_per_sec = (
            self._total_tokens_generated / self._total_generation_time
            if self._total_generation_time > 0 else 0
        )
        return {
            "provider": self.provider or "none",
            "framework": self.framework or "none",
            "device_type": getattr(self.model, 'device_type', 'unknown') if self.model else 'none',
            "last_tokens": self._last_tokens_generated,
            "last_time_s": round(self._last_generation_time, 2),
            "last_tok_per_sec": round(self._last_tokens_per_sec, 1),
            "total_tokens": self._total_tokens_generated,
            "total_time_s": round(self._total_generation_time, 2),
            "avg_tok_per_sec": round(avg_tok_per_sec, 1),
        }


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
        total_size: float = 0.0
        all_files = []
        for root, dirs, files in os.walk(model_path):
            for f in files:
                fp = os.path.join(root, f)
                total_size = float(total_size) + float(os.path.getsize(fp))  # type: ignore
                all_files.append(f)

        info["size_mb"] = round(total_size / 1024 / 1024, 2)  # type: ignore

        # Detect format by file contents
        if "genai_config.json" in all_files:
            info["format"] = "ONNX GenAI"
        elif any(f.endswith(".xml") for f in all_files):
            info["format"] = "ONNX"
        elif any(f.endswith(".onnx") for f in all_files):
            info["format"] = "ONNX"
        elif any(f.endswith(".gguf") for f in all_files):
            info["format"] = "GGUF"
        else:
            info["format"] = "HuggingFace"

    elif os.path.isfile(model_path):
        info["size_mb"] = round(float(os.path.getsize(model_path)) / 1024 / 1024, 2)  # type: ignore
        ext = os.path.splitext(model_path)[1].lower()
        format_map = {".xml": "ONNX", ".onnx": "ONNX", ".gguf": "GGUF"}
        info["format"] = format_map.get(ext, "unknown")

    return info


# Singleton engine instance
engine = AIInferenceEngine()
