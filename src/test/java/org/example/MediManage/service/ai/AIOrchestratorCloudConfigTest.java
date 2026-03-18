package org.example.MediManage.service.ai;

import org.example.MediManage.security.CloudApiKeyStore;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void combinedAnalysisCloudFallbackPromptIncludesBusinessContext() {
        JSONObject data = new JSONObject()
                .put("prompt", "Recommend safer expiry actions")
                .put("business_context", "Expiring items: Paracetamol");

        String prompt = AIActionPromptBuilder.buildDirectCloudFallbackPrompt("combined_analysis", data);

        assertTrue(prompt.contains("Business Data Summary:\nExpiring items: Paracetamol"));
        assertTrue(prompt.contains("Recommend safer expiry actions"));
    }

    @Test
    void cloudOnlyRequestsFallbackDirectlyWhenPythonSidecarIsDown() {
        StubLocalAIService localAI = new StubLocalAIService();
        StubCloudAIService cloudAI = new StubCloudAIService();
        JSONObject cloudConfig = new JSONObject()
                .put("provider", "GROQ")
                .put("model", "")
                .put("api_key", "test-key");

        AIOrchestrator orchestrator = new TestableAIOrchestrator(localAI, cloudAI, cloudConfig);
        JSONObject data = new JSONObject()
                .put("medicines", new JSONArray().put("Ace T 100mg/4mg Tablet"));

        String result = orchestrator.processOrchestration("detailed_protocol", data, "cloud_only", false).join();

        assertEquals("cloud-fallback-response", result);
        assertTrue(cloudAI.lastPrompt.contains("Patient Care Protocol"));
        assertTrue(cloudAI.lastPrompt.contains("Ace T 100mg/4mg Tablet"));
    }

    private static final class StubLocalAIService extends LocalAIService {
        StubLocalAIService() {
            super(false);
        }

        @Override
        public java.util.concurrent.CompletableFuture<String> orchestrate(
                String action,
                JSONObject data,
                JSONObject cloudConfig,
                String routing,
                boolean useSearch) {
            return java.util.concurrent.CompletableFuture.failedFuture(
                    new java.net.ConnectException("Connection refused"));
        }
    }

    private static final class StubCloudAIService extends CloudAIService {
        private String lastPrompt = "";

        @Override
        java.util.concurrent.CompletableFuture<String> chat(JSONObject cloudConfig, String prompt) {
            lastPrompt = prompt;
            return java.util.concurrent.CompletableFuture.completedFuture("cloud-fallback-response");
        }
    }

    private static final class TestableAIOrchestrator extends AIOrchestrator {
        private final JSONObject cloudConfig;

        TestableAIOrchestrator(LocalAIService pythonService, CloudAIService cloudService, JSONObject cloudConfig) {
            super(pythonService, cloudService);
            this.cloudConfig = cloudConfig;
        }

        @Override
        JSONObject buildCloudConfig() {
            return cloudConfig;
        }
    }
}
