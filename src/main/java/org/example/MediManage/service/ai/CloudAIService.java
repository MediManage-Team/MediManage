package org.example.MediManage.service.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Multi-provider Cloud AI Service.
 * Supports: Gemini, Groq, OpenRouter, OpenAI, Claude.
 */
public class CloudAIService implements AIService {

    // --- Provider Enum ---
    public enum Provider {
        GEMINI, GROQ, OPENROUTER, OPENAI, CLAUDE
    }

    // --- Model Definition ---
    public record ModelInfo(String id, String displayName, boolean free) {
        @Override
        public String toString() {
            return displayName + (free ? " [FREE]" : " [PAID]");
        }
    }

    // --- Static model lists per provider ---
    private static final Map<Provider, List<ModelInfo>> MODELS = new LinkedHashMap<>();

    static {
        MODELS.put(Provider.GEMINI, List.of(
                new ModelInfo("gemini-2.0-flash-lite", "Gemini 2.0 Flash Lite", true),
                new ModelInfo("gemini-2.0-flash", "Gemini 2.0 Flash", true),
                new ModelInfo("gemini-2.5-flash", "Gemini 2.5 Flash", true),
                new ModelInfo("gemini-2.5-pro", "Gemini 2.5 Pro", true)));

        MODELS.put(Provider.GROQ, List.of(
                new ModelInfo("llama-3.3-70b-versatile", "Llama 3.3 70B", true),
                new ModelInfo("llama-3.1-8b-instant", "Llama 3.1 8B Instant", true),
                new ModelInfo("mixtral-8x7b-32768", "Mixtral 8x7B", true),
                new ModelInfo("gemma2-9b-it", "Gemma 2 9B", true)));

        MODELS.put(Provider.OPENROUTER, List.of(
                new ModelInfo("meta-llama/llama-3.3-70b-instruct:free", "Llama 3.3 70B", true),
                new ModelInfo("google/gemini-2.0-flash-exp:free", "Gemini 2.0 Flash", true),
                new ModelInfo("deepseek/deepseek-r1:free", "DeepSeek R1", true),
                new ModelInfo("google/gemini-2.5-pro-exp-03-25:free", "Gemini 2.5 Pro", true)));

        MODELS.put(Provider.OPENAI, List.of(
                new ModelInfo("gpt-4o", "GPT-4o", false),
                new ModelInfo("gpt-4o-mini", "GPT-4o Mini", false),
                new ModelInfo("gpt-4-turbo", "GPT-4 Turbo", false)));

        MODELS.put(Provider.CLAUDE, List.of(
                new ModelInfo("claude-sonnet-4-20250514", "Claude Sonnet 4", false),
                new ModelInfo("claude-3-5-haiku-20241022", "Claude 3.5 Haiku", false)));
    }

    // --- Instance State ---
    private Provider activeProvider;
    private String activeModel;
    private final Map<Provider, String> apiKeys = new EnumMap<>(Provider.class);
    private final HttpClient client;

    public CloudAIService(String geminiKey) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        // Load all keys from Preferences
        java.util.prefs.Preferences prefs = null;
        try {
            prefs = java.util.prefs.Preferences
                    .userNodeForPackage(org.example.MediManage.SettingsController.class);
        } catch (Exception ignored) {
        }

        if (prefs != null) {
            apiKeys.put(Provider.GEMINI, prefs.get("cloud_api_key", geminiKey != null ? geminiKey : ""));
            apiKeys.put(Provider.GROQ, prefs.get("groq_api_key", ""));
            apiKeys.put(Provider.OPENROUTER, prefs.get("openrouter_api_key", ""));
            apiKeys.put(Provider.OPENAI, prefs.get("openai_api_key", ""));
            apiKeys.put(Provider.CLAUDE, prefs.get("claude_api_key", ""));

            String providerStr = prefs.get("cloud_provider", "GEMINI");
            try {
                this.activeProvider = Provider.valueOf(providerStr);
            } catch (Exception e) {
                this.activeProvider = Provider.GEMINI;
            }
            this.activeModel = prefs.get("cloud_model", getModels(activeProvider).get(0).id());
        } else {
            if (geminiKey != null && !geminiKey.isEmpty()) {
                apiKeys.put(Provider.GEMINI, geminiKey);
            }
            this.activeProvider = Provider.GEMINI;
            this.activeModel = "gemini-2.0-flash-lite";
        }

