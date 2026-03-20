import sys
import unittest
from pathlib import Path
from unittest.mock import patch

import requests


project_root = Path(__file__).resolve().parents[2]
ai_engine_path = project_root / "ai_engine"
sys.path.insert(0, str(ai_engine_path))

from app.services.cloud import CloudAPIClient, CloudProviderError


class CloudAPIClientTests(unittest.TestCase):
    def test_blank_model_uses_provider_default(self):
        client = CloudAPIClient()
        self.assertEqual("gemini-2.5-flash", client._resolve_model("GEMINI", ""))
        self.assertEqual("gpt-4o", client._resolve_model("OPENAI", "   "))

    def test_explicit_model_is_preserved(self):
        client = CloudAPIClient()
        self.assertEqual("custom-model", client._resolve_model("OPENAI", "custom-model"))

    def test_timeout_is_translated_to_concise_provider_error(self):
        client = CloudAPIClient()

        with patch("app.services.cloud.requests.post", side_effect=requests.exceptions.Timeout()):
            with self.assertRaises(CloudProviderError) as ctx:
                client._post_json("GROQ", "https://example.test", {}, {})

        self.assertIn("timed out", str(ctx.exception).lower())
        self.assertTrue(ctx.exception.retryable)


if __name__ == "__main__":
    unittest.main()
