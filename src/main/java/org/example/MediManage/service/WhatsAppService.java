package org.example.MediManage.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.example.MediManage.dao.MessageTemplateDAO;
import org.example.MediManage.dao.ReceiptSettingsDAO;
import org.example.MediManage.model.MessageTemplate;
import org.example.MediManage.model.ReceiptSettings;
import org.example.MediManage.config.WhatsAppBridgeConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class WhatsAppService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final MessageTemplateDAO templateDAO = new MessageTemplateDAO();
    private static final ReceiptSettingsDAO receiptDAO = new ReceiptSettingsDAO();

    /**
     * Sends a WhatsApp message via local Node.js bridge (whatsapp-web.js) with the invoice summary, care protocol and PDF attachment.
     * Uses customizable message template from the database.
     */
    public static CompletableFuture<Boolean> sendInvoiceWhatsApp(String toPhone, String customerName, double totalAmount, String careProtocol, int billId, String pdfPath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String cleanToPhone = toPhone.replaceAll("[^0-9+]", "");

                // Load customizable template
                String pharmacyName = "MediManage Pharmacy";
                try { 
                    ReceiptSettings rs = receiptDAO.getSettings();
                    if (rs.getPharmacyName() != null && !rs.getPharmacyName().isBlank()) {
                        pharmacyName = rs.getPharmacyName();
                    }
                } catch (Exception ignored) {}

                String careNote = "";
                if (careProtocol != null && !careProtocol.isBlank()) {
                    careNote = "\uD83D\uDCA1 *Note:* A personalized Patient Care Protocol for your medicines has been included at the end of the attached PDF. Please review it for dosage guidelines, interactions, and dietary advice.";
                }

                MessageTemplate template = templateDAO.getByKey(MessageTemplateDAO.KEY_WHATSAPP_INVOICE);
                String body;
                if (template != null) {
                    body = MessageTemplate.render(template.getBodyTemplate(), customerName, billId, totalAmount, pharmacyName, careNote);
                } else {
                    body = MessageTemplate.render(templateDAO.getDefaultBody(MessageTemplateDAO.KEY_WHATSAPP_INVOICE), customerName, billId, totalAmount, pharmacyName, careNote);
                }

                // Resolve absolute path for PDF
                String absolutePdfPath = (pdfPath != null && !pdfPath.isBlank())
                    ? java.nio.file.Paths.get(pdfPath).toAbsolutePath().toString()
                    : "";

                JsonObject json = new JsonObject();
                json.addProperty("phone", cleanToPhone);
                json.addProperty("message", body);
                json.addProperty("pdfPath", absolutePdfPath);

                String jsonPayload = new Gson().toJson(json);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(WhatsAppBridgeConfig.sendUrl()))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new RuntimeException("Local WhatsApp Bridge Error: " + response.body());
                }

                return true;
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("WhatsApp sending failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Sends a simple text WhatsApp message (no PDF) for generic notifications.
     */
    public static CompletableFuture<Boolean> sendNotificationWhatsApp(String toPhone, String message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String cleanToPhone = toPhone.replaceAll("[^0-9+]", "");

                JsonObject json = new JsonObject();
                json.addProperty("phone", cleanToPhone);
                json.addProperty("message", message);

                String jsonPayload = new Gson().toJson(json);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(WhatsAppBridgeConfig.sendUrl()))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new RuntimeException("Local WhatsApp Bridge Error: " + response.body());
                }

                return true;
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("WhatsApp Text sending failed: " + e.getMessage(), e);
            }
        });
    }
}
