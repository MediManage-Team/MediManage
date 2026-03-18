package org.example.MediManage.service.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.example.MediManage.security.CloudApiKeyStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.prefs.Preferences;

/**
 * Unified AI Orchestrator that proxies requests to the Python AI Engine.
 * 
 * Features:
 * - LRU response cache (5-minute TTL, max 100 entries)
 * - Rate limiting (max 5 concurrent AI requests via Semaphore)
 * - Feeds UI Cloud Configurations (Provider, Keys) directly to Python
 */
public class AIOrchestrator {
    private final LocalAIService pythonService;
    private boolean forceCloud = false;

    private static final Map<CloudApiKeyStore.Provider, List<String>> CLOUD_MODELS = Map.of(
            CloudApiKeyStore.Provider.GEMINI, List.of("gemini-2.5-flash", "gemini-2.5-pro", "gemini-1.5-pro"),
            CloudApiKeyStore.Provider.GROQ, List.of("llama-3.3-70b-versatile", "mixtral-8x7b-32768", "gemma2-9b-it"),
            CloudApiKeyStore.Provider.OPENROUTER, List.of("anthropic/claude-3.5-sonnet", "google/gemini-2.5-flash", "meta-llama/llama-3.3-70b-instruct"),
            CloudApiKeyStore.Provider.OPENAI, List.of("gpt-4o", "gpt-4o-mini", "o1-mini"),
            CloudApiKeyStore.Provider.CLAUDE, List.of("claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022", "claude-3-opus-20240229")
    );

    // ======================== CACHE ========================
    private static final int MAX_CACHE_SIZE = 100;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

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

    // ======================== RATE LIMITER ========================
    private static final int MAX_CONCURRENT_REQUESTS = 5;
    private final Semaphore rateLimiter = new Semaphore(MAX_CONCURRENT_REQUESTS);

    // ======================== CONSTRUCTORS ========================

    public AIOrchestrator() {
        this.pythonService = new LocalAIService();
    }

    public AIOrchestrator(LocalAIService pythonService) {
        this.pythonService = pythonService;
    }

    // ======================== CONFIGURATION ========================

    public void setForceCloud(boolean force) {
        this.forceCloud = force;
    }

    public boolean isForceCloud() {
        return forceCloud;
    }

    static CloudApiKeyStore.Provider resolveCloudProvider(String configuredProvider) {
        String normalized = configuredProvider == null
                ? ""
                : configuredProvider.trim().toUpperCase(Locale.ROOT);
        try {
            return CloudApiKeyStore.Provider.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return CloudApiKeyStore.Provider.GEMINI;
        }
    }

    static String resolveConfiguredCloudModel(CloudApiKeyStore.Provider provider, String configuredModel) {
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

    private JSONObject buildCloudConfig() {
        Preferences prefs = Preferences.userNodeForPackage(org.example.MediManage.MediManageApplication.class);
        CloudApiKeyStore.Provider provider = resolveCloudProvider(prefs.get("cloud_provider", "GEMINI"));
        String model = resolveConfiguredCloudModel(provider, prefs.get("cloud_model", ""));
        String apiKey = "";

        try {
            apiKey = CloudApiKeyStore.get(provider).trim();
        } catch (Exception e) {
            // Fallback for custom logic if needed
        }

        JSONObject config = new JSONObject();
        config.put("provider", provider.name());
        config.put("model", model);
        config.put("api_key", apiKey);
        return config;
    }

    // ======================== MAIN ROUTING ========================

    /**
     * Entry point for standard or specific orchestrated actions evaluated in Python.
     */
    public CompletableFuture<String> processOrchestration(String action, JSONObject data, String routingOverride, boolean useSearch) {
        String cacheKey = "process:" + action + ":" + (data != null ? data.toString() : "") + ":" + routingOverride + ":" + useSearch;
        String cached = getCached(cacheKey);
        if (cached != null) return CompletableFuture.completedFuture(cached);

        String activeRouting = forceCloud ? "cloud_only" : (routingOverride != null ? routingOverride : "auto");

        return withRateLimit(() -> pythonService.orchestrate(action, data, buildCloudConfig(), activeRouting, useSearch)
                .thenApply(r -> cacheAndReturn(cacheKey, r)));
    }

    public CompletableFuture<String> processQuery(String query, boolean requiresPrecision, boolean useSearch) {
        JSONObject data = new JSONObject().put("prompt", query);
        String routing = requiresPrecision ? "cloud_only" : "auto";
        return processOrchestration("raw_chat", data, routing, useSearch);
    }

    // ======================== EXPLICIT ROUTING ========================

    public CompletableFuture<String> cloudQuery(String prompt) {
        JSONObject data = new JSONObject().put("prompt", prompt);
        return processOrchestration("raw_chat", data, "cloud_only", false);
    }

    public CompletableFuture<String> localQueryWithContext(String prompt, String businessContext) {
        JSONObject data = new JSONObject()
                .put("prompt", prompt)
                .put("business_context", businessContext);
        return processOrchestration("raw_chat", data, "local_fallback", false);
    }

    public CompletableFuture<String> queryDatabase(String actionKey) {
        // e.g. actionKey = "sales_db_query"
        return pythonService.orchestrate(actionKey, null, null, "local_only", false);
    }

    public CompletableFuture<String> combinedQuery(String prompt, String businessContext) {
        JSONObject data = new JSONObject()
                .put("prompt", prompt)
                .put("business_context", businessContext);
        return processOrchestration("combined_analysis", data, "auto", false);
    }

    // ======================== CACHE UTILITIES ========================

    private synchronized String getCached(String key) {
        CacheEntry entry = responseCache.get(key);
        if (entry != null && !entry.isExpired()) {
            System.out.println("⚡ AI Cache hit for key: " + key.substring(0, Math.min(40, key.length())));
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

    public synchronized void clearCache() {
        responseCache.clear();
        System.out.println("🗑️ AI response cache cleared.");
    }

    // ======================== RATE LIMITER ========================

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

    // ======================== STATUS ========================

    public boolean isLocalAvailable() {
        return pythonService.isAvailable();
    }

    // ======================== LOCAL MODEL MANAGEMENT ========================

    public void loadLocalModel() {
        pythonService.loadModel();
    }

    public void loadLocalModel(String modelPath, String hardwareConfig) {
        pythonService.loadModel(modelPath, hardwareConfig);
    }

    public JSONObject getLocalHealth() {
        return pythonService.getHealth();
    }

    public void startModelDownload(String repoId, String filename, String source) {
        pythonService.startDownload(repoId, filename, source);
    }

    public JSONObject getModelDownloadStatus() {
        return pythonService.getDownloadStatus();
    }

    public void stopModelDownload() {
        pythonService.stopDownload();
    }

    public JSONArray listLocalModels() {
        return pythonService.listModels();
    }

    public boolean deleteLocalModel(String modelPath) {
        return pythonService.deleteModel(modelPath);
    }

    public void cancelLocalGeneration() {
        pythonService.cancelGeneration();
    }
}
