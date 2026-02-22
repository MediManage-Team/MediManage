package org.example.MediManage.service.subscription;

public enum SubscriptionEligibilityCode {
    ELIGIBLE,
    FEATURE_DISABLED,
    NO_CUSTOMER_SELECTED,
    NO_ENROLLMENT,
    ENROLLMENT_FROZEN,
    ENROLLMENT_CANCELLED,
    ENROLLMENT_EXPIRED,
    PLAN_INACTIVE,
    PLAN_NOT_FOUND,
    INVALID_SUBSCRIPTION_STATE
}
