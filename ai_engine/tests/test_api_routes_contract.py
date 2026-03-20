import os
import sys
import unittest
from pathlib import Path
from unittest.mock import patch


os.environ["MEDIMANAGE_LOCAL_API_TOKEN"] = "test-token"

project_root = Path(__file__).resolve().parents[2]
ai_engine_path = project_root / "ai_engine"
sys.path.insert(0, str(ai_engine_path))

from app.main import create_app
from app.services.cloud import CloudProviderError


class ApiRoutesContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.app = create_app()
        cls.client = cls.app.test_client()
        cls.headers = {"X-MediManage-Admin-Token": "test-token"}

    def test_care_protocol_timeout_returns_local_fallback_response(self):
        payload = {
            "action": "detailed_protocol",
            "data": {"medicines": ["Paracetamol"]},
            "cloud_config": {"provider": "GROQ", "model": "", "api_key": "demo-key"},
        }

        with patch(
            "app.api.routes.cloud_api_client.chat",
            side_effect=CloudProviderError("GROQ", "GROQ request timed out after 25s.", retryable=True),
        ):
            response = self.client.post("/orchestrate", json=payload, headers=self.headers)

        self.assertEqual(200, response.status_code)
        body = response.get_json()
        self.assertEqual("", body["response"])
        self.assertEqual("local_fallback", body["source"])

    def test_non_care_protocol_timeout_returns_service_unavailable(self):
        payload = {
            "action": "sales_summary",
            "data": {"sales_data": "{}", "total_revenue": 0, "top_items": "[]"},
            "cloud_config": {"provider": "GROQ", "model": "", "api_key": "demo-key"},
        }

        with patch(
            "app.api.routes.cloud_api_client.chat",
            side_effect=CloudProviderError("GROQ", "GROQ request timed out after 25s.", retryable=True),
        ):
            response = self.client.post("/orchestrate", json=payload, headers=self.headers)

        self.assertEqual(503, response.status_code)
        body = response.get_json()
        self.assertIn("timed out", body["error"].lower())


if __name__ == "__main__":
    unittest.main()
