package org.example.MediManage.service;


import org.example.MediManage.security.SecureSecretStore;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;
import java.io.File;

import org.example.MediManage.dao.MessageTemplateDAO;
import org.example.MediManage.dao.ReceiptSettingsDAO;
import org.example.MediManage.model.MessageTemplate;
import org.example.MediManage.model.ReceiptSettings;

public class EmailService {

    private static final Preferences prefs = Preferences.userNodeForPackage(org.example.MediManage.MediManageApplication.class);
    private static final MessageTemplateDAO templateDAO = new MessageTemplateDAO();
    private static final ReceiptSettingsDAO receiptDAO = new ReceiptSettingsDAO();

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

                // Fetch templates
                String pharmacyName = "MediManage Pharmacy";
                try {
                    ReceiptSettings rs = receiptDAO.getSettings();
                    if (rs.getPharmacyName() != null && !rs.getPharmacyName().isBlank()) {
                        pharmacyName = rs.getPharmacyName();
                    }
                } catch (Exception ignored) {}

                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(username, pharmacyName));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toAddress));

                String careNote = "";
                if (careProtocol != null && !careProtocol.isBlank()) {
                    careNote = "Patient Care Protocol note: a personalized care guide for these medicines is included at the end of the attached PDF.";
                }

                MessageTemplate subjectTpl = templateDAO.getByKey(MessageTemplateDAO.KEY_EMAIL_INVOICE_SUBJECT);
                String subjectStr = subjectTpl != null ? subjectTpl.getBodyTemplate() : templateDAO.getDefaultBody(MessageTemplateDAO.KEY_EMAIL_INVOICE_SUBJECT);
                String finalSubject = MessageTemplate.render(subjectStr, customerName, billId, totalAmount, pharmacyName, careNote);

                MessageTemplate bodyTpl = templateDAO.getByKey(MessageTemplateDAO.KEY_EMAIL_INVOICE_BODY);
                String bodyStr = bodyTpl != null ? bodyTpl.getBodyTemplate() : templateDAO.getDefaultBody(MessageTemplateDAO.KEY_EMAIL_INVOICE_BODY);
                String finalBody = MessageTemplate.render(bodyStr, customerName, billId, totalAmount, pharmacyName, careNote);

                message.setSubject(finalSubject);

                // Create a multipart message
                Multipart multipart = new MimeMultipart();

                // Body text part
                MimeBodyPart textPart = new MimeBodyPart();
                textPart.setText(finalBody, "UTF-8");
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

    /**
     * Sends a simple text email (no PDF) for generic notifications.
     */
    public static CompletableFuture<Boolean> sendNotificationEmail(String toAddress, String subject, String bodyText) {
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

                String pharmacyName = "MediManage Pharmacy";
                try {
                    ReceiptSettings rs = receiptDAO.getSettings();
                    if (rs.getPharmacyName() != null && !rs.getPharmacyName().isBlank()) {
                        pharmacyName = rs.getPharmacyName();
                    }
                } catch (Exception ignored) {}

                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(username, pharmacyName));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toAddress));
                message.setSubject(subject);
                message.setText(bodyText);

                Transport.send(message);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Email Notification failed: " + e.getMessage(), e);
            }
        });
    }
}
