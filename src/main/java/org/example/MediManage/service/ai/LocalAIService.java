package org.example.MediManage.service.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.json.JSONObject;

public class LocalAIService implements AIService {
    private static final String API_URL = "http://127.0.0.1:5000/chat";
    private static final String HEALTH_URL = "http://127.0.0.1:5000/health";
    private final HttpClient client;

    public LocalAIService() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)) // Increased timeout for loading
                .build();
        loadModel(); // Trigger load on startup
    }

    public void loadModel() {
        try {
            java.util.prefs.Preferences prefs = java.util.prefs.Preferences
                    .userNodeForPackage(org.example.MediManage.SettingsController.class);
            String modelPath = prefs.get("local_model_path", "");
            String hardware = prefs.get("ai_hardware", "Auto");

            // Map UI string to Backend Config
            String config = "auto";
            if (hardware.contains("OpenVINO"))
                config = "openvino";
            else if (hardware.contains("DirectML") || hardware.contains("Ryzen"))
                config = "directml";
            else if (hardware.contains("CUDA"))
                config = "cuda";
            else if (hardware.contains("CPU"))
                config = "cpu";

            if (modelPath.isEmpty()) {
                System.out.println("No local model configured.");
                return;
            }

            JSONObject json = new JSONObject();
            json.put("model_path", modelPath);
            json.put("hardware_config", config);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:5000/load_model"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            System.out.println("Local AI Model Loaded: " + response.body());
                        } else {
                            System.err.println("Failed to load local model: " + response.body());
                        }
                    });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public CompletableFuture<String> chat(String prompt) {
        return chat(prompt, false);
    }

    public CompletableFuture<String> chat(String prompt, boolean useSearch) {
        JSONObject json = new JSONObject();
        json.put("prompt", prompt);
        json.put("use_search", useSearch);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return new JSONObject(response.body()).getString("response");
                    } else {
                        throw new RuntimeException("AI Engine Error: " + response.statusCode());
                    }
                });
    }

    public void startDownload(String repoId, String filename) {
        try {
            JSONObject json = new JSONObject();
            json.put("repo_id", repoId);
            if (filename != null && !filename.isEmpty()) {
                json.put("filename", filename);
            }
            // Use a specific models directory in project or user home
            String localDir = System.getProperty("user.home") + "/MediManage/models";
            json.put("local_dir", localDir);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:5000/download_model"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        System.out.println("Download started: " + response.body());
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public JSONObject getDownloadStatus() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:5000/download_status"))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return new JSONObject(response.body());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new JSONObject().put("status", "error");
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
        return "Local AI (Python Engine)";
    }
}