        System.out.println("☁️ Cloud AI: " + activeProvider + " / " + activeModel);
    }

    // --- Public Configuration API ---

    public static List<ModelInfo> getModels(Provider provider) {
        return MODELS.getOrDefault(provider, List.of());
    }

    public static Provider[] getProviders() {
        return Provider.values();
    }

    public Provider getActiveProvider() {
        return activeProvider;
    }

    public String getActiveModel() {
        return activeModel;
    }

    public void configure(Provider provider, String model, String apiKey) {
        this.activeProvider = provider;
        this.activeModel = model;
        if (apiKey != null && !apiKey.isEmpty()) {
            this.apiKeys.put(provider, apiKey);
        }
        System.out.println("☁️ Cloud AI reconfigured: " + provider + " / " + model);
    }

    public void setApiKey(String apiKey) {
        // Legacy compatibility — sets Gemini key
        apiKeys.put(Provider.GEMINI, apiKey);
    }

    public void setApiKey(Provider provider, String apiKey) {
        apiKeys.put(provider, apiKey);
    }

    public String getApiKey(Provider provider) {
        return apiKeys.getOrDefault(provider, "");
    }

    // --- AIService Interface ---

    @Override
    public CompletableFuture<String> chat(String prompt) {
        String key = apiKeys.getOrDefault(activeProvider, "");
        if (key == null || key.isEmpty()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(activeProvider + " API key not configured. Set it in Settings."));
        }
        return sendWithRetry(prompt, 2);
    }

    @Override
    public boolean isAvailable() {
        String key = apiKeys.getOrDefault(activeProvider, "");
        return key != null && !key.isEmpty() && !key.equals("YOUR_API_KEY");
    }

    @Override
    public String getProviderName() {
        return switch (activeProvider) {
            case GEMINI -> "Google Gemini (Cloud)";
            case GROQ -> "Groq (Cloud)";
            case OPENROUTER -> "OpenRouter (Cloud)";
            case OPENAI -> "OpenAI (Cloud)";
            case CLAUDE -> "Anthropic Claude (Cloud)";
        };
    }

    // --- Internal: Retry Logic ---

    private CompletableFuture<String> sendWithRetry(String prompt, int retriesLeft) {
        return sendRequest(prompt)
                .exceptionally(ex -> {
                    if (retriesLeft > 0 && ex.getMessage() != null && ex.getMessage().contains("429")) {
                        System.out.println("⏳ Rate limited. Retrying in 15s... (" + retriesLeft + " left)");
                        try {
                            Thread.sleep(15000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                        return sendWithRetry(prompt, retriesLeft - 1).join();
                    }
                    throw new RuntimeException(ex);
                });
    }

    // --- Internal: Route to Provider ---

    private CompletableFuture<String> sendRequest(String prompt) {
        return switch (activeProvider) {
            case GEMINI -> sendGemini(prompt);
            case CLAUDE -> sendClaude(prompt);
            default -> sendOpenAICompatible(prompt); // Groq, OpenRouter, OpenAI
        };
    }

    // --- Gemini Format ---

    private CompletableFuture<String> sendGemini(String prompt) {
        String key = apiKeys.get(Provider.GEMINI);
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + activeModel + ":generateContent?key=" + key;

        JSONObject payload = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("text", prompt));
        content.put("parts", parts);
        contents.put(content);
        payload.put("contents", contents);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(90))
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        JSONObject json = new JSONObject(response.body());
                        return json.getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text");
                    }
                    throw new RuntimeException(handleError(response));
                });
    }

    // --- OpenAI-Compatible Format (Groq, OpenRouter, OpenAI) ---

    private CompletableFuture<String> sendOpenAICompatible(String prompt) {
        String key = apiKeys.get(activeProvider);
        String baseUrl = switch (activeProvider) {
            case GROQ -> "https://api.groq.com/openai/v1/chat/completions";
            case OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions";
            case OPENAI -> "https://api.openai.com/v1/chat/completions";
            default -> throw new RuntimeException("Unsupported provider: " + activeProvider);
        };

        JSONObject payload = new JSONObject();
        payload.put("model", activeModel);
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "user").put("content", prompt));
        payload.put("messages", messages);
        payload.put("max_tokens", 4096);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + key)
                .timeout(Duration.ofSeconds(90))
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()));

        // OpenRouter recommended headers
        if (activeProvider == Provider.OPENROUTER) {
            reqBuilder.header("HTTP-Referer", "https://medimanage.app");
            reqBuilder.header("X-Title", "MediManage");
        }

        return client.sendAsync(reqBuilder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        JSONObject json = new JSONObject(response.body());
                        return json.getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");
                    }
                    throw new RuntimeException(handleError(response));
                });
    }

    // --- Claude Format ---

    private CompletableFuture<String> sendClaude(String prompt) {
        String key = apiKeys.get(Provider.CLAUDE);

        JSONObject payload = new JSONObject();
        payload.put("model", activeModel);
        payload.put("max_tokens", 4096);
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "user").put("content", prompt));
        payload.put("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", key)
                .header("anthropic-version", "2023-06-01")
                .timeout(Duration.ofSeconds(90))
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        JSONObject json = new JSONObject(response.body());
                        return json.getJSONArray("content")
                                .getJSONObject(0)
                                .getString("text");
                    }
                    throw new RuntimeException(handleError(response));
                });
    }

    // --- Error Handling ---

    private String handleError(HttpResponse<String> response) {
        int code = response.statusCode();
        if (code == 429) {
            return "429: Rate limited — please wait a moment and try again.";
        }
        // Try to extract message from JSON error
        try {
            JSONObject err = new JSONObject(response.body());
            if (err.has("error")) {
                Object errorObj = err.get("error");
                if (errorObj instanceof JSONObject eo) {
                    return activeProvider + " Error (" + code + "): " + eo.optString("message", response.body());
                }
                return activeProvider + " Error (" + code + "): " + errorObj.toString();
            }
        } catch (Exception ignored) {
        }
        return activeProvider + " Error (" + code + "): " + response.body();
    }

    // --- Structured JSON Output ---

    /**
     * Override default: requests JSON-structured response from the AI.
     * For Gemini, uses the native response_mime_type for JSON mode.
     */
    @Override
    public CompletableFuture<String> chatWithJsonSchema(String prompt, String jsonSchemaHint) {
        String jsonPrompt = prompt + "\n\nRespond with ONLY valid JSON matching this schema:\n" + jsonSchemaHint
                + "\nDo NOT include markdown fences or explanation — raw JSON only.";
        return chat(jsonPrompt).thenApply(CloudAIService::parseJsonResponse);
    }

    /**
     * Utility: strip markdown fences and whitespace from AI response to extract raw
     * JSON.
     */
    public static String parseJsonResponse(String rawText) {
        if (rawText == null)
            return "{}";
        String cleaned = rawText.trim();
        // Strip ```json ... ``` fences
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline > 0) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();
        }
        return cleaned;
    }
}
