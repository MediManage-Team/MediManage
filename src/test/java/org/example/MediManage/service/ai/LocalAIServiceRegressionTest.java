package org.example.MediManage.service.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalAIServiceRegressionTest {
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void chatReturnsResponseFieldFromPythonEngine() throws Exception {
        startServer();
        server.createContext("/chat", exchange -> respond(exchange, 200, "{\"response\":\"hello from python\"}"));
        server.start();

        LocalAIService service = new LocalAIService(false);

        assertEquals("hello from python", service.chat("hello").join());
    }

    @Test
    void orchestratePostsActionPayloadWithCloudConfigAndFlags() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>("");
        startServer();
        server.createContext("/orchestrate", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"response\":\"orchestrated\"}");
        });
        server.start();

        LocalAIService service = new LocalAIService(false);
        JSONObject data = new JSONObject().put("prompt", "Summarize inventory");
        JSONObject cloudConfig = new JSONObject().put("provider", "GEMINI").put("model", "gemini-2.5-flash");

        assertEquals("orchestrated",
                service.orchestrate("combined_analysis", data, cloudConfig, "cloud_only", true).join());

        JSONObject sent = new JSONObject(requestBody.get());
        assertEquals("combined_analysis", sent.getString("action"));
        assertTrue(sent.getBoolean("use_search"));
        assertEquals("cloud_only", sent.getString("routing"));
        assertEquals("Summarize inventory", sent.getJSONObject("data").getString("prompt"));
        assertEquals("GEMINI", sent.getJSONObject("cloud_config").getString("provider"));
    }

    @Test
    void healthEndpointDrivesAvailabilityAndHealthPayload() throws Exception {
        startServer();
        server.createContext("/health", exchange -> respond(exchange, 200, "{\"status\":\"ok\",\"engine\":\"python\"}"));
        server.start();

        LocalAIService service = new LocalAIService(false);

        assertTrue(service.isAvailable());
        JSONObject health = service.getHealth();
        assertEquals("ok", health.getString("status"));
        assertEquals("python", health.getString("engine"));
    }

    private void startServer() throws IOException {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 5000), 0);
        } catch (BindException ex) {
            throw new TestAbortedException("Port 5000 is already in use; skipping LocalAIService contract test.", ex);
        }
    }

    private void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
