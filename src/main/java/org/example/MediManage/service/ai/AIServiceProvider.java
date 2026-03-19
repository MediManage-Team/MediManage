package org.example.MediManage.service.ai;

/**
 * Singleton holder for the Java-side Python AI client and orchestrator.
 */
public class AIServiceProvider {
    private static volatile AIServiceProvider instance;

    private final PythonAIClient pythonService;
    private final AIOrchestrator orchestrator;

    private AIServiceProvider() {
        this.pythonService = new PythonAIClient(false);
        this.orchestrator = new AIOrchestrator(pythonService);
    }

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

    public PythonAIClient getPythonService() {
        return pythonService;
    }

    public AIOrchestrator getOrchestrator() {
        return orchestrator;
    }
}
