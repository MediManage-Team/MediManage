package org.example.MediManage.controller;

import org.example.MediManage.model.AuditEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsControllerTest {

    @Test
    void normalizesWhatsAppStatusValues() {
        assertEquals("CONNECTED", SettingsController.normalizeWhatsAppStatus(" connected "));
        assertEquals("UNKNOWN", SettingsController.normalizeWhatsAppStatus(null));
    }

    @Test
    void autoRefreshesOnlyTransientWhatsAppStates() {
        assertTrue(SettingsController.isWhatsAppStatusTransient("AUTHENTICATING"));
        assertTrue(SettingsController.isWhatsAppStatusTransient("checking"));
        assertFalse(SettingsController.isWhatsAppStatusTransient("CONNECTED"));
        assertFalse(SettingsController.isWhatsAppStatusTransient("QR_REQUIRED"));
    }

    @Test
    void fetchesQrOnlyWhenPairingIsRequired() {
        assertTrue(SettingsController.shouldFetchWhatsAppQr("qr_required"));
        assertFalse(SettingsController.shouldFetchWhatsAppQr("DISCONNECTED"));
    }

    @Test
    void summarizesAuditJsonDetailsAndRedactsSecrets() {
        String summary = SettingsController.summarizeAuditDetails(
                "{\"smtp_host\":\"smtp.gmail.com\",\"api_key\":\"secret-value\",\"smtp_user\":\"ops@example.com\"}");

        assertTrue(summary.contains("Smtp Host: smtp.gmail.com"));
        assertTrue(summary.contains("Api Key: [hidden]"));
        assertTrue(summary.contains("Smtp User: ops@example.com"));
    }

    @Test
    void formatsAuditMetaFromActorEntityAndDetails() {
        AuditEvent event = new AuditEvent(
                7,
                "2026-03-20 20:00:00",
                1,
                "admin",
                "COMMUNICATION_SETTINGS_SAVED",
                "SETTINGS",
                9,
                "Saved communication settings",
                "{\"smtp_host\":\"smtp.gmail.com\"}");

        String meta = SettingsController.formatAuditEventMeta(event);

        assertTrue(meta.contains("By admin"));
        assertTrue(meta.contains("Settings #9"));
        assertTrue(meta.contains("Smtp Host: smtp.gmail.com"));
    }
}
