package org.example.MediManage.service.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.json.JSONObject;
import org.json.JSONArray;

/**
 * HTTP client for the Python AI Engine (localhost:5000).
 * Handles chat, model loading, downloading, and model management.
 */
public class LocalAIService implements AIService {
    private static final String BASE_URL = "http://127.0.0.1:5000";
    private static final String API_URL = BASE_URL + "/chat";
    private static final String RAG_URL = BASE_URL + "/chat/rag";
    private static final String HEALTH_URL = BASE_URL + "/health";
    private final HttpClient client;

    public LocalAIService() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        loadModel(); // Trigger load on startup
    }

    /** Constructor without auto-loading (for use as utility client). */
    public LocalAIService(boolean autoLoad) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        if (autoLoad) {
            loadModel();
        }
    }

    // ======================== MODEL LOADING ========================

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
                    .uri(URI.create(BASE_URL + "/load_model"))
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

    /** Load a specific model by path and hardware config. */
    public void loadModel(String modelPath, String hardwareConfig) {
        try {
            JSONObject json = new JSONObject();
            json.put("model_path", modelPath);
            json.put("hardware_config", hardwareConfig);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/load_model"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            System.out.println("Model loaded: " + response.body());
                        } else {
                            System.err.println("Failed to load model: " + response.body());
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ======================== CHAT ========================

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

    /**
     * Chat with business context (RAG). Java injects DB data as context
     * so the local model can reason over real inventory/sales/expiry data.
     */
    public CompletableFuture<String> chatWithContext(String prompt, String businessContext) {
        JSONObject json = new JSONObject();
        json.put("prompt", prompt);
        json.put("context", businessContext);
        json.put("use_search", false);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(RAG_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return new JSONObject(response.body()).getString("response");
                    } else {
                        throw new RuntimeException("AI Engine RAG Error: " + response.statusCode());
                    }
                });
    }

    // ======================== DOWNLOAD MANAGEMENT ========================

    public void startDownload(String repoId, String filename, String source) {
        try {
            JSONObject json = new JSONObject();
            json.put("repo_id", repoId);
            if (filename != null && !filename.isEmpty()) {
                json.put("filename", filename);
            }
            if (source != null && !source.isEmpty()) {
                json.put("source", source);
            }
            // Use a specific models directory in user home
            String localDir = System.getProperty("user.home") + "/MediManage/models";
            json.put("local_dir", localDir);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/download_model"))
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
                    .uri(URI.create(BASE_URL + "/download_status"))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return new JSONObject(response.body());
            }
        } catch (Exception e) {
            // Server might not be running
        }
        return new JSONObject().put("status", "error").put("message", "AI Engine not reachable");
    }

    /**
     * Cancel an ongoing download.
     */
    public void stopDownload() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/stop_download"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        System.out.println("Download stop requested: " + response.body());
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ======================== MODEL MANAGEMENT (ComfyUI-Style)
    // ========================

    /**
     * List all downloaded models from the models directory.
     * Returns a JSONArray of model objects with name, path, format, size_mb.
     */
    public JSONArray listModels() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/list_models"))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return new JSONObject(response.body()).getJSONArray("models");
            }
        } catch (Exception e) {
            System.err.println("Failed to list models: " + e.getMessage());
        }
        return new JSONArray();
    }

    /**
     * Get detailed info about a specific model.
     */
    public JSONObject getModelInfo(String modelPath) {
        try {
            JSONObject json = new JSONObject();
            json.put("model_path", modelPath);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/model_info"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return new JSONObject(response.body());
            }
        } catch (Exception e) {
            System.err.println("Failed to get model info: " + e.getMessage());
        }
        return new JSONObject();
    }

    /**
     * Delete a model from the local filesystem.
     */
    public boolean deleteModel(String modelPath) {
        try {
            JSONObject json = new JSONObject();
            json.put("model_path", modelPath);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/delete_model"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            System.err.println("Failed to delete model: " + e.getMessage());
        }
        return false;
    }

    // ======================== HEALTH CHECK ========================

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
