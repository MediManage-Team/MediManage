package org.example.MediManage.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.MediManage.SettingsController;
import org.example.MediManage.model.BillItem;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GeminiService {

    private static final String API_URL_TEMPLATE = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=%s";
    private final HttpClient httpClient;
    private final Gson gson;

    public GeminiService() {
        this.httpClient = HttpClient.newHttpClient();
        this.gson = new Gson();
    }

    public CompletableFuture<String> generateCareProtocol(List<BillItem> medicines) {
        String apiKey = SettingsController.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("API Key not configured in Settings."));
        }

        String prompt = buildPrompt(medicines);
        String requestBody = buildJsonRequest(prompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format(API_URL_TEMPLATE, apiKey)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("API Error: " + response.statusCode() + " - " + response.body());
                    }
                    return extractTextFromResponse(response.body());
                });
    }

    private String buildPrompt(List<BillItem> medicines) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a helpful 'Patient Care Assistant' for a pharmacy. ");
        sb.append("Generate a detailed Care Protocol in strictly valid JSON format for the following medicines:\n");

        for (BillItem item : medicines) {
            sb.append("- ").append(item.getName()).append("\n");
        }

        sb.append("\nThe JSON must be an array of objects, where each object has these fields:\n");
        sb.append("{\n");
        sb.append("  \"medicineName\": \"string\",\n");
        sb.append("  \"mechanism\": \"string (Simple explanation)\",\n");
        sb.append("  \"usage\": \"string (When/How to take)\",\n");
        sb.append("  \"dietary\": \"string (What to avoid)\",\n");
        sb.append("  \"sideEffects\": \"string (Common ones)\",\n");
        sb.append("  \"safety\": \"string (Safety warnings)\",\n");
        sb.append("  \"combinationalEffects\": \"string (Drug-Drug interactions if multiple meds, else 'None')\",\n");
        sb.append("  \"stopProtocol\": \"string (When to stop)\",\n");
        sb.append("  \"substitutes\": \"string (Generic name if applicable)\"\n");
        sb.append("}\n");
        sb.append("Return ONLY the JSON. No markdown formatting.");

        return sb.toString();
    }

    private String buildJsonRequest(String prompt) {
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

    private String extractTextFromResponse(String jsonResponse) {
        try {
            JsonObject root = JsonParser.parseString(jsonResponse).getAsJsonObject();
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
                JsonArray parts = content.getAsJsonArray("parts");
                if (parts != null && !parts.isEmpty()) {
                    return parts.get(0).getAsJsonObject().get("text").getAsString();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Could not generate insights.";
    }

    public CompletableFuture<String> findGenericName(String brandName) {
        String apiKey = SettingsController.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalStateException("API Key not configured."));
        }

        String prompt = "Identify the generic chemical composition of the medicine '" + brandName + "'. " +
                "Return ONLY the generic name(s) in a single line. Do not add any explanation. " +
                "If the medicine is not found or invalid, return 'UNKNOWN'.";

        String requestBody = buildJsonRequest(prompt);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(String.format(API_URL_TEMPLATE, apiKey)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new RuntimeException("API Error: " + response.statusCode());
                    }
                    return extractTextFromResponse(response.body()).trim();
                });
    }
}
