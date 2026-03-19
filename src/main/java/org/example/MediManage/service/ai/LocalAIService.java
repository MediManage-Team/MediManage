package org.example.MediManage.service.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONObject;
import org.json.JSONArray;
import org.example.MediManage.security.LocalAdminTokenManager;
import org.example.MediManage.util.AppExecutors;

/**
 * HTTP client for the Python AI Engine (localhost:5000).
 * Handles chat, model loading, downloading, and model management.
 */
public class LocalAIService implements AIService {
    private static final Logger LOGGER = Logger.getLogger(LocalAIService.class.getName());
    private static final String BASE_URL = "http://127.0.0.1:5000";
    private static final String API_URL = BASE_URL + "/chat";
    private static final String ORCHESTRATE_URL = BASE_URL + "/orchestrate";
    private static final String HEALTH_URL = BASE_URL + "/health";
    private static final Path MODELS_DIR = Path.of(System.getProperty("user.home"), "MediManage", "models");
    private final HttpClient client;

    public record ModelLoadResult(boolean success, String message, String provider, String modelPath) {
    }

    public LocalAIService() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        clearMissingSavedModelPreference();
        loadModel(); // Trigger load on startup
    }

    /** Constructor without auto-loading (for use as utility client). */
    public LocalAIService(boolean autoLoad) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        clearMissingSavedModelPreference();
        if (autoLoad) {
            loadModel();
        }
    }

    // ======================== MODEL LOADING ========================

    public void loadModel() {
        AppExecutors.runBackground(this::loadConfiguredModelBlocking);
    }

    public ModelLoadResult loadConfiguredModelBlocking() {
        try {
            Preferences prefs = Preferences.userNodeForPackage(org.example.MediManage.MediManageApplication.class);
            String modelPath = prefs.get("local_model_path", "");
            String hardware = prefs.get("ai_hardware", "Auto");
            String activeEnv = prefs.get("active_python_env", "cpu");

            if ("base".equalsIgnoreCase(activeEnv) || hardware.toLowerCase().contains("base")) {
                return new ModelLoadResult(false, "Cloud-only mode is active.", "", "");
            }

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

            modelPath = sanitizeSavedModelPreference(prefs, modelPath);
            if (modelPath.isEmpty()) {
                modelPath = chooseBestInstalledModelPath();
            }

            if (modelPath.isEmpty()) {
                return new ModelLoadResult(false, "No local model configured or installed.", "", "");
            }

            ModelLoadResult result = loadModelBlocking(modelPath, config);
            if (result.success()) {
                prefs.put("local_model_path", result.modelPath());
            }
            return result;
        } catch (Exception e) {
            return new ModelLoadResult(false, e.getMessage(), "", "");
        }
    }

    /** Load a specific model by path and hardware config. */
    public void loadModel(String modelPath, String hardwareConfig) {
        AppExecutors.runBackground(() -> loadModelBlocking(modelPath, hardwareConfig));
    }

    public ModelLoadResult loadModelBlocking(String modelPath, String hardwareConfig) {
        try {
            JSONObject json = new JSONObject();
            json.put("model_path", modelPath);
            json.put("hardware_config", hardwareConfig);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/load_model"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()));
            LocalAdminTokenManager.applyHeader(requestBuilder);
            HttpRequest request = requestBuilder.build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject body = response.body() == null || response.body().isBlank()
                    ? new JSONObject()
                    : new JSONObject(response.body());
            if (response.statusCode() == 200) {
                String resolvedPath = body.optString("model_path", modelPath);
                return new ModelLoadResult(
                        true,
                        body.optString("message", "Model loaded successfully."),
                        body.optString("provider", ""),
                        resolvedPath);
            }
            return new ModelLoadResult(false, body.optString("message", response.body()), "", modelPath);
        } catch (Exception e) {
            return new ModelLoadResult(false, e.getMessage(), "", modelPath);
        }
    }

    // ======================== ENGINE CONFIGURATION ========================

    public void updateConfig(String hfToken) {
        try {
            JSONObject json = new JSONObject();
            json.put("hf_token", hfToken);

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/update_config"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()));
            LocalAdminTokenManager.applyHeader(requestBuilder);
            HttpRequest request = requestBuilder.build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            LOGGER.info("AI Engine config updated.");
                        } else {
                            LOGGER.warning("Failed to update AI Engine config: " + response.body());
                        }
                    });
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to update AI Engine config.", e);
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
                .timeout(Duration.ofSeconds(120))
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
     * Unified Orchestration Endpoint. Handles both local and cloud AI generation via Python.
     */
    public CompletableFuture<String> orchestrate(String action, JSONObject data, JSONObject cloudConfig, String routing, boolean useSearch) {
        JSONObject json = new JSONObject();
        json.put("action", action);
        if (data != null) json.put("data", data);
        if (cloudConfig != null) json.put("cloud_config", cloudConfig);
        if (routing != null) json.put("routing", routing);
        json.put("use_search", useSearch);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(ORCHESTRATE_URL))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()));
        LocalAdminTokenManager.applyHeader(requestBuilder);

        HttpRequest request = requestBuilder.build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return new JSONObject(response.body()).getString("response");
                    } else {
                        String errMsg = "AI Orchestration Error: " + response.statusCode();
                        try {
                            errMsg += " - " + new JSONObject(response.body()).optString("error", "");
                        } catch (Exception ignored) {}
                        throw new RuntimeException(errMsg);
                    }
                });
    }

    /**
     * Instant database query — no AI model needed.
     * Returns pharmacy data directly from SQLite in sub-second time.
     */
    public CompletableFuture<String> queryDatabase(String query) {
        JSONObject json = new JSONObject();
        json.put("query", query);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/query_db"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        return new JSONObject(response.body()).getString("response");
                    } else {
                        throw new RuntimeException("DB Query Error: " + response.statusCode());
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

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/download_model"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()));
            LocalAdminTokenManager.applyHeader(requestBuilder);
            HttpRequest request = requestBuilder.build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        LOGGER.info("Download started: " + response.body());
                    });
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to start model download.", e);
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
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/stop_download"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody());
            LocalAdminTokenManager.applyHeader(requestBuilder);
            HttpRequest request = requestBuilder.build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        LOGGER.info("Download stop requested: " + response.body());
                    });
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to stop model download.", e);
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
                return dedupeModels(new JSONObject(response.body()).getJSONArray("models"));
            }
        } catch (Exception e) {
            // Fall back to direct filesystem scan when AI engine is not running
        }
        return scanModelsFromFilesystem();
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
            LOGGER.warning("Failed to get model info: " + e.getMessage());
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

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/delete_model"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()));
            LocalAdminTokenManager.applyHeader(requestBuilder);
            HttpRequest request = requestBuilder.build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                clearSavedModelPreferenceIfMatching(modelPath);
                return true;
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to delete model: " + e.getMessage());
        }
        return false;
    }

    // ======================== HEALTH CHECK ========================

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
        return "Local AI (Python Engine)";
    }

    public void cancelGeneration() {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/cancel_chat"))
                    .POST(HttpRequest.BodyPublishers.noBody());
            LocalAdminTokenManager.applyHeader(requestBuilder);
            client.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            LOGGER.warning("Failed to send cancel signal to Local AI: " + e.getMessage());
        }
    }

    private String chooseBestInstalledModelPath() {
        JSONArray models = listModels();
        JSONObject best = null;
        int bestRank = Integer.MAX_VALUE;
        for (int i = 0; i < models.length(); i++) {
            JSONObject model = models.optJSONObject(i);
            if (model == null) {
                continue;
            }
            int rank = rankModel(model.optString("format", ""));
            if (best == null || rank < bestRank) {
                best = model;
                bestRank = rank;
            }
        }
        return best == null ? "" : best.optString("path", "");
    }

    public void clearSavedModelPreferenceIfMatching(String modelPath) {
        if (modelPath == null || modelPath.isBlank()) {
            return;
        }
        Preferences prefs = Preferences.userNodeForPackage(org.example.MediManage.MediManageApplication.class);
        String saved = prefs.get("local_model_path", "");
        if (saved.equals(modelPath)) {
            prefs.remove("local_model_path");
        }
    }

    private void clearMissingSavedModelPreference() {
        Preferences prefs = Preferences.userNodeForPackage(org.example.MediManage.MediManageApplication.class);
        sanitizeSavedModelPreference(prefs, prefs.get("local_model_path", ""));
    }

    private String sanitizeSavedModelPreference(Preferences prefs, String savedPath) {
        if (savedPath == null || savedPath.isBlank()) {
            return "";
        }
        Path path = Path.of(savedPath);
        if (Files.exists(path)) {
            return savedPath;
        }
        prefs.remove("local_model_path");
        return "";
    }

    private int rankModel(String format) {
        return switch (format) {
            case "ONNX GenAI" -> 0;
            case "GGUF" -> 1;
            case "ONNX" -> 2;
            default -> 99;
        };
    }

    private JSONArray dedupeModels(JSONArray models) {
        java.util.LinkedHashMap<String, JSONObject> deduped = new java.util.LinkedHashMap<>();
        for (int i = 0; i < models.length(); i++) {
            JSONObject model = models.optJSONObject(i);
            if (model == null) {
                continue;
            }
            String path = model.optString("path", "").trim();
            if (path.isEmpty()) {
                continue;
            }
            deduped.putIfAbsent(path.toLowerCase(java.util.Locale.ROOT), model);
        }

        List<JSONObject> sorted = new ArrayList<>(deduped.values());
        sorted.sort(Comparator.comparing(model -> model.optString("name", "").toLowerCase(java.util.Locale.ROOT)));

        JSONArray result = new JSONArray();
        sorted.forEach(result::put);
        return result;
    }

    private JSONArray scanModelsFromFilesystem() {
        JSONArray models = new JSONArray();
        if (!Files.isDirectory(MODELS_DIR)) {
            return models;
        }

        try (var stream = Files.list(MODELS_DIR)) {
            List<Path> entries = stream
                    .filter(Files::exists)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT)))
                    .toList();

            for (Path entry : entries) {
                JSONObject model = scanModelEntry(entry);
                if (model != null) {
                    models.put(model);
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to scan local models directory: " + e.getMessage());
        }

        return models;
    }

    private JSONObject scanModelEntry(Path path) {
        try {
            if (!Files.exists(path)) {
                return null;
            }

            JSONObject info = new JSONObject();
            info.put("name", path.getFileName().toString());
            info.put("path", path.toAbsolutePath().toString());
            info.put("format", "unknown");
            info.put("size_mb", 0);

            if (Files.isRegularFile(path)) {
                long sizeBytes = Files.size(path);
                info.put("size_mb", roundSizeInMb(sizeBytes));
                String fileName = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                if (fileName.endsWith(".gguf")) {
                    info.put("format", "GGUF");
                } else if (fileName.endsWith(".onnx") || fileName.endsWith(".xml")) {
                    info.put("format", "ONNX");
                }
                return info;
            }

            List<String> fileNames = new ArrayList<>();
            final long[] sizeBytes = {0L};
            try (var walk = Files.walk(path)) {
                walk.filter(Files::isRegularFile).forEach(file -> {
                    try {
                        sizeBytes[0] += Files.size(file);
                        fileNames.add(file.getFileName().toString());
                    } catch (Exception ignored) {
                    }
                });
            }

            info.put("size_mb", roundSizeInMb(sizeBytes[0]));
            if (fileNames.contains("genai_config.json")) {
                info.put("format", "ONNX GenAI");
            } else if (fileNames.stream().anyMatch(name -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".gguf"))) {
                info.put("format", "GGUF");
            } else if (fileNames.stream().anyMatch(name -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".xml")
                    || name.toLowerCase(java.util.Locale.ROOT).endsWith(".onnx"))) {
                info.put("format", "ONNX");
            }

            return info;
        } catch (Exception e) {
            LOGGER.warning("Failed to inspect model entry " + path + ": " + e.getMessage());
            return null;
        }
    }

    private double roundSizeInMb(long bytes) {
        return Math.round((bytes / 1024.0 / 1024.0) * 100.0) / 100.0;
    }
}
