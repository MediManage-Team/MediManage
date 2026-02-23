package org.example.MediManage.service.subscription;

import org.example.MediManage.service.ai.AIOrchestrator;
import org.example.MediManage.service.ai.AIInputSafetyGuard;
import org.example.MediManage.service.ai.AIPromptCatalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class SubscriptionMultilingualExplanationService {

    private static final String ENGLISH_CODE = "en";
    private static final Map<String, String> LANGUAGE_NAME_BY_CODE = Map.of(
            "en", "English",
            "hi", "Hindi",
            "bn", "Bengali",
            "ta", "Tamil",
            "te", "Telugu",
            "mr", "Marathi");

    private static final Map<String, String> APPLIED_SNIPPET_BY_CODE = Map.of(
            "hi", "इस बिल पर आपकी सदस्यता योजना के अनुसार बचत लागू की गई है।",
            "bn", "এই বিলে আপনার সাবস্ক্রিপশন প্ল্যান অনুযায়ী সাশ্রয় প্রয়োগ করা হয়েছে।",
            "ta", "இந்த பில்லில் உங்கள் சந்தா திட்டத்தின் படி சேமிப்பு பயன்படுத்தப்பட்டுள்ளது.",
            "te", "ఈ బిల్లులో మీ సబ్‌స్క్రిప్షన్ ప్లాన్ ప్రకారం ఆదా అమలైంది.",
            "mr", "या बिलावर तुमच्या सदस्यत्व योजनेनुसार बचत लागू केली आहे.");

    private static final Map<String, String> REJECTED_SNIPPET_BY_CODE = Map.of(
            "hi", "इस बिल पर सदस्यता बचत लागू नहीं हुई।",
            "bn", "এই বিলে সাবস্ক্রিপশন সাশ্রয় প্রয়োগ হয়নি।",
            "ta", "இந்த பில்லில் சந்தா சேமிப்பு பயன்படுத்தப்படவில்லை.",
            "te", "ఈ బిల్లులో సబ్‌స్క్రిప్షన్ ఆదా అమలుకాలేదు.",
            "mr", "या बिलावर सदस्यत्व बचत लागू झाली नाही.");

    private static final Map<String, String> REASON_PREFIX_BY_CODE = Map.of(
            "hi", "कारण",
            "bn", "কারণ",
            "ta", "காரணம்",
            "te", "కారణం",
            "mr", "कारण");

    public CompletableFuture<LocalizedExplanation> localize(
            String requestedLanguageCode,
            boolean discountApplied,
            SubscriptionEligibilityCode eligibilityCode,
            String summary,
            List<String> talkingPoints,
            AIOrchestrator aiOrchestrator) {
        String languageCode = normalizeLanguageCode(requestedLanguageCode);
        String languageName = LANGUAGE_NAME_BY_CODE.getOrDefault(languageCode, LANGUAGE_NAME_BY_CODE.get(ENGLISH_CODE));
        String englishSnippet = englishSnippet(summary, talkingPoints);

        if (ENGLISH_CODE.equals(languageCode)) {
            return CompletableFuture.completedFuture(new LocalizedExplanation(
                    languageCode,
                    languageName,
                    englishSnippet,
                    false));
        }

        if (!canUseAi(aiOrchestrator)) {
            return CompletableFuture.completedFuture(
                    fallbackLocalization(languageCode, discountApplied, eligibilityCode, englishSnippet, talkingPoints));
        }

        String prompt = buildTranslationPrompt(englishSnippet, languageName);
        return aiOrchestrator.processQuery(prompt, true, false)
                .orTimeout(4, TimeUnit.SECONDS)
                .thenApply(this::sanitizeAiTranslation)
                .thenApply(translated -> {
                    if (translated == null || translated.isBlank()) {
                        return fallbackLocalization(languageCode, discountApplied, eligibilityCode, englishSnippet, talkingPoints);
                    }
                    return new LocalizedExplanation(languageCode, languageName, translated, true);
                })
                .exceptionally(ex -> fallbackLocalization(
                        languageCode,
                        discountApplied,
                        eligibilityCode,
                        englishSnippet,
                        talkingPoints));
    }

    public Map<String, String> supportedLanguages() {
        Map<String, String> ordered = new LinkedHashMap<>();
        ordered.put("English", "en");
        ordered.put("Hindi", "hi");
        ordered.put("Bengali", "bn");
        ordered.put("Tamil", "ta");
        ordered.put("Telugu", "te");
        ordered.put("Marathi", "mr");
        return ordered;
    }

    private boolean canUseAi(AIOrchestrator aiOrchestrator) {
        if (aiOrchestrator == null) {
            return false;
        }
        return aiOrchestrator.isCloudAvailable() || aiOrchestrator.isLocalAvailable();
    }

    private String normalizeLanguageCode(String requestedLanguageCode) {
        if (requestedLanguageCode == null || requestedLanguageCode.isBlank()) {
            return ENGLISH_CODE;
        }
        String normalized = requestedLanguageCode.trim().toLowerCase(Locale.ROOT);
        return LANGUAGE_NAME_BY_CODE.containsKey(normalized) ? normalized : ENGLISH_CODE;
    }

    private String englishSnippet(String summary, List<String> talkingPoints) {
        String safeSummary = summary == null || summary.isBlank()
                ? "Subscription discount decision is available in billing context."
                : AIInputSafetyGuard.sanitizeFreeText(summary);
        if (talkingPoints == null || talkingPoints.isEmpty()) {
            return safeSummary;
        }
        for (String point : talkingPoints) {
            if (point != null && !point.isBlank()) {
                return safeSummary + " " + AIInputSafetyGuard.sanitizeFreeText(point);
            }
        }
        return safeSummary;
    }

    private LocalizedExplanation fallbackLocalization(
            String languageCode,
            boolean discountApplied,
            SubscriptionEligibilityCode eligibilityCode,
            String englishSnippet,
            List<String> talkingPoints) {
        String languageName = LANGUAGE_NAME_BY_CODE.getOrDefault(languageCode, LANGUAGE_NAME_BY_CODE.get(ENGLISH_CODE));
        String localizedCore = discountApplied
                ? APPLIED_SNIPPET_BY_CODE.getOrDefault(languageCode, englishSnippet)
                : REJECTED_SNIPPET_BY_CODE.getOrDefault(languageCode, englishSnippet);

        String firstReason = firstReasonLine(talkingPoints);
        if (firstReason == null || firstReason.isBlank()) {
            firstReason = defaultReasonFromEligibility(eligibilityCode);
        }
        String reasonPrefix = REASON_PREFIX_BY_CODE.getOrDefault(languageCode, "Reason");
        String snippet = localizedCore + " (" + reasonPrefix + ": " + firstReason + ")";
        return new LocalizedExplanation(languageCode, languageName, snippet, false);
    }

    private String firstReasonLine(List<String> talkingPoints) {
        if (talkingPoints == null) {
            return null;
        }
        for (String point : talkingPoints) {
            if (point == null || point.isBlank()) {
                continue;
            }
            return AIInputSafetyGuard.sanitizeFreeText(point);
        }
        return null;
    }

    private String defaultReasonFromEligibility(SubscriptionEligibilityCode eligibilityCode) {
        if (eligibilityCode == null) {
            return "Subscription context unavailable.";
        }
        return switch (eligibilityCode) {
            case FEATURE_DISABLED -> "Subscription feature is disabled.";
            case NO_CUSTOMER_SELECTED -> "No customer selected.";
            case NO_ENROLLMENT -> "No active subscription enrollment.";
            case ENROLLMENT_FROZEN -> "Enrollment is frozen.";
            case ENROLLMENT_CANCELLED -> "Enrollment is cancelled.";
            case ENROLLMENT_EXPIRED -> "Enrollment is expired.";
            case PLAN_INACTIVE -> "Subscription plan is inactive.";
            case PLAN_NOT_FOUND -> "Subscription plan not found.";
            case INVALID_SUBSCRIPTION_STATE -> "Invalid subscription state.";
            case ELIGIBLE -> "Subscription policy guardrails resulted in no discount.";
        };
    }

    private String buildTranslationPrompt(String englishSnippet, String languageName) {
        return AIPromptCatalog.subscriptionMultilingualTranslationPrompt(englishSnippet, languageName);
    }

    private String sanitizeAiTranslation(String raw) {
        if (raw == null) {
            return "";
        }
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            int newline = cleaned.indexOf('\n');
            if (newline > 0) {
                cleaned = cleaned.substring(newline + 1);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3);
            }
            cleaned = cleaned.trim();
        }
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() > 1) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        return AIInputSafetyGuard.sanitizeFreeText(cleaned);
    }

    public record LocalizedExplanation(
            String languageCode,
            String languageName,
            String snippet,
            boolean aiTranslated) {
    }
}
