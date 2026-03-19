package org.example.MediManage.service.ai;

import org.example.MediManage.security.CloudApiKeyStore;
import org.example.MediManage.util.AppExecutors;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

class CloudAIService {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);
    private static final int MAX_RETRIES = 2;

    private final HttpClient client;

    CloudAIService() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    CompletableFuture<String> chat(JSONObject cloudConfig, String prompt) {
        return CompletableFuture.supplyAsync(() -> execute(cloudConfig, prompt), AppExecutors.background());
    }

    private String execute(JSONObject cloudConfig, String prompt) {
        JSONObject safeConfig = cloudConfig == null ? new JSONObject() : cloudConfig;
        CloudApiKeyStore.Provider provider = AIOrchestrator.resolveCloudProvider(safeConfig.optString("provider", "GEMINI"));
        String model = AIOrchestrator.resolveConfiguredCloudModel(provider, safeConfig.optString("model", ""));
        String apiKey = safeConfig.optString("api_key", "").trim();

        if (apiKey.isBlank() || "YOUR_API_KEY".equals(apiKey)) {
            throw new IllegalArgumentException(provider.name() + " API key not configured. Set it in Settings.");
        }

        RuntimeException lastError = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return switch (provider) {
                    case GEMINI -> sendGemini(model, apiKey, prompt);
                    case CLAUDE -> sendClaude(model, apiKey, prompt);
                    case GROQ, OPENROUTER, OPENAI -> sendOpenAiCompatible(provider, model, apiKey, prompt);
                };
            } catch (RuntimeException ex) {
                lastError = ex;
                if (!isRateLimit(ex) || attempt == MAX_RETRIES) {
                    throw ex;
                }
                try {
                    Thread.sleep(15_000L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Cloud AI retry interrupted", interruptedException);
                }
            }
        }
        throw lastError == null ? new RuntimeException("Unknown cloud AI error") : lastError;
    }

    private boolean isRateLimit(RuntimeException ex) {
        String message = ex.getMessage();
        return message != null && message.contains("429");
    }

    private String sendGemini(String model, String apiKey, String prompt) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model
                + ":generateContent?key=" + apiKey;
        JSONObject payload = new JSONObject()
                .put("contents", new JSONArray().put(new JSONObject()
                        .put("parts", new JSONArray().put(new JSONObject().put("text", prompt)))));

        HttpResponse<String> response = sendJson(url, payload, Map.of("Content-Type", "application/json"));
        if (response.statusCode() == 200) {
            JSONObject json = new JSONObject(response.body());
            return json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text");
        }
        throw buildProviderException("GEMINI", response);
    }

    private String sendOpenAiCompatible(CloudApiKeyStore.Provider provider, String model, String apiKey, String prompt) {
        String url = switch (provider) {
            case GROQ -> "https://api.groq.com/openai/v1/chat/completions";
            case OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions";
            case OPENAI -> "https://api.openai.com/v1/chat/completions";
            default -> throw new IllegalArgumentException("Unsupported OpenAI-compatible provider: " + provider);
        };

        JSONObject payload = new JSONObject()
                .put("model", model)
                .put("messages", new JSONArray().put(new JSONObject()
                        .put("role", "user")
                        .put("content", prompt)))
                .put("max_tokens", 4096);

        Map<String, String> headers = new java.util.LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + apiKey);
        if (provider == CloudApiKeyStore.Provider.OPENROUTER) {
            headers.put("HTTP-Referer", "https://medimanage.app");
            headers.put("X-Title", "MediManage");
        }

        HttpResponse<String> response = sendJson(url, payload, headers);
        if (response.statusCode() == 200) {
            JSONObject json = new JSONObject(response.body());
            return json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
        }
        throw buildProviderException(provider.name(), response);
    }

    private String sendClaude(String model, String apiKey, String prompt) {
        JSONObject payload = new JSONObject()
                .put("model", model)
                .put("max_tokens", 4096)
                .put("messages", new JSONArray().put(new JSONObject()
                        .put("role", "user")
                        .put("content", prompt)));

        HttpResponse<String> response = sendJson(
                "https://api.anthropic.com/v1/messages",
                payload,
                Map.of(
                        "Content-Type", "application/json",
                        "x-api-key", apiKey,
                        "anthropic-version", "2023-06-01"));

        if (response.statusCode() == 200) {
            JSONObject json = new JSONObject(response.body());
            return json.getJSONArray("content")
                    .getJSONObject(0)
                    .getString("text");
        }
        throw buildProviderException("CLAUDE", response);
    }

    private HttpResponse<String> sendJson(String url, JSONObject payload, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()));

        headers.forEach(builder::header);

        try {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new RuntimeException("Cloud request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Cloud request interrupted", e);
        }
    }

    private RuntimeException buildProviderException(String provider, HttpResponse<String> response) {
        int status = response.statusCode();
        String body = response.body();

        if (status == 429) {
            return new RuntimeException("429: Rate limited by " + provider);
        }

        try {
            JSONObject json = new JSONObject(body);
            Object error = json.opt("error");
            if (error instanceof JSONObject errorObject) {
                String message = errorObject.optString("message", errorObject.toString());
                return new RuntimeException(provider + " Error (" + status + "): " + message);
            }
            if (error != null) {
                return new RuntimeException(provider + " Error (" + status + "): " + error);
            }
        } catch (Exception ignored) {
            // Fall through to raw body.
        }

        return new RuntimeException(provider + " Error (" + status + "): " + body);
    }
}
