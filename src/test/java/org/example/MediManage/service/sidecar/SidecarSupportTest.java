package org.example.MediManage.service.sidecar;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SidecarSupportTest {
    private HttpServer server;
    private String metadataServiceName;
    private final String originalUserHome = System.getProperty("user.home");
    private java.nio.file.Path tempHome;

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.stop(0);
        }
        if (metadataServiceName != null) {
            SidecarOwnershipMetadata.delete(metadataServiceName);
        }
        System.setProperty("user.home", originalUserHome);
        if (tempHome != null) {
            try (var stream = java.nio.file.Files.walk(tempHome)) {
                stream.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try {
                        java.nio.file.Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            }
            tempHome = null;
        }
    }

    @Test
    void recognizesHealthyOwnedSidecarFromHealthProbe() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> respond(exchange,
                "{\"service\":\"medimanage-ai-engine\",\"healthy\":true,\"owner_verified\":true,\"status\":\"running\"}"));
        server.start();

        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/health");
        SidecarProbeResult probe = SidecarHttpProbe.probe(uri, "medimanage-ai-engine", builder -> {
        });
        SidecarStartupAdvisor.Decision decision = SidecarStartupAdvisor.decide(
                "AI Engine", probe, Optional.empty());

        assertTrue(probe.reachable());
        assertTrue(probe.recognized());
        assertTrue(probe.ownerVerified());
        assertTrue(probe.healthy());
        assertEquals(SidecarStartupAdvisor.Action.REUSE_EXISTING, decision.action());
    }

    @Test
    void rejectsUnrelatedProcessOnExpectedPort() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/status", exchange -> respond(exchange,
                "{\"service\":\"unrelated-service\",\"healthy\":true,\"status\":\"ok\"}"));
        server.start();

        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/status");
        SidecarProbeResult probe = SidecarHttpProbe.probe(uri, "medimanage-whatsapp-bridge", builder -> {
        });
        SidecarStartupAdvisor.Decision decision = SidecarStartupAdvisor.decide(
                "WhatsApp Bridge", probe, Optional.empty());

        assertTrue(probe.reachable());
        assertFalse(probe.recognized());
        assertEquals(SidecarStartupAdvisor.Action.REJECT_CONFLICT, decision.action());
    }

    @Test
    void clearsStaleOwnershipMetadataBeforeStartingNewProcess() throws Exception {
        tempHome = java.nio.file.Files.createTempDirectory("sidecar-metadata-home-");
        System.setProperty("user.home", tempHome.toString());
        metadataServiceName = "test-sidecar-" + System.nanoTime();
        SidecarOwnershipMetadata.write(metadataServiceName, 6553, 999999L);

        SidecarStartupAdvisor.Decision decision = SidecarStartupAdvisor.decide(
                "Test Sidecar",
                SidecarProbeResult.unreachable("Connection refused"),
                SidecarOwnershipMetadata.read(metadataServiceName));

        assertEquals(SidecarStartupAdvisor.Action.START_NEW, decision.action());
        assertTrue(decision.clearStaleMetadata());
    }

    private void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
