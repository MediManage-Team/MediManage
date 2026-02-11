package org.example.MediManage.service.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.json.JSONArray;
import org.json.JSONObject;

public class CloudAIService implements AIService {
    private String apiKey;
    private final HttpClient client;
    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=";

    public CloudAIService(String apiKey) {
        this.apiKey = apiKey;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public CompletableFuture<String> chat(String prompt) {
        if (!isAvailable()) {
            return CompletableFuture.failedFuture(new IllegalStateException("API Key not configured."));
        }

        // Construct JSON Payload for Gemini
        JSONObject part = new JSONObject();
        part.put("text", prompt);

        JSONArray parts = new JSONArray();
        parts.put(part);

        JSONObject content = new JSONObject();
        content.put("parts", parts);

        JSONArray contents = new JSONArray();
        contents.put(content);

        JSONObject payload = new JSONObject();
        payload.put("contents", contents);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GEMINI_URL + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JSONObject json = new JSONObject(response.body());
                            return json.getJSONArray("candidates")
                                    .getJSONObject(0)
                                    .getJSONObject("content")
                                    .getJSONArray("parts")
                                    .getJSONObject(0)
                                    .getString("text");
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to parse AI response: " + e.getMessage());
                        }
                    } else {
                        throw new RuntimeException(
                                "Cloud AI Error (" + response.statusCode() + "): " + response.body());
                    }
                });
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isEmpty() && !apiKey.equals("YOUR_API_KEY");
    }

    @Override
    public String getProviderName() {
        return "Google Gemini (Cloud)";
    }
}
