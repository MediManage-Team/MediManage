package org.example.MediManage.service.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.example.MediManage.MediManageApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalAIServiceRegressionTest {
    private final Preferences prefs = Preferences.userNodeForPackage(MediManageApplication.class);
    private HttpServer server;
    private Path tempModelPath;

    @AfterEach
    void tearDown() throws Exception {
        prefs.remove("local_model_path");
        if (server != null) {
            server.stop(0);
        }
        if (tempModelPath != null) {
            java.nio.file.Files.deleteIfExists(tempModelPath);
            tempModelPath = null;
        }
    }

    @Test
    void loadModelBlockingReturnsSuccessfulContract() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 5000), 0);
        server.createContext("/load_model", exchange -> respond(exchange, 200,
                "{\"status\":\"success\",\"provider\":\"stub-provider\",\"model_path\":\"C:/models/current.gguf\",\"model_name\":\"current.gguf\"}"));
        server.start();

        LocalAIService service = new LocalAIService(false);
        LocalAIService.ModelLoadResult result = service.loadModelBlocking("C:/models/requested.gguf", "cpu");

        assertTrue(result.success());
        assertEquals("stub-provider", result.provider());
        assertEquals("C:/models/current.gguf", result.modelPath());
    }

    @Test
    void failedLoadDoesNotOverwriteSavedModelPreference() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 5000), 0);
        server.createContext("/load_model", exchange -> respond(exchange, 500,
                "{\"status\":\"error\",\"message\":\"Load failed\"}"));
        server.start();

        tempModelPath = java.nio.file.Files.createTempFile("previous-model-", ".gguf");
        prefs.put("local_model_path", tempModelPath.toString());
        LocalAIService service = new LocalAIService(false);
        LocalAIService.ModelLoadResult result = service.loadModelBlocking("C:/models/new.gguf", "cpu");

        assertFalse(result.success());
        assertEquals(tempModelPath.toString(), prefs.get("local_model_path", ""));
    }

    @Test
    void constructorClearsMissingSavedModelPreference() throws Exception {
        Path missingPath = Path.of(System.getProperty("java.io.tmpdir"), "missing-model-" + System.nanoTime() + ".gguf");
        prefs.put("local_model_path", missingPath.toString());

        new LocalAIService(false);

        assertEquals("", prefs.get("local_model_path", ""));
    }

    @Test
    void deletingActiveModelClearsSavedPreference() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 5000), 0);
        server.createContext("/delete_model", exchange -> respond(exchange, 200,
                "{\"status\":\"deleted\",\"path\":\"C:/models/current.gguf\",\"was_loaded\":true}"));
        server.start();

        prefs.put("local_model_path", "C:/models/current.gguf");
        LocalAIService service = new LocalAIService(false);

        assertTrue(service.deleteModel("C:/models/current.gguf"));
        assertEquals("", prefs.get("local_model_path", ""));
    }

    private void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
