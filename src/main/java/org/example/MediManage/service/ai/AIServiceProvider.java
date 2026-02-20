package org.example.MediManage.service.ai;

/**
 * Singleton service provider for all AI services.
 * Prevents duplicate instantiation of LocalAIService, CloudAIService, and
 * AIOrchestrator.
 * 
 * Usage:
 * AIServiceProvider.get().getOrchestrator()
 * AIServiceProvider.get().getLocalService()
 * AIServiceProvider.get().getCloudService()
 */
public class AIServiceProvider {

    private static volatile AIServiceProvider instance;

    private final LocalAIService localService;
    private final CloudAIService cloudService;
    private final AIOrchestrator orchestrator;
    private final PythonEnvironmentManager envManager;

    private AIServiceProvider() {
        System.out.println("🧠 Initializing AI Service Provider (singleton)...");

        // Single LocalAIService — no auto-load (server may not be ready yet)
        this.localService = new LocalAIService(false);

        // Single CloudAIService — loads API key from prefs
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences
                .userNodeForPackage(org.example.MediManage.SettingsController.class);
        String apiKey = prefs.get("cloud_api_key", "");
        this.cloudService = new CloudAIService(apiKey);

        // Single AIOrchestrator — reuses the above services
        this.orchestrator = new AIOrchestrator(localService, cloudService);

        // Single PythonEnvironmentManager
        this.envManager = new PythonEnvironmentManager();

        System.out.println("✅ AI Service Provider ready.");
    }

    /**
     * Get the singleton instance. Thread-safe via double-checked locking.
     */
    public static AIServiceProvider get() {
        if (instance == null) {
            synchronized (AIServiceProvider.class) {
                if (instance == null) {
                    instance = new AIServiceProvider();
                }
            }
        }
        return instance;
    }

    public LocalAIService getLocalService() {
        return localService;
    }

    public CloudAIService getCloudService() {
        return cloudService;
    }

    public AIOrchestrator getOrchestrator() {
        return orchestrator;
    }

    public PythonEnvironmentManager getEnvManager() {
        return envManager;
    }

    /**
     * Update the cloud API key at runtime (e.g. from Settings).
     * Legacy method — sets the Gemini key. Use configureCloudProvider for full
     * control.
     */
    public void setCloudApiKey(String key) {
        cloudService.setApiKey(key);
    }

    /**
     * Configure the active cloud provider, model, and API key at runtime.
     */
    public void configureCloudProvider(CloudAIService.Provider provider, String model, String apiKey) {
        cloudService.configure(provider, model, apiKey);
    }

    /**
     * Trigger model reload (e.g. after settings change).
     */
    public void reloadModel() {
        localService.loadModel();
    }
}
