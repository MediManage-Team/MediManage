package org.example.MediManage.service.ai;

import org.example.MediManage.security.CloudApiKeyStore;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.prefs.Preferences;

/**
 * Central Java-side AI gateway.
 * Java only forwards requests to the Python AI engine, which owns all AI logic.
 */
public class AIOrchestrator {
    private final PythonAIClient pythonService;

    private static final Map<CloudApiKeyStore.Provider, List<String>> CLOUD_MODELS = Map.of(
            CloudApiKeyStore.Provider.GEMINI, List.of("gemini-2.5-flash", "gemini-2.5-pro", "gemini-1.5-pro"),
            CloudApiKeyStore.Provider.GROQ, List.of("llama-3.3-70b-versatile", "mixtral-8x7b-32768", "gemma2-9b-it"),
            CloudApiKeyStore.Provider.OPENROUTER, List.of("anthropic/claude-3.5-sonnet", "google/gemini-2.5-flash", "meta-llama/llama-3.3-70b-instruct"),
            CloudApiKeyStore.Provider.OPENAI, List.of("gpt-4o", "gpt-4o-mini", "o1-mini"),
            CloudApiKeyStore.Provider.CLAUDE, List.of("claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022", "claude-3-opus-20240229")
    );

    private static final int MAX_CACHE_SIZE = 100;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;
    private static final int MAX_CONCURRENT_REQUESTS = 5;

    private record CacheEntry(String response, long timestamp) {
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    private final Map<String, CacheEntry> responseCache = new LinkedHashMap<>(MAX_CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
            return size() > MAX_CACHE_SIZE || eldest.getValue().isExpired();
        }
    };

    private final Semaphore rateLimiter = new Semaphore(MAX_CONCURRENT_REQUESTS);

    public AIOrchestrator() {
        this(new PythonAIClient(false));
    }

    public AIOrchestrator(PythonAIClient pythonService) {
        this.pythonService = pythonService;
    }

    public static CloudApiKeyStore.Provider resolveCloudProvider(String configuredProvider) {
        String normalized = configuredProvider == null ? "" : configuredProvider.trim().toUpperCase(Locale.ROOT);
        try {
            return CloudApiKeyStore.Provider.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return CloudApiKeyStore.Provider.GEMINI;
        }
    }

    public static String resolveConfiguredCloudModel(CloudApiKeyStore.Provider provider, String configuredModel) {
        List<String> providerModels = CLOUD_MODELS.getOrDefault(provider, List.of());
        String trimmedModel = configuredModel == null ? "" : configuredModel.trim();
        if (trimmedModel.isBlank()) {
            return providerModels.isEmpty() ? "" : providerModels.get(0);
        }
        if (providerModels.contains(trimmedModel)) {
            return trimmedModel;
        }
        boolean knownForeignModel = CLOUD_MODELS.values().stream().anyMatch(models -> models.contains(trimmedModel));
        if (knownForeignModel && !providerModels.isEmpty()) {
            return providerModels.get(0);
        }
        return trimmedModel;
    }

    JSONObject buildCloudConfig() {
        Preferences prefs = Preferences.userNodeForPackage(org.example.MediManage.MediManageApplication.class);
        CloudApiKeyStore.Provider provider = resolveCloudProvider(prefs.get("cloud_provider", "GEMINI"));
        String model = resolveConfiguredCloudModel(provider, prefs.get("cloud_model", ""));
        String apiKey = "";
        try {
            apiKey = CloudApiKeyStore.get(provider).trim();
        } catch (Exception ignored) {
        }

        return new JSONObject()
                .put("provider", provider.name())
                .put("model", model)
                .put("api_key", apiKey);
    }

    public CompletableFuture<String> processOrchestration(String action, JSONObject data, String routingOverride, boolean useSearch) {
        String routing = (routingOverride == null || routingOverride.isBlank()) ? "cloud_only" : routingOverride;
        String cacheKey = "process:" + action + ":" + (data != null ? data.toString() : "") + ":" + routing + ":" + useSearch;
        String cached = getCached(cacheKey);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return withRateLimit(() -> pythonService.orchestrate(action, data, buildCloudConfig(), routing, useSearch)
                .thenApply(response -> cacheAndReturn(cacheKey, response)));
    }

    public CompletableFuture<String> processQuery(String query, boolean requiresPrecision, boolean useSearch) {
        JSONObject data = new JSONObject().put("prompt", query);
        return processOrchestration("raw_chat", data, "cloud_only", useSearch);
    }

    public CompletableFuture<String> cloudQuery(String prompt) {
        JSONObject data = new JSONObject().put("prompt", prompt);
        return processOrchestration("raw_chat", data, "cloud_only", false);
    }

    public CompletableFuture<String> queryDatabase(String actionKey) {
        return pythonService.orchestrate(actionKey, null, buildCloudConfig(), "cloud_only", false);
    }

    public CompletableFuture<String> combinedQuery(String prompt, String businessContext) {
        JSONObject data = new JSONObject()
                .put("prompt", prompt)
                .put("business_context", businessContext);
        return processOrchestration("combined_analysis", data, "cloud_only", false);
    }

    public boolean isEngineAvailable() {
        return pythonService.isAvailable();
    }

    public JSONObject getEngineHealth() {
        return pythonService.getHealth();
    }

    public void cancelGeneration() {
        pythonService.cancelGeneration();
    }

    public synchronized void clearCache() {
        responseCache.clear();
    }

    private synchronized String getCached(String key) {
        CacheEntry entry = responseCache.get(key);
        if (entry != null && !entry.isExpired()) {
            return entry.response();
        }
        if (entry != null) {
            responseCache.remove(key);
        }
        return null;
    }

    private synchronized String cacheAndReturn(String key, String response) {
        responseCache.put(key, new CacheEntry(response, System.currentTimeMillis()));
        return response;
    }

    private <T> CompletableFuture<T> withRateLimit(java.util.function.Supplier<CompletableFuture<T>> task) {
        return CompletableFuture.runAsync(() -> {
            try {
                rateLimiter.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Rate limiter interrupted", e);
            }
        }).thenCompose(ignored -> {
            CompletableFuture<T> taskFuture;
            try {
                taskFuture = task.get();
            } catch (RuntimeException e) {
                rateLimiter.release();
                return CompletableFuture.failedFuture(e);
            }
            return taskFuture.whenComplete((result, ex) -> rateLimiter.release());
        });
    }
}
