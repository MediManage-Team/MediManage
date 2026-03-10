package org.example.MediManage.service;

import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;

import org.example.MediManage.security.SecureSecretStore;

import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;

public class WhatsAppService {

    private static final Preferences prefs = Preferences.userNodeForPackage(org.example.MediManage.MediManageApplication.class);

    /**
     * Sends a WhatsApp message via Twilio with the invoice summary and care protocol.
     */
    public static CompletableFuture<Boolean> sendInvoiceWhatsApp(String toPhone, String customerName, double totalAmount, String careProtocol, int billId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String sid = prefs.get("twilio_sid", "");
                String token = SecureSecretStore.get("twilio_token");
                String senderNo = prefs.get("twilio_sender", "");

                if (sid.isBlank() || token.isBlank() || senderNo.isBlank()) {
                    throw new IllegalArgumentException("Twilio WhatsApp credentials not configured in Settings.");
                }

                // Make sure recipient and sender have "whatsapp:" prefix
                String formattedTo = toPhone.startsWith("whatsapp:") ? toPhone : "whatsapp:" + toPhone;
                String formattedFrom = senderNo.startsWith("whatsapp:") ? senderNo : "whatsapp:" + senderNo;

                Twilio.init(sid, token);

                StringBuilder body = new StringBuilder();
                body.append("🏥 *MediManage Pharmacy*\n\n");
                body.append("Dear ").append(customerName).append(",\n");
                body.append("Thank you for your purchase. Invoice #").append(billId).append(" total: *₹")
                    .append(String.format("%.2f", totalAmount)).append("*.\n\n");

                if (careProtocol != null && !careProtocol.isBlank()) {
                    body.append("💊 *Patient Care Protocol*\n");
                    // Optionally trim very long protocols or leave as is. Twilio limits are quite large (1600 chars).
                    body.append(careProtocol).append("\n\n");
                }

                body.append("Take care and stay healthy!");

                Message message = Message.creator(
                        new PhoneNumber(formattedTo),
                        new PhoneNumber(formattedFrom),
                        body.toString()
                ).create();

                if (message.getErrorCode() != null) {
                    throw new RuntimeException("Twilio Error: " + message.getErrorMessage());
                }

                return true;
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("WhatsApp sending failed: " + e.getMessage(), e);
            }
        });
    }
}
