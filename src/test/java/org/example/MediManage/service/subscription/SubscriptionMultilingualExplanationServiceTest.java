package org.example.MediManage.service.subscription;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionMultilingualExplanationServiceTest {

    private final SubscriptionMultilingualExplanationService service = new SubscriptionMultilingualExplanationService();

    @Test
    void localizeReturnsEnglishSnippetWhenLanguageIsEnglish() {
        SubscriptionMultilingualExplanationService.LocalizedExplanation localized = service.localize(
                "en",
                true,
                SubscriptionEligibilityCode.ELIGIBLE,
                "Applied 10% subscription discount.",
                List.of("Discount affected 1 of 2 lines."),
                null).join();

        assertEquals("en", localized.languageCode());
        assertEquals("English", localized.languageName());
        assertFalse(localized.aiTranslated());
        assertTrue(localized.snippet().contains("Applied 10% subscription discount."));
    }

    @Test
    void localizeFallsBackToLocalizedTemplateWhenAiUnavailable() {
        SubscriptionMultilingualExplanationService.LocalizedExplanation localized = service.localize(
                "hi",
                false,
                SubscriptionEligibilityCode.NO_ENROLLMENT,
                "Subscription discount was not applied.",
                List.of("Reason: No active subscription enrollment found for this customer."),
                null).join();

        assertEquals("hi", localized.languageCode());
        assertEquals("Hindi", localized.languageName());
        assertFalse(localized.aiTranslated());
        assertTrue(localized.snippet().contains("इस बिल पर सदस्यता बचत लागू नहीं हुई"));
        assertTrue(localized.snippet().contains("Reason"));
    }

    @Test
    void localizeMasksPiiBeforeSendingOrReturningSnippet() {
        SubscriptionMultilingualExplanationService.LocalizedExplanation localized = service.localize(
                "en",
                true,
                SubscriptionEligibilityCode.ELIGIBLE,
                "Applied for patient Alice. Contact: +91-9876543210, alice@example.com",
                List.of("Policy ABH-123456 validated."),
                null).join();

        assertFalse(localized.snippet().contains("Alice"));
        assertFalse(localized.snippet().contains("9876543210"));
        assertFalse(localized.snippet().contains("alice@example.com"));
        assertTrue(localized.snippet().contains("[PHONE_TOKEN]"));
        assertTrue(localized.snippet().contains("[EMAIL_TOKEN]"));
        assertTrue(localized.snippet().contains("[ID_TOKEN]"));
    }
}
