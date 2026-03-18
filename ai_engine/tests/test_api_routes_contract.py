import sys
import threading
import types
import unittest
from importlib import import_module
from pathlib import Path

from flask import Flask


class _FakeEngine:
    def __init__(self):
        self.provider = "stub-local"
        self.generate_calls = []
        self.search_calls = []

    def generate(self, prompt, use_search=False):
        self.generate_calls.append((prompt, use_search))
        return f"GENERATED::{prompt}"

    def search_pharmacy_db(self, prompt):
        self.search_calls.append(prompt)
        return f"DB::{prompt}"

    def load_model(self, *_args, **_kwargs):
        self.provider = "stub-local"


class _FakeCloudClient:
    def __init__(self):
        self.calls = []

    def chat(self, provider, model, api_key, prompt, requires_json=False, retries=2):
        self.calls.append({
            "provider": provider,
            "model": model,
            "api_key": api_key,
            "prompt": prompt,
            "requires_json": requires_json,
            "retries": retries,
        })
        return f"CLOUD::{provider}::{prompt}"


def _install_route_stubs(fake_engine, fake_cloud_client):
    inference_mod = types.ModuleType("app.services.inference")
    inference_mod.engine = fake_engine
    inference_mod.list_local_models = lambda _models_dir: []
    sys.modules["app.services.inference"] = inference_mod

    cloud_mod = types.ModuleType("app.services.cloud")
    cloud_mod.cloud_api_client = fake_cloud_client
    sys.modules["app.services.cloud"] = cloud_mod

    download_mod = types.ModuleType("app.services.download")
    download_mod.set_cancel_event = lambda _event: None
    download_mod.download_hf_model = lambda *args, **kwargs: "dummy-model"
    download_mod.download_ollama_model = lambda *args, **kwargs: "dummy-model"
    download_mod.download_direct_url = lambda *args, **kwargs: "dummy-model"
    sys.modules["app.services.download"] = download_mod

    hardware_mod = types.ModuleType("app.core.hardware")
    hardware_mod.detect_hardware = lambda: {"backend": "cpu", "device_name": "Stub CPU"}
    hardware_mod.get_hardware_info = lambda: {"backend": "cpu", "device_name": "Stub CPU"}
    sys.modules["app.core.hardware"] = hardware_mod

    middleware_mod = types.ModuleType("app.api.middleware")
    middleware_mod.set_progress = lambda _data: None
    middleware_mod.get_progress = lambda: {"status": "idle", "percent": 0}
    middleware_mod._download_cancel = threading.Event()
    sys.modules["app.api.middleware"] = middleware_mod


class ApiRoutesContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        project_root = Path(__file__).resolve().parents[2]
        ai_engine_path = project_root / "ai_engine"
        sys.path.insert(0, str(ai_engine_path))

        cls.fake_engine = _FakeEngine()
        cls.fake_cloud = _FakeCloudClient()
        _install_route_stubs(cls.fake_engine, cls.fake_cloud)
        sys.modules.pop("app.api.routes", None)
        cls.routes = import_module("app.api.routes")
        cls.routes.engine = cls.fake_engine
        cls.routes.cloud_api_client = cls.fake_cloud

        app = Flask(__name__)
        app.register_blueprint(cls.routes.api_bp)
        cls.client = app.test_client()

    def setUp(self):
        self.fake_engine.generate_calls.clear()
        self.fake_engine.search_calls.clear()
        self.fake_cloud.calls.clear()
        self.fake_engine.provider = "stub-local"

    def test_local_only_raw_chat_uses_local_generation_not_database_lookup(self):
        response = self.client.post(
            "/orchestrate",
            json={
                "action": "raw_chat",
                "data": {"prompt": "Explain current demand"},
                "routing": "local_only",
            },
        )

        self.assertEqual(response.status_code, 200)
        body = response.get_json()
        self.assertEqual("local", body["source"])
        self.assertEqual(1, len(self.fake_engine.generate_calls))
        self.assertEqual([], self.fake_engine.search_calls)
        self.assertIn("Explain current demand", self.fake_engine.generate_calls[0][0])

    def test_combined_analysis_cloud_only_uses_business_fallback_when_no_local_model(self):
        self.fake_engine.provider = None

        response = self.client.post(
            "/orchestrate",
            json={
                "action": "combined_analysis",
                "data": {
                    "prompt": "What should we do next?",
                    "business_context": "Expiring items: Amoxicillin",
                },
                "cloud_config": {
                    "provider": "GEMINI",
                    "model": "",
                    "api_key": "test-key",
                },
                "routing": "cloud_only",
            },
        )

        self.assertEqual(response.status_code, 200)
        body = response.get_json()
        self.assertEqual("cloud_combined", body["source"])
        self.assertEqual(1, len(self.fake_cloud.calls))
        self.assertIn("Business Data Summary:\nExpiring items: Amoxicillin", self.fake_cloud.calls[0]["prompt"])
        self.assertIn("What should we do next?", self.fake_cloud.calls[0]["prompt"])

    def test_chat_rag_combines_context_and_prompt(self):
        response = self.client.post(
            "/chat/rag",
            json={
                "prompt": "Which items need restocking?",
                "context": "Low stock: Paracetamol, Cetirizine",
            },
        )

        self.assertEqual(response.status_code, 200)
        body = response.get_json()
        self.assertEqual("local_rag", body["source"])
        self.assertEqual(1, len(self.fake_engine.generate_calls))
        prompt, use_search = self.fake_engine.generate_calls[0]
        self.assertFalse(use_search)
        self.assertIn("### Business Data", prompt)
        self.assertIn("Low stock: Paracetamol, Cetirizine", prompt)
        self.assertIn("### Query", prompt)
        self.assertIn("Which items need restocking?", prompt)


if __name__ == "__main__":
    unittest.main()
