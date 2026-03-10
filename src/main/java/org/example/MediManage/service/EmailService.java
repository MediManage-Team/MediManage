package org.example.MediManage.service;


import org.example.MediManage.security.SecureSecretStore;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;
import java.io.File;

public class EmailService {

    private static final Preferences prefs = Preferences.userNodeForPackage(org.example.MediManage.MediManageApplication.class);

    /**
     * Sends an email with the invoice attached and care protocol in the body.
     */
    public static CompletableFuture<Boolean> sendInvoiceEmail(String toAddress, String customerName, String careProtocol, String pdfAttachmentPath, int billId, double totalAmount) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String host = prefs.get("smtp_host", "smtp.gmail.com");
                String port = prefs.get("smtp_port", "587");
                String username = prefs.get("smtp_user", "");
                String password = SecureSecretStore.get("smtp_pass");

                if (username.isBlank() || password.isBlank()) {
                    throw new IllegalArgumentException("SMTP Credentials not configured in Settings.");
                }

                Properties props = new Properties();
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.host", host);
                props.put("mail.smtp.port", port);

                Session session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                });

                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(username, "MediManage Pharmacy"));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toAddress));
                message.setSubject("Your Invoice #" + billId + " & Care Protocol - MediManage");

                // Create a multipart message
                Multipart multipart = new MimeMultipart();

                // Body text part
                MimeBodyPart textPart = new MimeBodyPart();
                StringBuilder sb = new StringBuilder();
                sb.append("Dear ").append(customerName).append(",\n\n");
                sb.append("Thank you for your purchase. Your total was ₹").append(String.format("%.2f", totalAmount)).append(".\n\n");
                sb.append("Please find your invoice attached to this email.\n\n");
                
                if (careProtocol != null && !careProtocol.isBlank()) {
                    sb.append("--- Patient Care Protocol ---\n");
                    sb.append(careProtocol).append("\n\n");
                }
                
                sb.append("Stay healthy!\n- MediManage Pharmacy System");
                textPart.setText(sb.toString());
                multipart.addBodyPart(textPart);

                // Attachment part
                if (pdfAttachmentPath != null && !pdfAttachmentPath.isBlank()) {
                    File pdfFile = new File(pdfAttachmentPath);
                    if (pdfFile.exists()) {
                        MimeBodyPart attachmentPart = new MimeBodyPart();
                        attachmentPart.attachFile(pdfFile);
                        multipart.addBodyPart(attachmentPart);
                    }
                }

                message.setContent(multipart);
                Transport.send(message);

                return true;
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Email sending failed: " + e.getMessage(), e);
            }
        });
    }
}
