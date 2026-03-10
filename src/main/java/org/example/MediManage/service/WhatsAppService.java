package org.example.MediManage.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class WhatsAppService {

    /**
     * Sends a WhatsApp message via local Node.js bridge (whatsapp-web.js) with the invoice summary, care protocol and PDF attachment.
     */
    public static CompletableFuture<Boolean> sendInvoiceWhatsApp(String toPhone, String customerName, double totalAmount, String careProtocol, int billId, String pdfPath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Clean up the recipient phone number 
                String cleanToPhone = toPhone.replaceAll("[^0-9+]", "");
                
                // Format the message nicely with Emojis
                StringBuilder bodyBuilder = new StringBuilder();
                bodyBuilder.append("🟢 *MediManage Pharmacy*\n");
                bodyBuilder.append("─────────────────────\n\n");
                bodyBuilder.append("Hello *").append(customerName).append("*,\n\n");
                bodyBuilder.append("Thank you for choosing us! Your invoice #*").append(billId).append("* for *₹")
                    .append(String.format("%.2f", totalAmount)).append("* is attached below as a PDF document.\n\n");

                if (careProtocol != null && !careProtocol.isBlank()) {
                    bodyBuilder.append("💡 *Note:* A personalized Patient Care Protocol for your medicines has been included at the end of the attached PDF. Please review it for dosage guidelines, interactions, and dietary advice.\n\n");
                }
                bodyBuilder.append("Stay healthy & take care! 🙏");

                // Resolve absolute path for PDF so Node.js (running in subfolder) can find it
                String absolutePdfPath = (pdfPath != null && !pdfPath.isBlank()) 
                    ? java.nio.file.Paths.get(pdfPath).toAbsolutePath().toString() 
                    : "";

                // Safely build the JSON payload using Gson
                JsonObject json = new JsonObject();
                json.addProperty("phone", cleanToPhone);
                json.addProperty("message", bodyBuilder.toString());
                json.addProperty("pdfPath", absolutePdfPath);

                String jsonPayload = new Gson().toJson(json);

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:3000/send"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

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

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:3000/send"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

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
