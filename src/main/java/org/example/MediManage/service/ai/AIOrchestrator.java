package org.example.MediManage.service.ai;

import java.util.concurrent.CompletableFuture;

/**
 * Routes queries between Local AI (business tasks) and Cloud AI (medical
 * precision).
 * 
 * Routing Logic:
 * 1. If precision required (drug info, dosage, interactions) → Cloud (Gemini)
 * 2. If local AI is available → Local (ONNX/OpenVINO/CUDA)
 * 3. Fallback → Cloud
 */
public class AIOrchestrator {
    private final LocalAIService localService;
    private final CloudAIService cloudService;
    private boolean forceCloud = false;

    /**
     * Default constructor — creates services with auto-configuration.
     */
    public AIOrchestrator() {
        this.localService = new LocalAIService();
        // Load API key from preferences
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

    public void setCloudApiKey(String key) {
        this.cloudService.setApiKey(key);
    }

    public void setForceCloud(boolean force) {
        this.forceCloud = force;
    }

    public CompletableFuture<String> processQuery(String query, boolean requiresPrecision, boolean useSearch) {
        // Routing Logic:
        // 1. If explicit precision required (e.g. medical info) → Cloud
        // 2. If Local is available → Local
        // 3. Fallback → Cloud

        if (requiresPrecision || forceCloud) {
            if (cloudService.isAvailable()) {
                return cloudService.chat(query);
            } else {
                // Fall back to local if cloud is unavailable
                if (localService.isAvailable()) {
                    return localService.chat(query, useSearch);
                }
                return CompletableFuture.failedFuture(
                        new RuntimeException(
                                "Cloud AI required but not available. Please configure API key in Settings."));
            }
        }

        if (localService.isAvailable()) {
            return localService.chat(query, useSearch)
                    .exceptionally(ex -> {
                        // Fallback to cloud on local failure
                        if (cloudService.isAvailable()) {
                            return cloudService.chat(query).join();
                        }
                        return "Error: Local AI failed and Cloud AI unavailable. " + ex.getMessage();
                    });
        } else {
            if (cloudService.isAvailable()) {
                return cloudService.chat(query);
            } else {
                return CompletableFuture
                        .completedFuture(
                                "AI Service Unavailable. Please start Local Engine or configure Cloud API key in Settings.");
            }
        }
    }

    // ======================== EXPLICIT ROUTING ========================

    /**
     * Always use Cloud AI (Gemini). For billing care protocols, drug info, medical
     * precision.
     */
    public CompletableFuture<String> cloudQuery(String prompt) {
        if (cloudService.isAvailable()) {
            return cloudService.chat(prompt);
        }
        return CompletableFuture.failedFuture(
                new RuntimeException("Cloud AI not available. Please configure API key in Settings."));
    }

    /**
     * Always use Local AI with business context (RAG).
     * For stock management, restock forecasting, expiry analysis.
     * Falls back to Cloud if local is unavailable.
     */
    public CompletableFuture<String> localQueryWithContext(String prompt, String businessContext) {
        if (localService.isAvailable()) {
            return localService.chatWithContext(prompt, businessContext);
        }
        // Fallback: send context-augmented prompt to Cloud
        if (cloudService.isAvailable()) {
            String augmented = "### Business Data\n" + businessContext + "\n\n### Query\n" + prompt;
            return cloudService.chat(augmented);
        }
        return CompletableFuture.failedFuture(
                new RuntimeException("No AI service available. Please start local engine or configure Cloud API key."));
    }

    /**
     * Combined query: Local AI analyzes business data → Cloud AI adds medical
     * precision.
     * Example: "Which expiring drugs need special disposal?" →
     * Local analyzes stock data → Cloud gives medical disposal advice.
     */
    public CompletableFuture<String> combinedQuery(String prompt, String businessContext) {
        if (!localService.isAvailable() && !cloudService.isAvailable()) {
            return CompletableFuture.failedFuture(
                    new RuntimeException("No AI service available."));
        }

        // Step 1: Local AI analyzes business data
        CompletableFuture<String> localAnalysis;
        if (localService.isAvailable()) {
            localAnalysis = localService.chatWithContext(
                    "Analyze this business data and produce a concise summary with key findings:\n" + prompt,
                    businessContext);
        } else {
            // If no local, just pass context directly
            localAnalysis = CompletableFuture.completedFuture(
                    "Business Data Summary:\n" + businessContext);
        }

        // Step 2: Feed local analysis into Cloud for medical/precision answer
        return localAnalysis.thenCompose(localResult -> {
            if (cloudService.isAvailable()) {
                String cloudPrompt = "Based on this business analysis:\n\n" +
                        localResult + "\n\n" +
                        "Now answer the following with medical/pharmaceutical precision:\n" + prompt;
                return cloudService.chat(cloudPrompt);
            }
            // If no cloud, return local result
            return CompletableFuture.completedFuture(localResult);
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
