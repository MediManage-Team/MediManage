package org.example.MediManage.service.ai;

import org.example.MediManage.security.CloudApiKeyStore;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AIOrchestratorCloudConfigTest {

    @Test
    void defaultsBlankModelToProviderDefault() {
        assertEquals(
                "gemini-2.5-flash",
                AIOrchestrator.resolveConfiguredCloudModel(CloudApiKeyStore.Provider.GEMINI, "")
        );
    }

    @Test
    void staleKnownModelFallsBackToCurrentProviderDefault() {
        assertEquals(
                "gpt-4o",
                AIOrchestrator.resolveConfiguredCloudModel(
                        CloudApiKeyStore.Provider.OPENAI,
                        "claude-3-opus-20240229"
                )
        );
    }

    @Test
    void providerResolutionNormalizesAndDefaultsSafely() {
        assertEquals(
                CloudApiKeyStore.Provider.GEMINI,
                AIOrchestrator.resolveCloudProvider(" gemini ")
        );
        assertEquals(
                CloudApiKeyStore.Provider.GEMINI,
                AIOrchestrator.resolveCloudProvider("not-a-provider")
        );
    }

    @Test
    void processOrchestrationForwardsActionDataAndCloudConfigToPythonService() {
        StubPythonAIService pythonService = new StubPythonAIService();
        JSONObject cloudConfig = new JSONObject()
                .put("provider", "GROQ")
                .put("model", "llama-3.3-70b-versatile")
                .put("api_key", "test-key");

        AIOrchestrator orchestrator = new TestableAIOrchestrator(pythonService, cloudConfig);
        JSONObject data = new JSONObject()
                .put("prompt", "Recommend safer expiry actions")
                .put("business_context", "Expiring items: Paracetamol");

        String result = orchestrator.processOrchestration("combined_analysis", data, "cloud_only", true).join();

        assertEquals("python-service-response", result);
        assertEquals("combined_analysis", pythonService.lastAction);
        assertSame(data, pythonService.lastData);
        assertEquals(cloudConfig.toString(), pythonService.lastCloudConfig.toString());
        assertEquals("cloud_only", pythonService.lastRouting);
        assertTrue(pythonService.lastUseSearch);
    }

    @Test
    void queryDatabaseUsesCloudOnlyRoutingWithoutSearch() {
        StubPythonAIService pythonService = new StubPythonAIService();
        AIOrchestrator orchestrator = new TestableAIOrchestrator(pythonService, new JSONObject());

        String result = orchestrator.queryDatabase("inventory_summary").join();

        assertEquals("python-service-response", result);
        assertEquals("inventory_summary", pythonService.lastAction);
        assertEquals("cloud_only", pythonService.lastRouting);
        assertFalse(pythonService.lastUseSearch);
    }

    private static final class StubPythonAIService extends LocalAIService {
        private String lastAction;
        private JSONObject lastData;
        private JSONObject lastCloudConfig;
        private String lastRouting;
        private boolean lastUseSearch;

        StubPythonAIService() {
            super(false);
        }

        @Override
        public CompletableFuture<String> orchestrate(
                String action,
                JSONObject data,
                JSONObject cloudConfig,
                String routing,
                boolean useSearch) {
            lastAction = action;
            lastData = data;
            lastCloudConfig = cloudConfig;
            lastRouting = routing;
            lastUseSearch = useSearch;
            return CompletableFuture.completedFuture("python-service-response");
        }
    }

    private static final class TestableAIOrchestrator extends AIOrchestrator {
        private final JSONObject cloudConfig;

        TestableAIOrchestrator(LocalAIService pythonService, JSONObject cloudConfig) {
            super(pythonService);
            this.cloudConfig = cloudConfig;
        }

        @Override
        JSONObject buildCloudConfig() {
            return cloudConfig;
        }
    }
}
