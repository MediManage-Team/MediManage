package org.example.MediManage.service.sidecar;

import org.json.JSONObject;

public record SidecarProbeResult(
        boolean reachable,
        boolean recognized,
        boolean ownerVerified,
        boolean healthy,
        int statusCode,
        JSONObject payload,
        String summary) {

    public static SidecarProbeResult unreachable(String summary) {
        return new SidecarProbeResult(false, false, false, false, -1, new JSONObject(), summary);
    }
}
