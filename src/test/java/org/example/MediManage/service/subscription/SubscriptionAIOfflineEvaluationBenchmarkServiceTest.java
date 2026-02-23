package org.example.MediManage.service.subscription;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionAIOfflineEvaluationBenchmarkServiceTest {

    @Test
    void evaluateFairnessBiasTestSetPassesAllRequiredFeatureGates() throws Exception {
        SubscriptionAIOfflineEvaluationBenchmarkService service = new SubscriptionAIOfflineEvaluationBenchmarkService();
        List<SubscriptionAIOfflineEvaluationBenchmarkService.BenchmarkCase> rows =
                loadCases("/subscription-ai/fairness-bias-test-set-v1.csv");

        SubscriptionAIOfflineEvaluationBenchmarkService.BenchmarkSnapshot snapshot = service.evaluate(rows);

        assertTrue(snapshot.allFeaturesPass());
        Set<String> required = service.requiredFeatureKeys();
        long passedRequiredCount = snapshot.features().stream()
                .filter(feature -> required.contains(feature.featureKey()))
                .filter(SubscriptionAIOfflineEvaluationBenchmarkService.FeatureBenchmark::passesGate)
                .count();
        assertEquals(required.size(), passedRequiredCount);
    }

    @Test
    void evaluateFailsWhenFairnessGapIsTooWideForFeature() {
        SubscriptionAIOfflineEvaluationBenchmarkService service = new SubscriptionAIOfflineEvaluationBenchmarkService();
        List<SubscriptionAIOfflineEvaluationBenchmarkService.BenchmarkCase> rows = new ArrayList<>();
        rows.addAll(buildBalancedFeatureCases("PLAN_RECOMMENDATION", "PR"));
        rows.addAll(buildBalancedFeatureCases("RENEWAL_PROPENSITY", "RP"));
        rows.addAll(buildBalancedFeatureCases("ABUSE_DETECTION", "AB"));
        rows.addAll(buildBiasedFeatureCases("DYNAMIC_OFFER", "DO"));
        rows.addAll(buildBalancedFeatureCases("OVERRIDE_RISK", "OR"));

        SubscriptionAIOfflineEvaluationBenchmarkService.BenchmarkSnapshot snapshot = service.evaluate(rows);
        assertFalse(snapshot.allFeaturesPass());

        SubscriptionAIOfflineEvaluationBenchmarkService.FeatureBenchmark dynamicOffer = snapshot.features().stream()
                .filter(feature -> "DYNAMIC_OFFER".equals(feature.featureKey()))
                .findFirst()
                .orElseThrow();
        assertFalse(dynamicOffer.meetsFairnessGap());
        assertFalse(dynamicOffer.passesGate());
        assertTrue(dynamicOffer.blockingReasons().stream()
                .anyMatch(reason -> reason.contains("Fairness positive-rate gap")));
    }

    private List<SubscriptionAIOfflineEvaluationBenchmarkService.BenchmarkCase> loadCases(String resourcePath)
            throws Exception {
        InputStream is = getClass().getResourceAsStream(resourcePath);
        assertNotNull(is, "Missing resource: " + resourcePath);

        List<SubscriptionAIOfflineEvaluationBenchmarkService.BenchmarkCase> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (header) {
                    header = false;
                    continue;
                }
                String[] columns = trimmed.split(",");
                if (columns.length < 5) {
                    continue;
                }
                rows.add(new SubscriptionAIOfflineEvaluationBenchmarkService.BenchmarkCase(
                        columns[0].trim(),
                        columns[1].trim(),
                        columns[2].trim(),
                        Boolean.parseBoolean(columns[3].trim()),
                        Boolean.parseBoolean(columns[4].trim())));
            }
        }
        return rows;
    }

    private List<SubscriptionAIOfflineEvaluationBenchmarkService.BenchmarkCase> buildBalancedFeatureCases(
            String featureKey,
            String prefix) {
        List<SubscriptionAIOfflineEvaluationBenchmarkService.BenchmarkCase> rows = new ArrayList<>();
        rows.add(new SubscriptionAIOfflineEvaluationBenchmarkService.BenchmarkCase(
                prefix + "-ADULT-1", featureKey, "ADULT", true, true));
        rows.add(new SubscriptionAIOfflineEvaluationBenchmarkService.BenchmarkCase(
                prefix + "-ADULT-2", featureKey, "ADULT", false, false));
        rows.add(new SubscriptionAIOfflineEvaluationBenchmarkService.BenchmarkCase(
                prefix + "-SENIOR-1", featureKey, "SENIOR", true, true));
        rows.add(new SubscriptionAIOfflineEvaluationBenchmarkService.BenchmarkCase(
                prefix + "-SENIOR-2", featureKey, "SENIOR", false, false));
        rows.add(new SubscriptionAIOfflineEvaluationBenchmarkService.BenchmarkCase(
                prefix + "-RURAL-1", featureKey, "RURAL", true, true));
        rows.add(new SubscriptionAIOfflineEvaluationBenchmarkService.BenchmarkCase(
                prefix + "-RURAL-2", featureKey, "RURAL", false, false));
        rows.add(new SubscriptionAIOfflineEvaluationBenchmarkService.BenchmarkCase(
                prefix + "-URBAN-1", featureKey, "URBAN", true, true));
        rows.add(new SubscriptionAIOfflineEvaluationBenchmarkService.BenchmarkCase(
                prefix + "-URBAN-2", featureKey, "URBAN", false, false));
        return rows;
    }

    private List<SubscriptionAIOfflineEvaluationBenchmarkService.BenchmarkCase> buildBiasedFeatureCases(
            String featureKey,
            String prefix) {
        List<SubscriptionAIOfflineEvaluationBenchmarkService.BenchmarkCase> rows = new ArrayList<>();
        rows.add(new SubscriptionAIOfflineEvaluationBenchmarkService.BenchmarkCase(
                prefix + "-ADULT-1", featureKey, "ADULT", true, true));
        rows.add(new SubscriptionAIOfflineEvaluationBenchmarkService.BenchmarkCase(
                prefix + "-ADULT-2", featureKey, "ADULT", true, true));
        rows.add(new SubscriptionAIOfflineEvaluationBenchmarkService.BenchmarkCase(
                prefix + "-SENIOR-1", featureKey, "SENIOR", false, false));
        rows.add(new SubscriptionAIOfflineEvaluationBenchmarkService.BenchmarkCase(
                prefix + "-SENIOR-2", featureKey, "SENIOR", false, false));
        rows.add(new SubscriptionAIOfflineEvaluationBenchmarkService.BenchmarkCase(
                prefix + "-RURAL-1", featureKey, "RURAL", false, false));
        rows.add(new SubscriptionAIOfflineEvaluationBenchmarkService.BenchmarkCase(
                prefix + "-RURAL-2", featureKey, "RURAL", false, false));
        rows.add(new SubscriptionAIOfflineEvaluationBenchmarkService.BenchmarkCase(
                prefix + "-URBAN-1", featureKey, "URBAN", false, false));
        rows.add(new SubscriptionAIOfflineEvaluationBenchmarkService.BenchmarkCase(
                prefix + "-URBAN-2", featureKey, "URBAN", false, false));
        return rows;
    }
}
