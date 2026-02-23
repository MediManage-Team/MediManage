package org.example.MediManage.service.ai;

import org.example.MediManage.model.BillItem;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class AIInputSafetyGuard {
    private static final int MAX_TEXT_LENGTH = 400;
    private static final int MAX_MEDICINE_LINES = 12;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(?:\\+?\\d[\\d\\s-]{8,}\\d)(?!\\d)");
    private static final Pattern POLICY_OR_ID_PATTERN = Pattern.compile("\\b[A-Z]{2,}[\\-_]?[A-Z0-9]{4,}\\b");
    private static final Pattern PERSON_LABEL_PATTERN = Pattern.compile(
            "(?i)\\b(patient|customer)\\s+[A-Z][a-z]+(?:\\s+[A-Z][a-z]+)?\\b");

    private AIInputSafetyGuard() {
    }

    public static String tokenizePersonName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "customer";
        }
        String normalized = rawName.trim().toLowerCase(Locale.US).replaceAll("\\s+", " ");
        String digest = sha256Hex(normalized);
        return "customer_" + digest.substring(0, Math.min(10, digest.length()));
    }

    public static String sanitizeFreeText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        String masked = rawText.trim();
        masked = POLICY_OR_ID_PATTERN.matcher(masked).replaceAll("[ID_TOKEN]");
        masked = EMAIL_PATTERN.matcher(masked).replaceAll("[EMAIL_TOKEN]");
        masked = PHONE_PATTERN.matcher(masked).replaceAll("[PHONE_TOKEN]");
        masked = PERSON_LABEL_PATTERN.matcher(masked).replaceAll("$1 [PERSON_TOKEN]");
        masked = masked.replaceAll("\\s+", " ").trim();
        if (masked.length() > MAX_TEXT_LENGTH) {
            return masked.substring(0, MAX_TEXT_LENGTH) + "...";
        }
        return masked;
    }

    public static String sanitizeClinicalConditions(String rawConditions) {
        String sanitized = sanitizeFreeText(rawConditions);
        if (sanitized.isBlank()) {
            return "";
        }
        sanitized = sanitized.replaceAll("[^A-Za-z0-9,./()\\-+\\s]", " ");
        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        return sanitized;
    }

    public static List<String> approvedMedicinePromptLines(List<BillItem> billItems) {
        List<String> lines = new ArrayList<>();
        if (billItems == null || billItems.isEmpty()) {
            lines.add("- No medicines in current bill.");
            return lines;
        }

        int added = 0;
        for (BillItem item : billItems) {
            if (item == null) {
                continue;
            }
            String medicine = sanitizeMedicineName(item.getName());
            if (medicine.isBlank()) {
                continue;
            }
            StringBuilder line = new StringBuilder();
            line.append("- ").append(medicine).append(" (qty: ").append(Math.max(0, item.getQty()));
            String expiry = sanitizeExpiry(item.getExpiry());
            if (!expiry.isBlank()) {
                line.append(", expiry: ").append(expiry);
            }
            line.append(")");
            lines.add(line.toString());
            added++;
            if (added >= MAX_MEDICINE_LINES) {
                break;
            }
        }

        if (lines.isEmpty()) {
            lines.add("- No medicines in current bill.");
        }
        return lines;
    }

    private static String sanitizeMedicineName(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "";
        }
        String cleaned = rawName.trim().replaceAll("[^A-Za-z0-9\\-+()./\\s]", " ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        if (cleaned.length() > 64) {
            return cleaned.substring(0, 64);
        }
        return cleaned;
    }

    private static String sanitizeExpiry(String rawExpiry) {
        if (rawExpiry == null || rawExpiry.isBlank()) {
            return "";
        }
        String cleaned = rawExpiry.trim().replaceAll("[^0-9\\-/:]", "");
        if (cleaned.length() > 16) {
            return cleaned.substring(0, 16);
        }
        return cleaned;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                hex.append(String.format(Locale.US, "%02x", value));
            }
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
