package org.example.MediManage.service.sidecar;

import org.json.JSONObject;
import org.example.MediManage.util.AppPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SidecarOwnershipMetadata {
    private static final Logger LOGGER = Logger.getLogger(SidecarOwnershipMetadata.class.getName());

    private SidecarOwnershipMetadata() {
    }

    public record Entry(String serviceName, int port, long pid, Instant startedAt) {
        boolean isProcessAlive() {
            return pid > 0 && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
        }
    }

    public static Optional<Entry> read(String serviceName) {
        Path path = metadataPath(serviceName);
        if (!Files.exists(path)) {
            return Optional.empty();
        }

        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(raw);
            return Optional.of(new Entry(
                    json.getString("service"),
                    json.getInt("port"),
                    json.getLong("pid"),
                    Instant.parse(json.getString("started_at"))));
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to read sidecar metadata for " + serviceName, e);
            return Optional.empty();
        }
    }

    public static void write(String serviceName, int port, long pid) {
        Path path = metadataPath(serviceName);
        try {
            Files.createDirectories(path.getParent());
            JSONObject json = new JSONObject()
                    .put("service", serviceName)
                    .put("port", port)
                    .put("pid", pid)
                    .put("started_at", Instant.now().toString());
            Files.writeString(path, json.toString(2), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to write sidecar metadata for " + serviceName, e);
        }
    }

    public static void delete(String serviceName) {
        try {
            Files.deleteIfExists(metadataPath(serviceName));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to delete sidecar metadata for " + serviceName, e);
        }
    }

    private static Path metadataPath(String serviceName) {
        return AppPaths.appDataPath("runtime", "sidecars", serviceName + ".json");
    }
}
