package org.example.MediManage.service.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

/**
 * Routes queries between Local AI (business tasks) and Cloud AI (medical
 * precision).
 * 
 * Features:
 * - Intelligent routing (precision → Cloud, business → Local, fallback)
 * - LRU response cache (5-minute TTL, max 100 entries)
 * - Rate limiting (max 5 concurrent AI requests via Semaphore)
 * - JSON-structured output routing
 */
public class AIOrchestrator {
    private final LocalAIService localService;
    private final CloudAIService cloudService;
    private boolean forceCloud = false;

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

    /**
     * Default constructor — creates services with auto-configuration.
     */
    public AIOrchestrator() {
        this.localService = new LocalAIService();
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences
                .userNodeForPackage(org.example.MediManage.SettingsController.class);
        String apiKey = prefs.get("cloud_api_key", "");
        this.cloudService = new CloudAIService(apiKey);
    }

    /**
     * Constructor with injected services — prevents redundant instantiation.
     */
    public AIOrchestrator(LocalAIService localService, CloudAIService cloudService) {
        this.localService = localService;
        this.cloudService = cloudService;
    }

    // ======================== CONFIGURATION ========================

    public void setCloudApiKey(String key) {
        this.cloudService.setApiKey(key);
    }

    public void setForceCloud(boolean force) {
        this.forceCloud = force;
    }

    // ======================== MAIN ROUTING ========================

    public CompletableFuture<String> processQuery(String query, boolean requiresPrecision, boolean useSearch) {
        // Check cache first
        String cacheKey = "process:" + query.hashCode() + ":" + requiresPrecision + ":" + useSearch;
        String cached = getCached(cacheKey);
        if (cached != null)
            return CompletableFuture.completedFuture(cached);

        return withRateLimit(() -> {
            if (requiresPrecision || forceCloud) {
                if (cloudService.isAvailable()) {
                    return cloudService.chat(query).thenApply(r -> cacheAndReturn(cacheKey, r));
                } else if (localService.isAvailable()) {
                    return localService.chat(query, useSearch).thenApply(r -> cacheAndReturn(cacheKey, r));
                }
                return CompletableFuture.failedFuture(
                        new RuntimeException(
                                "Cloud AI required but not available. Please configure API key in Settings."));
            }

            if (localService.isAvailable()) {
                return localService.chat(query, useSearch)
                        .thenApply(r -> cacheAndReturn(cacheKey, r))
                        .exceptionally(ex -> {
                            if (cloudService.isAvailable()) {
                                return cacheAndReturn(cacheKey, cloudService.chat(query).join());
                            }
                            return "Error: Local AI failed and Cloud AI unavailable. " + ex.getMessage();
                        });
            } else if (cloudService.isAvailable()) {
                return cloudService.chat(query).thenApply(r -> cacheAndReturn(cacheKey, r));
            } else {
                return CompletableFuture.completedFuture(
                        "AI Service Unavailable. Please start Local Engine or configure Cloud API key in Settings.");
            }
        });
    }

    // ======================== EXPLICIT ROUTING ========================

    /**
     * Always use Cloud AI. For billing care protocols, drug info, medical
     * precision.
     */
    public CompletableFuture<String> cloudQuery(String prompt) {
        String cacheKey = "cloud:" + prompt.hashCode();
        String cached = getCached(cacheKey);
        if (cached != null)
            return CompletableFuture.completedFuture(cached);

        if (cloudService.isAvailable()) {
            return withRateLimit(() -> cloudService.chat(prompt).thenApply(r -> cacheAndReturn(cacheKey, r)));
        }
        return CompletableFuture.failedFuture(
                new RuntimeException("Cloud AI not available. Please configure API key in Settings."));
    }

    /**
     * Cloud query expecting structured JSON response.
     */
    public CompletableFuture<String> cloudQueryWithJson(String prompt, String jsonSchemaHint) {
        if (cloudService.isAvailable()) {
            return withRateLimit(() -> cloudService.chatWithJsonSchema(prompt, jsonSchemaHint));
        }
        return CompletableFuture.failedFuture(
                new RuntimeException("Cloud AI not available. Please configure API key in Settings."));
    }

    /**
     * Always use Local AI with business context (RAG).
     * Falls back to Cloud if local is unavailable.
     */
    public CompletableFuture<String> localQueryWithContext(String prompt, String businessContext) {
        String cacheKey = "local:" + prompt.hashCode() + ":" + businessContext.hashCode();
        String cached = getCached(cacheKey);
        if (cached != null)
            return CompletableFuture.completedFuture(cached);

        return withRateLimit(() -> {
            if (localService.isAvailable()) {
                return localService.chatWithContext(prompt, businessContext)
                        .thenApply(r -> cacheAndReturn(cacheKey, r));
            }
            if (cloudService.isAvailable()) {
                String augmented = "### Business Data\n" + businessContext + "\n\n### Query\n" + prompt;
                return cloudService.chat(augmented).thenApply(r -> cacheAndReturn(cacheKey, r));
            }
            return CompletableFuture.failedFuture(
                    new RuntimeException(
                            "No AI service available. Please start local engine or configure Cloud API key."));
        });
    }

    /**
     * Combined query: Local AI analyzes business data → Cloud AI adds medical
     * precision.
     */
    public CompletableFuture<String> combinedQuery(String prompt, String businessContext) {
        if (!localService.isAvailable() && !cloudService.isAvailable()) {
            return CompletableFuture.failedFuture(
                    new RuntimeException("No AI service available."));
        }

        return withRateLimit(() -> {
            CompletableFuture<String> localAnalysis;
            if (localService.isAvailable()) {
                localAnalysis = localService.chatWithContext(
                        "Analyze this business data and produce a concise summary with key findings:\n" + prompt,
                        businessContext);
            } else {
                localAnalysis = CompletableFuture.completedFuture(
                        "Business Data Summary:\n" + businessContext);
            }

            return localAnalysis.thenCompose(localResult -> {
                if (cloudService.isAvailable()) {
                    String cloudPrompt = "Based on this business analysis:\n\n" +
                            localResult + "\n\n" +
                            "Now answer the following with medical/pharmaceutical precision:\n" + prompt;
                    return cloudService.chat(cloudPrompt);
                }
                return CompletableFuture.completedFuture(localResult);
            });
        });
    }

    // ======================== CACHE UTILITIES ========================

    private synchronized String getCached(String key) {
        CacheEntry entry = responseCache.get(key);
        if (entry != null && !entry.isExpired()) {
            System.out.println("⚡ AI Cache hit for key: " + key.substring(0, Math.min(40, key.length())));
            return entry.response();
        }
        if (entry != null) {
            responseCache.remove(key); // Evict expired
        }
        return null;
    }

    private synchronized String cacheAndReturn(String key, String response) {
        responseCache.put(key, new CacheEntry(response, System.currentTimeMillis()));
        return response;
    }

    /**
     * Manually clear the response cache.
     */
    public synchronized void clearCache() {
        responseCache.clear();
        System.out.println("🗑️ AI response cache cleared.");
    }

    // ======================== RATE LIMITER ========================

    private <T> CompletableFuture<T> withRateLimit(java.util.function.Supplier<CompletableFuture<T>> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                rateLimiter.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Rate limiter interrupted", e);
            }
            return null;
        }).thenCompose(ignored -> {
            try {
                return task.get();
            } finally {
                rateLimiter.release();
            }
        });
    }

    // ======================== STATUS ========================

    public boolean isLocalAvailable() {
        return localService.isAvailable();
    }

    public boolean isCloudAvailable() {
        return cloudService.isAvailable();
    }
}
