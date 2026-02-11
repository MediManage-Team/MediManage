package org.example.MediManage.service.ai;

import java.util.concurrent.CompletableFuture;

public class AIOrchestrator {
    private final LocalAIService localService;
    private final CloudAIService cloudService;
    private boolean forceCloud = false;

    public AIOrchestrator() {
        this.localService = new LocalAIService();
        this.cloudService = new CloudAIService(""); // API Key set via settings
    }

    public void setCloudApiKey(String key) {
        this.cloudService.setApiKey(key);
    }

    public CompletableFuture<String> processQuery(String query, boolean requiresPrecision, boolean useSearch) {
        // Routing Logic
        // 1. If explicit precision required (e.g. medical info) -> Cloud
        // 2. If Local is available -> Local
        // 3. Fallback -> Cloud

        if (requiresPrecision || forceCloud) {
            if (cloudService.isAvailable()) {
                return cloudService.chat(query);
            } else {
                return CompletableFuture.failedFuture(new RuntimeException("Cloud AI required but not available."));
            }
        }

        if (localService.isAvailable()) {
            return localService.chat(query, useSearch)
                    .exceptionally(ex -> {
                        // Fallback to cloud on local failure
                        if (cloudService.isAvailable()) {
                            return cloudService.chat(query).join();
                        }
                        return "Error: Local AI failed and Cloud AI unavailable.";
                    });
        } else {
            if (cloudService.isAvailable()) {
                return cloudService.chat(query);
            } else {
                return CompletableFuture
                        .completedFuture("AI Service Unavailable. Please start Local Engine or configure Cloud API.");
            }
        }
    }

    public boolean isLocalAvailable() {
        return localService.isAvailable();
    }
}
