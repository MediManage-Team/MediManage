package org.example.MediManage.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.MediManage.model.BillItem;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;

public class GeminiService {
    private final HttpClient httpClient;
    private final Gson gson;
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=";

    public GeminiService() {
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
    }

    private String getApiKey() {
        Preferences prefs = Preferences.userNodeForPackage(org.example.MediManage.MediManageApplication.class);
        return prefs.get("gemini_api_key", null);
    }

    public CompletableFuture<String> generateCareProtocol(List<BillItem> medicines) {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("API Key not configured."));
        }

        String prompt = buildPrompt(medicines);
        String jsonBody = createJsonBody(prompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return extractContent(response.body());
                    } else {
                        throw new RuntimeException("API Error: " + response.statusCode() + " - " + response.body());
                    }
                });
    }

    public CompletableFuture<String> findGenericName(String brandName) {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("API Key not configured."));
        }

        String prompt = "What is the generic name for the medicine brand '" + brandName + "'? Return ONLY the generic name. If unknown, return 'UNKNOWN'.";
        String jsonBody = createJsonBody(prompt);

         HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return extractContent(response.body()).trim();
                    } else {
                        throw new RuntimeException("API Error: " + response.statusCode());
                    }
                });
    }

    private String buildPrompt(List<BillItem> medicines) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a pharmacist assistant. For the following list of medicines being purchased together, provide a structured JSON response with a care protocol.\n");
        sb.append("Medicines: \n");
        for (BillItem item : medicines) {
            sb.append("- ").append(item.getName()).append("\n");
        }
        sb.append("\nResponse JSON Format (Array of Objects):\n");
        sb.append("[\n");
        sb.append("  {\n");
        sb.append("  \"medicineName\": \"string\",\n");
        sb.append("  \"mechanism\": \"string (brief)\",\n");
        sb.append("  \"usage\": \"string (when to take, with/without food)\",\n");
        sb.append("  \"dietary\": \"string (foods to avoid/recommend)\",\n");
        sb.append("  \"sideEffects\": \"string (common ones)\",\n");
        sb.append("  \"substitutes\": \"string (generic names)\",\n");
        sb.append("  \"safety\": \"string (Safety warnings)\",\n");
        sb.append("  \"combinationalEffects\": \"string (Drug-Drug interactions if multiple meds, else 'None')\",\n");
        sb.append("  \"stopProtocol\": \"string (When to stop)\",\n");
        sb.append("  }\n");
        sb.append("]\n");
        sb.append("Return ONLY valid JSON.");
        return sb.toString();
    }

    private String createJsonBody(String prompt) {
        JsonObject part = new JsonObject();
        part.addProperty("text", prompt);

        JsonArray parts = new JsonArray();
        parts.add(part);

        JsonObject content = new JsonObject();
        content.add("parts", parts);

        JsonArray contents = new JsonArray();
        contents.add(content);

        JsonObject root = new JsonObject();
        root.add("contents", contents);

        return gson.toJson(root);
    }

    private String extractContent(String jsonResponse) {
        try {
            JsonObject root = JsonParser.parseString(jsonResponse).getAsJsonObject();
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates.size() > 0) {
                JsonObject candidate = candidates.get(0).getAsJsonObject();
                JsonObject content = candidate.getAsJsonObject("content");
                JsonArray parts = content.getAsJsonArray("parts");
                if (parts.size() > 0) {
                    return parts.get(0).getAsJsonObject().get("text").getAsString();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Error parsing AI response.";
    }
}
