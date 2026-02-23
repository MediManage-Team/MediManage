package org.example.MediManage.service.subscription;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SubscriptionAIOfflineEvaluationBenchmarkService {
    public static final int MIN_CASES_PER_FEATURE = 8;
    public static final double MIN_PRECISION_PERCENT = 50.0;
    public static final double MIN_RECALL_PERCENT = 50.0;
    public static final double MAX_GROUP_POSITIVE_RATE_GAP_PERCENT = 25.0;

    private static final Set<String> REQUIRED_FEATURE_KEYS = Set.of(
            "PLAN_RECOMMENDATION",
            "RENEWAL_PROPENSITY",
            "ABUSE_DETECTION",
            "DYNAMIC_OFFER",
            "OVERRIDE_RISK");

    public BenchmarkSnapshot evaluate(List<BenchmarkCase> rawCases) {
        List<BenchmarkCase> safeCases = rawCases == null ? List.of() : rawCases;
        Map<String, List<BenchmarkCase>> casesByFeature = new HashMap<>();
        for (BenchmarkCase row : safeCases) {
            if (row == null) {
                continue;
            }
            String featureKey = normalizeFeatureKey(row.featureKey());
            if (featureKey.isBlank()) {
                continue;
            }
            casesByFeature.computeIfAbsent(featureKey, ignored -> new ArrayList<>()).add(row);
        }

        Set<String> featureOrder = new LinkedHashSet<>();
        featureOrder.addAll(REQUIRED_FEATURE_KEYS);
        featureOrder.addAll(casesByFeature.keySet());

        List<FeatureBenchmark> features = new ArrayList<>();
        List<String> globalBlockingReasons = new ArrayList<>();
        for (String featureKey : featureOrder) {
            List<BenchmarkCase> featureCases = casesByFeature.getOrDefault(featureKey, List.of());
            FeatureBenchmark benchmark = evaluateFeature(featureKey, featureCases);
            features.add(benchmark);
            if (!benchmark.passesGate()) {
                globalBlockingReasons.add(featureKey + ": " + String.join("; ", benchmark.blockingReasons()));
            }
        }

        features.sort(Comparator.comparing(FeatureBenchmark::featureKey));
        boolean allFeaturesPass = globalBlockingReasons.isEmpty();
        return new BenchmarkSnapshot(
                allFeaturesPass,
                List.copyOf(features),
                List.copyOf(globalBlockingReasons));
    }

    public Set<String> requiredFeatureKeys() {
        return Set.copyOf(REQUIRED_FEATURE_KEYS);
    }

    private FeatureBenchmark evaluateFeature(String featureKey, List<BenchmarkCase> featureCases) {
        int tp = 0;
        int fp = 0;
        int tn = 0;
        int fn = 0;

        Map<String, MutableGroupCounter> groups = new LinkedHashMap<>();
        for (BenchmarkCase row : featureCases) {
            if (row == null) {
                continue;
            }
            boolean predictedPositive = row.predictedPositive();
            boolean actualPositive = row.actualPositive();
            if (predictedPositive && actualPositive) {
                tp++;
            } else if (predictedPositive) {
                fp++;
            } else if (actualPositive) {
                fn++;
            } else {
                tn++;
            }

            String group = normalizeGroup(row.customerGroup());
            MutableGroupCounter counter = groups.computeIfAbsent(group, ignored -> new MutableGroupCounter(group));
            counter.totalCases++;
            if (predictedPositive) {
                counter.predictedPositiveCount++;
            }
        }

        int totalCases = tp + fp + tn + fn;
        double precisionPercent = (tp + fp) <= 0 ? 0.0 : round2((tp * 100.0) / (tp + fp));
        double recallPercent = (tp + fn) <= 0 ? 0.0 : round2((tp * 100.0) / (tp + fn));
        double accuracyPercent = totalCases <= 0 ? 0.0 : round2(((tp + tn) * 100.0) / totalCases);

        List<GroupBenchmark> groupBenchmarks = new ArrayList<>();
        double minPositiveRate = 100.0;
        double maxPositiveRate = 0.0;
        boolean hasGroupRate = false;
        for (MutableGroupCounter counter : groups.values()) {
            double positiveRate = counter.totalCases <= 0
                    ? 0.0
                    : round2((counter.predictedPositiveCount * 100.0) / counter.totalCases);
            groupBenchmarks.add(new GroupBenchmark(
                    counter.customerGroup,
                    counter.totalCases,
                    counter.predictedPositiveCount,
                    positiveRate));
            if (counter.totalCases > 0) {
                hasGroupRate = true;
                minPositiveRate = Math.min(minPositiveRate, positiveRate);
                maxPositiveRate = Math.max(maxPositiveRate, positiveRate);
            }
        }
        groupBenchmarks.sort(Comparator.comparing(GroupBenchmark::customerGroup));
        double gapPercent = hasGroupRate ? round2(maxPositiveRate - minPositiveRate) : 0.0;

        boolean meetsMinimumCases = totalCases >= MIN_CASES_PER_FEATURE;
        boolean meetsPrecision = precisionPercent >= MIN_PRECISION_PERCENT;
        boolean meetsRecall = recallPercent >= MIN_RECALL_PERCENT;
        boolean meetsFairnessGap = gapPercent <= MAX_GROUP_POSITIVE_RATE_GAP_PERCENT;

        List<String> blockingReasons = new ArrayList<>();
        if (!meetsMinimumCases) {
            blockingReasons.add("Insufficient offline cases (" + totalCases + "/" + MIN_CASES_PER_FEATURE + ").");
        }
        if (!meetsPrecision) {
            blockingReasons.add("Precision below gate (" + precisionPercent + "% < " + MIN_PRECISION_PERCENT + "%).");
        }
        if (!meetsRecall) {
            blockingReasons.add("Recall below gate (" + recallPercent + "% < " + MIN_RECALL_PERCENT + "%).");
        }
        if (!meetsFairnessGap) {
            blockingReasons.add("Fairness positive-rate gap above gate (" + gapPercent + "% > "
                    + MAX_GROUP_POSITIVE_RATE_GAP_PERCENT + "%).");
        }

        if (featureCases.isEmpty() && REQUIRED_FEATURE_KEYS.contains(featureKey)) {
            blockingReasons.add("No offline benchmark cases provided.");
        }

        boolean passesGate = blockingReasons.isEmpty();
        return new FeatureBenchmark(
                featureKey,
                totalCases,
                tp,
                fp,
                tn,
                fn,
                precisionPercent,
                recallPercent,
                accuracyPercent,
                gapPercent,
                meetsMinimumCases,
                meetsPrecision,
                meetsRecall,
                meetsFairnessGap,
                passesGate,
                List.copyOf(groupBenchmarks),
                List.copyOf(blockingReasons));
    }

    private String normalizeFeatureKey(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "";
        }
        String value = raw.trim().toUpperCase(Locale.US).replaceAll("[^A-Z0-9]+", "_");
        value = value.replaceAll("_+", "_");
        if (value.startsWith("_")) {
            value = value.substring(1);
        }
        if (value.endsWith("_")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String normalizeGroup(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "UNKNOWN";
        }
        String value = raw.trim().toUpperCase(Locale.US).replaceAll("[^A-Z0-9]+", "_");
        value = value.replaceAll("_+", "_");
        if (value.startsWith("_")) {
            value = value.substring(1);
        }
        if (value.endsWith("_")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.isBlank() ? "UNKNOWN" : value;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record BenchmarkCase(
            String caseId,
            String featureKey,
            String customerGroup,
            boolean predictedPositive,
            boolean actualPositive) {
    }

    public record GroupBenchmark(
            String customerGroup,
            int totalCases,
            int predictedPositiveCount,
            double predictedPositiveRatePercent) {
    }

    public record FeatureBenchmark(
            String featureKey,
            int totalCases,
            int truePositiveCount,
            int falsePositiveCount,
            int trueNegativeCount,
            int falseNegativeCount,
            double precisionPercent,
            double recallPercent,
            double accuracyPercent,
            double maxGroupPositiveRateGapPercent,
            boolean meetsMinimumCases,
            boolean meetsPrecision,
            boolean meetsRecall,
            boolean meetsFairnessGap,
            boolean passesGate,
            List<GroupBenchmark> groups,
            List<String> blockingReasons) {
    }

    public record BenchmarkSnapshot(
            boolean allFeaturesPass,
            List<FeatureBenchmark> features,
            List<String> blockingReasons) {
    }

    private static final class MutableGroupCounter {
        private final String customerGroup;
        private int totalCases;
        private int predictedPositiveCount;

        private MutableGroupCounter(String customerGroup) {
            this.customerGroup = customerGroup;
        }
    }
}
