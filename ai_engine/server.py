import logging
import threading
from flask import Flask, request, jsonify
from inference import engine

app = Flask(__name__)

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

@app.route('/health', methods=['GET'])
def health():
    return jsonify({"status": "running", "provider": engine.provider})

@app.route('/load_model', methods=['POST'])
def load_model():
    data = request.json
    model_path = data.get("model_path")
    hardware_config = data.get("hardware_config", "auto")
    
    try:
        engine.load_model(model_path, hardware_config)
        return jsonify({"status": "success", "provider": engine.provider})
    except Exception as e:
        logger.error(f"Error loading model: {e}")
        return jsonify({"status": "error", "message": str(e)}), 500

@app.route('/chat', methods=['POST'])
def chat():
    data = request.json
    prompt = data.get("prompt")
    use_search = data.get("use_search", False)
    
    if not prompt:
        return jsonify({"error": "No prompt provided"}), 400
        
    try:
        response_text = engine.generate(prompt, use_search=use_search)
        return jsonify({"response": response_text})
    except Exception as e:
        logger.error(f"Inference error: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/download_model', methods=['POST'])
def download_model():
    data = request.json
    repo_id = data.get("repo_id")
    filename = data.get("filename", "") 
    local_dir = data.get("local_dir", "models")
    
    if not repo_id:
        return jsonify({"error": "No repo_id provided"}), 400

    def download_task():
        global download_progress
        download_progress = {"status": "downloading", "percent": 0, "message": "Starting...", "speed": ""}

        # Custom Tqdm to capture progress
        import tqdm
        # Patch both common locations
        
        class ProgressTqdm(tqdm.tqdm):
            def update(self, n=1):
                super().update(n)
                if self.total and self.total > 0:
                    pct = (self.n / self.total) * 100
                    # Message format: "Downloading... 45% (100 MB / 200 MB)"
                    download_progress["percent"] = round(pct, 1)
                    download_progress["message"] = f"Downloading... {round(pct, 1)}% ({self.n / 1024 / 1024:.1f} MB / {self.total / 1024 / 1024:.1f} MB)"
                else:
                    download_progress["message"] = f"Downloading... {self.n / 1024 / 1024:.1f} MB"
        
        # Monkey patch broadly
        tqdm.tqdm = ProgressTqdm
        try:
            import tqdm.auto
            tqdm.auto.tqdm = ProgressTqdm
        except:
            pass
            
        try:
             import huggingface_hub.utils.tqdm as hf_tqdm
             hf_tqdm.tqdm = ProgressTqdm
        except:
             pass
        
        try:
            from huggingface_hub import snapshot_download, hf_hub_download
            import os
            
            # Create dir
            os.makedirs(local_dir, exist_ok=True)
            model_name = repo_id.split("/")[-1]
            
            # Simplified logic:
            if filename == "*":
                target_path = os.path.join(local_dir, model_name)
                download_progress["message"] = f"Starting full download for {model_name}..."
                # local_dir_use_symlinks=False ensuring it acts like a normal download manager (files in folder)
                snapshot_download(repo_id=repo_id, local_dir=target_path, local_dir_use_symlinks=False, resume_download=True)
                
            elif "*" in filename:
                target_path = os.path.join(local_dir, model_name)
                download_progress["message"] = f"Starting subset download for {model_name}..."
                snapshot_download(repo_id=repo_id, local_dir=target_path, allow_patterns=filename, local_dir_use_symlinks=False, resume_download=True)
            elif filename:
                 target_path = os.path.join(local_dir, model_name) 
                 download_progress["message"] = f"Downloading {filename}..."
                 hf_hub_download(repo_id=repo_id, filename=filename, local_dir=target_path, local_dir_use_symlinks=False, resume_download=True)
            else:
                 target_path = os.path.join(local_dir, model_name)
                 download_progress["message"] = f"Starting full download for {model_name}..."
                 snapshot_download(repo_id=repo_id, local_dir=target_path, local_dir_use_symlinks=False, resume_download=True)
                 
            download_progress = {"status": "completed", "percent": 100, "message": "Download Complete!", "path": target_path}
            logger.info(f"Download complete: {target_path}")
            
        except Exception as e:
            download_progress = {"status": "error", "percent": 0, "message": str(e)}
            logger.error(f"Download failed: {e}")
        finally:
            # Restore original tqdm just in case
            hf_tqdm.tqdm = original_tqdm

    thread = threading.Thread(target=download_task)
    thread.start()
    
    return jsonify({"status": "started"})

@app.route('/download_status', methods=['GET'])
def download_status():
    global download_progress
    return jsonify(download_progress)

@app.route('/shutdown', methods=['POST'])
def shutdown():
    shutdown_func = request.environ.get('werkzeug.server.shutdown')
    if shutdown_func is None:
        # Fallback for other environments if needed, but we are using standard Flask dev server
        import os, signal
        os.kill(os.getpid(), signal.SIGINT)
        return jsonify({"status": "shutting_down_fallback"})
    
    shutdown_func()
    return jsonify({"status": "shutting_down"})

# Global progress state
download_progress = {"status": "idle", "percent": 0, "message": ""}

if __name__ == '__main__':
    logger.info("Starting AI Engine Server on port 5000...")
    # debug=False is important for signal handling in some contexts, but standard run is fine
    app.run(host='127.0.0.1', port=5000)
