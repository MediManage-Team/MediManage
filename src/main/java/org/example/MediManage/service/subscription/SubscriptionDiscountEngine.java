package org.example.MediManage.service.subscription;

import org.example.MediManage.model.BillItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SubscriptionDiscountEngine {

    public EvaluationResult evaluate(List<BillItem> items, EvaluationContext context) {
        List<BillItem> safeItems = items == null ? List.of() : items;
        PlanPolicy plan = context == null ? null : context.plan();
        if (safeItems.isEmpty()) {
            return new EvaluationResult(0.0, 0.0, 0.0, List.of(), List.of());
        }

        Map<Integer, DiscountRule> medicineRules = safeMap(context == null ? null : context.medicineRules());
        Map<String, DiscountRule> categoryRules = safeMap(context == null ? null : context.categoryRules());
        Map<Integer, String> categoryByMedicine = safeMap(context == null ? null : context.medicineCategoryById());
        Map<Integer, Double> costByMedicine = safeMap(context == null ? null : context.medicineCostById());

        List<ItemEvaluation> rows = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        double subtotal = 0.0;
        double totalDiscount = 0.0;

        for (BillItem item : safeItems) {
            if (item == null) {
                continue;
            }

            double lineSubtotal = round2(Math.max(0.0, item.getPrice()) * Math.max(0, item.getQty()));
            subtotal += lineSubtotal;

            if (plan == null || !plan.active()) {
                rows.add(new ItemEvaluation(
                        item.getMedicineId(),
                        item.getName(),
                        item.getQty(),
                        item.getPrice(),
                        lineSubtotal,
                        false,
                        "PLAN_INACTIVE_OR_MISSING",
                        0.0,
                        0.0));
                continue;
            }

            DiscountRule rule = resolveRule(item.getMedicineId(), medicineRules, categoryRules, categoryByMedicine);
            if (rule != null && !rule.active()) {
                rule = null;
            }

            if (rule != null && !rule.includeRule()) {
                rows.add(new ItemEvaluation(
                        item.getMedicineId(),
                        item.getName(),
                        item.getQty(),
                        item.getPrice(),
                        lineSubtotal,
                        false,
                        "EXCLUDED_BY_RULE",
                        0.0,
                        0.0));
                continue;
            }

            double discountPercent = firstNonNull(
                    rule == null ? null : rule.discountPercent(),
                    plan.defaultDiscountPercent());
            discountPercent = clamp(discountPercent, 0.0, plan.maxDiscountPercent());

            double requiredMinMarginPercent = firstNonNull(
                    rule == null ? null : rule.minMarginPercent(),
                    plan.minimumMarginPercent());
            if (requiredMinMarginPercent > 0.0) {
                Double unitCost = costByMedicine.get(item.getMedicineId());
                if (unitCost == null) {
                    warnings.add("Missing medicine cost for ID " + item.getMedicineId()
                            + "; margin floor not enforced for this line.");
                } else {
                    double maxAllowedPercent = maxDiscountPercentByMargin(item.getPrice(), unitCost, requiredMinMarginPercent);
                    discountPercent = Math.min(discountPercent, maxAllowedPercent);
                }
            }

            double discountAmount = round2(lineSubtotal * (discountPercent / 100.0));
            if (rule != null && rule.maxDiscountAmount() != null && rule.maxDiscountAmount() >= 0.0) {
                discountAmount = Math.min(discountAmount, round2(rule.maxDiscountAmount()));
            }
            discountAmount = clamp(discountAmount, 0.0, lineSubtotal);

            double appliedPercent = lineSubtotal <= 0.0 ? 0.0 : round4((discountAmount / lineSubtotal) * 100.0);
            totalDiscount += discountAmount;

            rows.add(new ItemEvaluation(
                    item.getMedicineId(),
                    item.getName(),
                    item.getQty(),
                    item.getPrice(),
                    lineSubtotal,
                    discountAmount > 0.0,
                    discountAmount > 0.0 ? "APPLIED" : "NO_EFFECTIVE_DISCOUNT",
                    appliedPercent,
                    round2(discountAmount)));
        }

        double roundedSubtotal = round2(subtotal);
        double roundedDiscount = round2(totalDiscount);
        double netSubtotal = round2(roundedSubtotal - roundedDiscount);
        return new EvaluationResult(
                roundedSubtotal,
                roundedDiscount,
                netSubtotal,
                Collections.unmodifiableList(rows),
                Collections.unmodifiableList(warnings));
    }

    private DiscountRule resolveRule(
            int medicineId,
            Map<Integer, DiscountRule> medicineRules,
            Map<String, DiscountRule> categoryRules,
            Map<Integer, String> categoryByMedicine) {
        DiscountRule medicineRule = medicineRules.get(medicineId);
        if (medicineRule != null) {
            return medicineRule;
        }
        String category = categoryByMedicine.get(medicineId);
        if (category == null || category.isBlank()) {
            return null;
        }
        return categoryRules.get(category.toLowerCase(Locale.ROOT));
    }

    private double maxDiscountPercentByMargin(double unitPrice, double unitCost, double minMarginPercent) {
        if (unitPrice <= 0.0) {
            return 0.0;
        }
        double minAllowedNetPrice = unitCost + (unitPrice * (minMarginPercent / 100.0));
        double maxDiscountAmount = Math.max(0.0, unitPrice - minAllowedNetPrice);
        return clamp((maxDiscountAmount / unitPrice) * 100.0, 0.0, 100.0);
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static double firstNonNull(Double primary, double fallback) {
        return primary == null ? fallback : primary;
    }

    private static <K, V> Map<K, V> safeMap(Map<K, V> value) {
        return value == null ? Map.of() : value;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    public record PlanPolicy(
            boolean active,
            double defaultDiscountPercent,
            double maxDiscountPercent,
            double minimumMarginPercent) {
    }

    public record DiscountRule(
            boolean includeRule,
            Double discountPercent,
            Double maxDiscountAmount,
            Double minMarginPercent,
            boolean active) {
    }

    public record EvaluationContext(
            PlanPolicy plan,
            Map<Integer, DiscountRule> medicineRules,
            Map<String, DiscountRule> categoryRules,
            Map<Integer, String> medicineCategoryById,
            Map<Integer, Double> medicineCostById) {
    }

    public record ItemEvaluation(
            int medicineId,
            String medicineName,
            int qty,
            double unitPrice,
            double lineSubtotal,
            boolean discountApplied,
            String reasonCode,
            double appliedPercent,
            double discountAmount) {
    }

    public record EvaluationResult(
            double subtotal,
            double totalDiscount,
            double netSubtotal,
            List<ItemEvaluation> items,
            List<String> warnings) {
    }
}
