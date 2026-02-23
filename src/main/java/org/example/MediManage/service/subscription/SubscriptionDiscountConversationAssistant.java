package org.example.MediManage.service.subscription;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SubscriptionDiscountConversationAssistant {

    public AssistantResponse explain(AssistantInput input) {
        if (input == null) {
            return rejected(
                    SubscriptionEligibilityCode.INVALID_SUBSCRIPTION_STATE,
                    "the customer",
                    "Missing discount-evaluation context.");
        }

        String customer = displayCustomer(input.customerDisplayName());
        if (!input.subscriptionFeatureEnabled()) {
            return rejected(
                    SubscriptionEligibilityCode.FEATURE_DISABLED,
                    customer,
                    "Subscription discount feature is disabled.");
        }

        SubscriptionEligibilityCode code = input.eligibilityCode() == null
                ? SubscriptionEligibilityCode.INVALID_SUBSCRIPTION_STATE
                : input.eligibilityCode();
        if (code != SubscriptionEligibilityCode.ELIGIBLE) {
            return rejected(code, customer, input.eligibilityMessage());
        }

        SubscriptionDiscountEngine.EvaluationResult evaluation = input.evaluation();
        if (evaluation == null) {
            return rejected(
                    SubscriptionEligibilityCode.INVALID_SUBSCRIPTION_STATE,
                    customer,
                    "Discount evaluation could not be produced for this bill.");
        }

        int totalLines = evaluation.items() == null ? 0 : evaluation.items().size();
        int discountedLines = 0;
        if (evaluation.items() != null) {
            for (SubscriptionDiscountEngine.ItemEvaluation row : evaluation.items()) {
                if (row != null && row.discountApplied()) {
                    discountedLines++;
                }
            }
        }

        if (evaluation.totalDiscount() <= 0.0 || discountedLines <= 0) {
            return explainNoDiscount(input, customer, evaluation, totalLines);
        }
        return explainApplied(input, customer, evaluation, totalLines, discountedLines);
    }

    private AssistantResponse explainApplied(
            AssistantInput input,
            String customer,
            SubscriptionDiscountEngine.EvaluationResult evaluation,
            int totalLines,
            int discountedLines) {
        double effectivePercent = evaluation.subtotal() <= 0.0
                ? 0.0
                : round2((evaluation.totalDiscount() / evaluation.subtotal()) * 100.0);
        String plan = safePlan(input.planName());

        String summary = String.format(
                Locale.US,
                "Applied %.2f%% subscription discount for %s (Rs %.2f saved).",
                effectivePercent,
                customer,
                round2(evaluation.totalDiscount()));

        List<String> talkingPoints = new ArrayList<>();
        talkingPoints.add("Eligibility passed under " + plan + " " + enrollmentRef(input.enrollmentId()) + ".");
        talkingPoints.add(String.format(
                Locale.US,
                "Discount affected %d of %d bill line(s).",
                discountedLines,
                Math.max(totalLines, 0)));

        String topMissReason = dominantReasonCode(evaluation.items(), false);
        if (topMissReason != null) {
            talkingPoints.add("Some lines stayed undiscounted: " + describeItemReason(topMissReason));
        }

        if (evaluation.warnings() != null && !evaluation.warnings().isEmpty()) {
            talkingPoints.add("Evaluation warning: " + evaluation.warnings().get(0));
        }

        return new AssistantResponse(
                true,
                SubscriptionEligibilityCode.ELIGIBLE,
                summary,
                List.copyOf(talkingPoints));
    }

    private AssistantResponse explainNoDiscount(
            AssistantInput input,
            String customer,
            SubscriptionDiscountEngine.EvaluationResult evaluation,
            int totalLines) {
        String plan = safePlan(input.planName());
        String summary = "No subscription discount was applied for " + customer + ".";

        List<String> talkingPoints = new ArrayList<>();
        talkingPoints.add("Eligibility passed under " + plan + " " + enrollmentRef(input.enrollmentId()) + ".");
        talkingPoints.add("No bill lines produced a positive discount amount.");

        String topReason = dominantReasonCode(evaluation.items(), false);
        if (topReason != null) {
            talkingPoints.add("Primary line-level outcome: " + describeItemReason(topReason));
        } else if (totalLines <= 0) {
            talkingPoints.add("No bill lines were available for subscription evaluation.");
        }

        if (evaluation.warnings() != null && !evaluation.warnings().isEmpty()) {
            talkingPoints.add("Evaluation warning: " + evaluation.warnings().get(0));
        }

        return new AssistantResponse(
                false,
                SubscriptionEligibilityCode.ELIGIBLE,
                summary,
                List.copyOf(talkingPoints));
    }

    private AssistantResponse rejected(
            SubscriptionEligibilityCode eligibilityCode,
            String customerDisplay,
            String explicitMessage) {
        SubscriptionEligibilityCode safeCode = eligibilityCode == null
                ? SubscriptionEligibilityCode.INVALID_SUBSCRIPTION_STATE
                : eligibilityCode;
        String customer = displayCustomer(customerDisplay);
        String summary = "Subscription discount was not applied for " + customer + ".";

        List<String> talkingPoints = new ArrayList<>();
        String reason = explicitMessage == null || explicitMessage.isBlank()
                ? describeEligibility(safeCode)
                : explicitMessage.trim();
        talkingPoints.add("Reason: " + reason);
        talkingPoints.add("Next step: " + recommendedAction(safeCode));

        return new AssistantResponse(false, safeCode, summary, List.copyOf(talkingPoints));
    }

    private String dominantReasonCode(List<SubscriptionDiscountEngine.ItemEvaluation> rows, boolean discountApplied) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        Map<String, Integer> counts = new HashMap<>();
        for (SubscriptionDiscountEngine.ItemEvaluation row : rows) {
            if (row == null || row.discountApplied() != discountApplied) {
                continue;
            }
            String key = row.reasonCode() == null ? "UNKNOWN" : row.reasonCode().trim().toUpperCase(Locale.US);
            if (key.isBlank()) {
                key = "UNKNOWN";
            }
            counts.put(key, counts.getOrDefault(key, 0) + 1);
        }
        String winner = null;
        int best = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > best) {
                best = entry.getValue();
                winner = entry.getKey();
            }
        }
        return winner;
    }

    private String describeItemReason(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            return "No line-level reason code was recorded.";
        }
        return switch (reasonCode) {
            case "EXCLUDED_BY_RULE" -> "line item is excluded by subscription medicine/category rules";
            case "NO_EFFECTIVE_DISCOUNT" -> "configured discount resolved to zero after guardrails";
            case "PLAN_INACTIVE_OR_MISSING" -> "plan was inactive or unavailable at discount evaluation time";
            case "APPLIED" -> "line-level discount was applied";
            default -> "line-level decision code " + reasonCode;
        };
    }

    private String describeEligibility(SubscriptionEligibilityCode code) {
        return switch (code) {
            case FEATURE_DISABLED -> "Subscription discounts are disabled by feature flags.";
            case NO_CUSTOMER_SELECTED -> "No customer is selected for this bill.";
            case NO_ENROLLMENT -> "Customer does not have an active enrollment.";
            case ENROLLMENT_FROZEN -> "Customer enrollment is currently frozen.";
            case ENROLLMENT_CANCELLED -> "Customer enrollment is cancelled.";
            case ENROLLMENT_EXPIRED -> "Customer enrollment has expired.";
            case PLAN_INACTIVE -> "Enrolled subscription plan is inactive.";
            case PLAN_NOT_FOUND -> "Subscription plan could not be found.";
            case INVALID_SUBSCRIPTION_STATE -> "Subscription state is invalid for discounting.";
            case ELIGIBLE -> "Eligibility passed.";
        };
    }

    private String recommendedAction(SubscriptionEligibilityCode code) {
        return switch (code) {
            case FEATURE_DISABLED -> "Enable subscription commerce flags for this role/store.";
            case NO_CUSTOMER_SELECTED -> "Select a registered customer and re-run discount explanation.";
            case NO_ENROLLMENT -> "Enroll the customer in an active subscription plan.";
            case ENROLLMENT_FROZEN -> "Unfreeze enrollment or use an approved manual override.";
            case ENROLLMENT_CANCELLED -> "Create a new enrollment if policy permits.";
            case ENROLLMENT_EXPIRED -> "Renew enrollment before applying subscription discount.";
            case PLAN_INACTIVE, PLAN_NOT_FOUND -> "Review plan status/configuration in Subscription Admin.";
            case INVALID_SUBSCRIPTION_STATE -> "Verify enrollment dates/status and re-check billing context.";
            case ELIGIBLE -> "Proceed with billing using current subscription settings.";
        };
    }

    private String safePlan(String planName) {
        if (planName == null || planName.isBlank()) {
            return "the customer plan";
        }
        return "plan \"" + planName.trim() + "\"";
    }

    private String enrollmentRef(Integer enrollmentId) {
        if (enrollmentId == null || enrollmentId <= 0) {
            return "(enrollment unavailable)";
        }
        return "(enrollment #" + enrollmentId + ")";
    }

    private String displayCustomer(String customerDisplayName) {
        if (customerDisplayName == null || customerDisplayName.isBlank()) {
            return "the customer";
        }
        return customerDisplayName.trim();
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record AssistantInput(
            boolean subscriptionFeatureEnabled,
            String customerDisplayName,
            SubscriptionEligibilityCode eligibilityCode,
            String eligibilityMessage,
            Integer enrollmentId,
            String planName,
            SubscriptionDiscountEngine.EvaluationResult evaluation) {
    }

    public record AssistantResponse(
            boolean discountApplied,
            SubscriptionEligibilityCode eligibilityCode,
            String summary,
            List<String> talkingPoints) {
    }
}
