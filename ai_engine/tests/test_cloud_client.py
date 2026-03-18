import sys
import unittest
from pathlib import Path


project_root = Path(__file__).resolve().parents[2]
ai_engine_path = project_root / "ai_engine"
sys.path.insert(0, str(ai_engine_path))

from app.services.cloud import CloudAPIClient


class CloudAPIClientTests(unittest.TestCase):
    def test_blank_model_uses_provider_default(self):
        client = CloudAPIClient()
        self.assertEqual("gemini-2.5-flash", client._resolve_model("GEMINI", ""))
        self.assertEqual("gpt-4o", client._resolve_model("OPENAI", "   "))

    def test_explicit_model_is_preserved(self):
        client = CloudAPIClient()
        self.assertEqual("custom-model", client._resolve_model("OPENAI", "custom-model"))


if __name__ == "__main__":
    unittest.main()
