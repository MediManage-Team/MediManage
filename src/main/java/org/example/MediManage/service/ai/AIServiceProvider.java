package org.example.MediManage.service.ai;

/**
 * Singleton service provider for all AI services.
 * Prevents duplicate instantiation of LocalAIService and
 * AIOrchestrator.
 * 
 * Usage:
 * AIServiceProvider.get().getOrchestrator()
 * AIServiceProvider.get().getLocalService()
 */
public class AIServiceProvider {

    private static volatile AIServiceProvider instance;

    private final LocalAIService localService;
    private final AIOrchestrator orchestrator;
    private final PythonEnvironmentManager envManager;

    private AIServiceProvider() {
        System.out.println("🧠 Initializing AI Service Provider (singleton)...");

        // Single LocalAIService — no auto-load (server may not be ready yet)
        this.localService = new LocalAIService(false);

        // Single AIOrchestrator — reuses the above local python service
        this.orchestrator = new AIOrchestrator(localService);

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

    public AIOrchestrator getOrchestrator() {
        return orchestrator;
    }

    public PythonEnvironmentManager getEnvManager() {
        return envManager;
    }

    /**
     * Trigger model reload (e.g. after settings change).
     */
    public void reloadModel() {
        orchestrator.loadLocalModel();
    }
}
