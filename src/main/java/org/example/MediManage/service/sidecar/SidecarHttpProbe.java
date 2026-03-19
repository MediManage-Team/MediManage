package org.example.MediManage.service.sidecar;

import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

public final class SidecarHttpProbe {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private SidecarHttpProbe() {
    }

    public static SidecarProbeResult probe(URI uri, String expectedService,
                                           Consumer<HttpRequest.Builder> requestCustomizer) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(2))
                    .GET();
            if (requestCustomizer != null) {
                requestCustomizer.accept(builder);
            }

            HttpResponse<String> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            JSONObject payload;
            try {
                payload = new JSONObject(response.body());
            } catch (Exception parseException) {
                return new SidecarProbeResult(
                        true,
                        false,
                        false,
                        false,
                        response.statusCode(),
                        new JSONObject(),
                        "Unexpected response from occupied port.");
            }
            boolean recognized = expectedService.equalsIgnoreCase(payload.optString("service", ""));
            boolean ownerVerified = payload.optBoolean("owner_verified", false);
            boolean healthy = payload.optBoolean("healthy", recognized && response.statusCode() == 200);
            String summary = payload.optString("message",
                    payload.optString("status", "HTTP " + response.statusCode()));
            return new SidecarProbeResult(true, recognized, ownerVerified, healthy, response.statusCode(), payload,
                    summary);
        } catch (Exception e) {
            return SidecarProbeResult.unreachable(e.getMessage());
        }
    }
}
