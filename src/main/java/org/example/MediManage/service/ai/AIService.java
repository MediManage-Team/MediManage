package org.example.MediManage.service.ai;

import java.util.concurrent.CompletableFuture;

/**
 * Core AI service interface for Java-side clients that talk to the Python AI engine.
 */
public interface AIService {

    /**
     * Send a plain text prompt and get a response.
     */
    CompletableFuture<String> chat(String prompt);

    /**
     * Check if this AI service is currently available and configured.
     */
    boolean isAvailable();

    /**
     * Get a human-readable name for this provider (e.g. "Google Gemini (Cloud)").
     */
    String getProviderName();

    // ======================== EXTENDED METHODS (defaults) ========================

    /**
     * Chat with additional business context injected (RAG-style).
     * Default: concatenates context and prompt.
     */
    default CompletableFuture<String> chatWithContext(String prompt, String context) {
        String enrichedPrompt = "Context:\n" + context + "\n\nQuestion:\n" + prompt;
        return chat(enrichedPrompt);
    }

    /**
     * Chat requesting a JSON-structured response.
     * Appends schema instructions to help the AI return valid JSON.
     * Default: adds JSON instruction suffix.
     */
    default CompletableFuture<String> chatWithJsonSchema(String prompt, String jsonSchemaHint) {
        String jsonPrompt = prompt + "\n\nRespond with ONLY valid JSON matching this schema:\n" + jsonSchemaHint
                + "\nDo NOT include markdown fences or explanation — raw JSON only.";
        return chat(jsonPrompt);
    }
}
