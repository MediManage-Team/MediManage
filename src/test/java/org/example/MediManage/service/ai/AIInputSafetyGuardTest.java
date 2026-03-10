package org.example.MediManage.service.ai;

import org.example.MediManage.model.BillItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AIInputSafetyGuardTest {

    @Test
    void tokenizePersonNameIsDeterministicAndNonRaw() {
        String tokenOne = AIInputSafetyGuard.tokenizePersonName("Alice Kumar");
        String tokenTwo = AIInputSafetyGuard.tokenizePersonName("Alice Kumar");
        String tokenThree = AIInputSafetyGuard.tokenizePersonName("Bob Kumar");

        assertEquals(tokenOne, tokenTwo);
        assertTrue(tokenOne.startsWith("customer_"));
        assertFalse(tokenOne.contains("Alice"));
        assertNotEquals(tokenOne, tokenThree);
    }

    @Test
    void sanitizeFreeTextMasksEmailPhoneAndPolicyLikeTokens() {
        String raw = "Reach me at alice@example.com or +91-9876543210. Policy ABH-123456.";
        String sanitized = AIInputSafetyGuard.sanitizeFreeText(raw);

        assertFalse(sanitized.contains("alice@example.com"));
        assertFalse(sanitized.contains("9876543210"));
        assertFalse(sanitized.contains("ABH-123456"));
        assertTrue(sanitized.contains("[EMAIL_TOKEN]"));
        assertTrue(sanitized.contains("[PHONE_TOKEN]"));
        assertTrue(sanitized.contains("[ID_TOKEN]"));
    }

    @Test
    void approvedMedicinePromptLinesContainOnlyAllowedFields() {
        List<String> lines = AIInputSafetyGuard.approvedMedicinePromptLines(List.of(
                new BillItem(11, "Paracetamol 500mg", "2031-10-31", 2, 18.5, 0.0)));

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("Paracetamol 500mg"));
        assertTrue(lines.get(0).contains("qty: 2"));
        assertTrue(lines.get(0).contains("expiry: 2031-10-31"));
        assertFalse(lines.get(0).contains("18.5"));
    }

}
