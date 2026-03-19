package org.example.MediManage.service.sidecar;

import java.util.Optional;

public final class SidecarStartupAdvisor {
    private SidecarStartupAdvisor() {
    }

    public enum Action {
        START_NEW,
        REUSE_EXISTING,
        REJECT_CONFLICT
    }

    public record Decision(Action action, boolean clearStaleMetadata, String message) {
    }

    public static Decision decide(
            String serviceDisplayName,
            SidecarProbeResult probe,
            Optional<SidecarOwnershipMetadata.Entry> metadata) {
        if (probe.reachable()) {
            if (probe.recognized() && probe.ownerVerified() && probe.healthy()) {
                return new Decision(Action.REUSE_EXISTING, false,
                        serviceDisplayName + " is already running and ownership was verified.");
            }
            return new Decision(Action.REJECT_CONFLICT, false,
                    serviceDisplayName + " port is occupied by an untrusted or unhealthy process.");
        }

        boolean clearMetadata = metadata.isPresent();
        if (clearMetadata) {
            SidecarOwnershipMetadata.Entry entry = metadata.get();
            String detail = entry.isProcessAlive()
                    ? "Clearing stale ownership metadata because the recorded process no longer exposes the service."
                    : "Clearing stale ownership metadata from a dead process.";
            return new Decision(Action.START_NEW, true, detail);
        }

        return new Decision(Action.START_NEW, false, serviceDisplayName + " is not running; starting a new process.");
    }
}
