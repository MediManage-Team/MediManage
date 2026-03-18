package org.example.MediManage.service.ai;

import org.example.MediManage.security.CloudApiKeyStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
