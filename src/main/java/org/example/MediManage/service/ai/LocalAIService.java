package org.example.MediManage.service.ai;

import org.example.MediManage.security.LocalAdminTokenManager;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Thin HTTP client for the Python AI engine.
 * The Python service owns all AI routing, prompts, provider selection, and cloud calls.
 */
public class LocalAIService implements AIService {
    private static final String BASE_URL = "http://127.0.0.1:5000";
    private static final String CHAT_URL = BASE_URL + "/chat";
    private static final String ORCHESTRATE_URL = BASE_URL + "/orchestrate";
    private static final String HEALTH_URL = BASE_URL + "/health";
    private static final String CANCEL_URL = BASE_URL + "/cancel_chat";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public LocalAIService() {
    }

    public LocalAIService(boolean ignored) {
    }

    @Override
    public CompletableFuture<String> chat(String prompt) {
        JSONObject payload = new JSONObject().put("prompt", prompt);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(CHAT_URL))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()));
        LocalAdminTokenManager.applyHeader(requestBuilder);

        return client.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return new JSONObject(response.body()).optString("response", "");
                    }
                    throw new RuntimeException("AI Engine Error: " + response.statusCode());
                });
    }

    public CompletableFuture<String> orchestrate(
            String action,
            JSONObject data,
            JSONObject cloudConfig,
            String routing,
            boolean useSearch) {
        JSONObject payload = new JSONObject().put("action", action).put("use_search", useSearch);
        if (data != null) {
            payload.put("data", data);
        }
        if (cloudConfig != null) {
            payload.put("cloud_config", cloudConfig);
        }
        if (routing != null && !routing.isBlank()) {
            payload.put("routing", routing);
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(ORCHESTRATE_URL))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()));
        LocalAdminTokenManager.applyHeader(requestBuilder);

        return client.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return new JSONObject(response.body()).optString("response", "");
                    }

                    String error = "AI Orchestration Error: " + response.statusCode();
                    try {
                        error += " - " + new JSONObject(response.body()).optString("error", "");
                    } catch (Exception ignored) {
                    }
                    throw new RuntimeException(error);
                });
    }

    public JSONObject getHealth() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(HEALTH_URL))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return new JSONObject(response.body());
            }
            return new JSONObject()
                    .put("status", "error")
                    .put("message", "Health endpoint returned " + response.statusCode());
        } catch (Exception e) {
            return new JSONObject()
                    .put("status", "error")
                    .put("message", e.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(HEALTH_URL))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getProviderName() {
        return "Python Cloud AI Engine";
    }

    public void cancelGeneration() {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(CANCEL_URL))
                    .POST(HttpRequest.BodyPublishers.noBody());
            LocalAdminTokenManager.applyHeader(requestBuilder);
            client.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
        }
    }
}
