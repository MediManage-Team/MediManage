package org.example.MediManage.service.subscription;

public record SubscriptionEligibilityResult(
        SubscriptionEligibilityCode code,
        String message,
        Integer enrollmentId,
        Integer planId,
        String planName) {

    public boolean eligible() {
        return code == SubscriptionEligibilityCode.ELIGIBLE;
    }

    public static SubscriptionEligibilityResult eligible(Integer enrollmentId, Integer planId, String planName) {
        return new SubscriptionEligibilityResult(
                SubscriptionEligibilityCode.ELIGIBLE,
                "Subscription is eligible for discount.",
                enrollmentId,
                planId,
                planName);
    }

    public static SubscriptionEligibilityResult ineligible(SubscriptionEligibilityCode code, String message) {
        return new SubscriptionEligibilityResult(code, message, null, null, null);
    }
}
