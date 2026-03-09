import sys
from llama_cpp import Llama

model_path = r"C:\Users\ksvik\MediManage\models\bitnet-b1.58-2B-4T-gguf\ggml-model-i2_s.gguf"
print(f"Loading {model_path}...")
try:
    llm = Llama(
        model_path=model_path,
        n_ctx=4096,
        n_threads=4,
        verbose=True
    )
    print("Success")
except Exception as e:
    print(f"Error: {e}")
