import requests
import json
import time
import logging
import os

logger = logging.getLogger(__name__)


class CloudProviderError(RuntimeError):
    def __init__(self, provider: str, message: str, *, status_code: int | None = None, retryable: bool = False):
        super().__init__(message)
        self.provider = provider
        self.status_code = status_code
        self.retryable = retryable


class CloudAPIClient:
    """
    Cloud-only provider client for the MediManage Python AI engine.
    Handles provider HTTP calls, retries, and per-request API keys.
    """

    def __init__(self):
        self.timeout = int(os.getenv("MEDIMANAGE_CLOUD_TIMEOUT_SECONDS", "25"))
        self.default_models = {
            "GEMINI": "gemini-2.5-flash",
            "GROQ": "llama-3.3-70b-versatile",
            "OPENROUTER": "anthropic/claude-3.5-sonnet",
            "OPENAI": "gpt-4o",
            "CLAUDE": "claude-3-5-sonnet-20241022",
        }

    def chat(self, provider: str, model: str, api_key: str, prompt: str, requires_json: bool = False, retries: int = 2) -> str:
        """Execute a prompt with a configured Cloud LLM, handling rate limits automatically."""
        provider = (provider or "").strip().upper()
        model = self._resolve_model(provider, model)
        api_key = (api_key or "").strip()

        if not api_key or api_key == "YOUR_API_KEY":
            raise ValueError(f"{provider} API key not configured. Set it in UI Settings.")

        # Modify prompt if JSON is required
        if requires_json:
            prompt += "\n\nRespond with ONLY valid JSON. Do NOT include markdown fences or explanation — raw JSON only."

        for attempt in range(retries + 1):
            try:
                if provider == "GEMINI":
                    response_text = self._send_gemini(model, api_key, prompt)
                elif provider == "CLAUDE":
                    response_text = self._send_claude(model, api_key, prompt)
                elif provider in ["GROQ", "OPENROUTER", "OPENAI"]:
                    response_text = self._send_openai_compatible(provider, model, api_key, prompt)
                else:
                    raise ValueError(f"Unsupported cloud provider: {provider}")

                if requires_json:
                    return self._parse_json_response(response_text)
                return response_text

            except CloudProviderError as e:
                # Basic rate limit check
                if e.status_code == 429 and attempt < retries:
                    logger.warning(f"Rate limited by {provider}. Retrying in 15 seconds... ({retries - attempt} left)")
                    time.sleep(15)
                else:
                    raise
            except Exception:
                raise

    def _resolve_model(self, provider: str, model: str) -> str:
        configured_model = (model or "").strip()
        if configured_model:
            return configured_model
        return self.default_models.get(provider, "")

    def _send_gemini(self, model: str, api_key: str, prompt: str) -> str:
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}"
        payload = {
            "contents": [{"parts": [{"text": prompt}]}]
        }
        res = self._post_json(
            "GEMINI",
            url,
            payload,
            {"Content-Type": "application/json"},
        )
        if res.status_code == 200:
            data = res.json()
            return data["candidates"][0]["content"]["parts"][0]["text"]
        self._raise_for_status(provider="GEMINI", res=res)
        raise RuntimeError("GEMINI: no response")

    def _send_openai_compatible(self, provider: str, model: str, api_key: str, prompt: str) -> str:
        if provider == "GROQ":
            base_url = "https://api.groq.com/openai/v1/chat/completions"
        elif provider == "OPENROUTER":
            base_url = "https://openrouter.ai/api/v1/chat/completions"
        elif provider == "OPENAI":
            base_url = "https://api.openai.com/v1/chat/completions"
        else:
            raise ValueError(f"Unknown OpenAI compatible provider: {provider}")

        payload = {
            "model": model,
            "messages": [{"role": "user", "content": prompt}],
            "max_tokens": 4096
        }
        
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}"
        }

        if provider == "OPENROUTER":
            headers["HTTP-Referer"] = "https://medimanage.app"
            headers["X-Title"] = "MediManage"

        res = self._post_json(provider, base_url, payload, headers)
        if res.status_code == 200:
            data = res.json()
            return data["choices"][0]["message"]["content"]
        self._raise_for_status(provider=provider, res=res)
        raise RuntimeError(f"{provider}: no response")

    def _send_claude(self, model: str, api_key: str, prompt: str) -> str:
        url = "https://api.anthropic.com/v1/messages"
        payload = {
            "model": model,
            "max_tokens": 4096,
            "messages": [{"role": "user", "content": prompt}]
        }
        headers = {
            "Content-Type": "application/json",
            "x-api-key": api_key,
            "anthropic-version": "2023-06-01"
        }
        res = self._post_json("CLAUDE", url, payload, headers)
        if res.status_code == 200:
            data = res.json()
            return data["content"][0]["text"]
        self._raise_for_status(provider="CLAUDE", res=res)
        raise RuntimeError("CLAUDE: no response")

    def _post_json(self, provider: str, url: str, payload: dict, headers: dict) -> requests.Response:
        try:
            return requests.post(url, json=payload, headers=headers, timeout=self.timeout)
        except requests.exceptions.Timeout:
            raise CloudProviderError(
                provider,
                f"{provider} request timed out after {self.timeout}s.",
                retryable=True,
            ) from None
        except requests.exceptions.ConnectionError:
            raise CloudProviderError(
                provider,
                f"{provider} connection failed. Check internet access or provider availability.",
                retryable=True,
            ) from None
        except requests.exceptions.RequestException as error:
            raise CloudProviderError(
                provider,
                f"{provider} request failed: {error}",
                retryable=True,
            ) from None

    def _raise_for_status(self, provider: str, res: requests.Response):
        """Format an informative error explicitly."""
        code = res.status_code
        if code == 429:
            raise CloudProviderError(f"{provider}", f"429: Rate limited by {provider}", status_code=429, retryable=True)
        try:
            err_data = res.json()
            if "error" in err_data:
                err = err_data["error"]
                if isinstance(err, dict) and "message" in err:
                    raise CloudProviderError(provider, f"{provider} Error ({code}): {err['message']}", status_code=code)
                raise CloudProviderError(provider, f"{provider} Error ({code}): {err}", status_code=code)
        except json.JSONDecodeError:
            pass
        raise CloudProviderError(provider, f"{provider} Error ({code}): {res.text}", status_code=code)

    def _parse_json_response(self, raw_text: str) -> str:
        """Strip markdown fences to expose raw JSON."""
        if not raw_text:
            return "{}"
        cleaned = raw_text.strip()
        if cleaned.startswith("```"):
            newline_idx = cleaned.find("\n")
            if newline_idx > 0:
                cleaned = cleaned[newline_idx + 1:len(cleaned)]
            if cleaned.endswith("```"):
                cleaned = cleaned[0:len(cleaned) - 3]
        return cleaned.strip()

cloud_api_client = CloudAPIClient()
