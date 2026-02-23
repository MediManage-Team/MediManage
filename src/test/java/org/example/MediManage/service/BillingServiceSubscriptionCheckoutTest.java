package org.example.MediManage.service;

import net.sf.jasperreports.engine.JRException;
import org.example.MediManage.dao.BillDAO;
import org.example.MediManage.dao.CustomerDAO;
import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.dao.SubscriptionDAO;
import org.example.MediManage.model.BillItem;
import org.example.MediManage.model.Customer;
import org.example.MediManage.model.CustomerSubscription;
import org.example.MediManage.model.SubscriptionEnrollmentStatus;
import org.example.MediManage.model.SubscriptionDiscountOverrideStatus;
import org.example.MediManage.model.SubscriptionPlanStatus;
import org.example.MediManage.model.User;
import org.example.MediManage.model.UserRole;
import org.example.MediManage.service.subscription.SubscriptionDiscountEngine;
import org.example.MediManage.service.subscription.SubscriptionEligibilityCode;
import org.example.MediManage.util.UserSession;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BillingServiceSubscriptionCheckoutTest {

    @Test
    void completeCheckoutAppliesSubscriptionDiscountAndPersistsMetadata() throws Exception {
        String releaseKey = "medimanage.feature.subscription.release.enabled";
        String flagKey = "medimanage.feature.subscription.commerce.enabled";
        String previousRelease = System.getProperty(releaseKey);
        String previous = System.getProperty(flagKey);
        System.setProperty(releaseKey, "true");
        System.setProperty(flagKey, "true");

        try {
            FakeBillDAO billDAO = new FakeBillDAO();
            FakeSubscriptionDAO subscriptionDAO = new FakeSubscriptionDAO();
            FakeReportService reportService = new FakeReportService();

            BillingService service = new BillingService(
                    new MedicineDAO(),
                    new CustomerDAO(),
                    billDAO,
                    subscriptionDAO,
                    reportService,
                    null,
                    null,
                    new SubscriptionDiscountEngine());

            List<BillItem> items = new ArrayList<>();
            items.add(new BillItem(101, "Drug A", "2030-12-31", 2, 100.0, 0.0));
            Customer customer = new Customer(5, "Alice", "9999999999");

            BillingService.CheckoutResult result = service.completeCheckout(
                    items,
                    customer,
                    1,
                    "Cash",
                    "care-protocol");

            assertEquals(1, result.billId());
            assertEquals("Gold Plan", result.subscriptionPlanName());
            assertEquals(30.0, result.subscriptionSavings(), 0.0001);

            assertNotNull(billDAO.capturedContext);
            assertEquals(10, billDAO.capturedContext.subscriptionEnrollmentId());
            assertEquals(20, billDAO.capturedContext.subscriptionPlanId());
            assertEquals(15.0, billDAO.capturedContext.subscriptionDiscountPercent(), 0.0001);
            assertEquals(30.0, billDAO.capturedContext.subscriptionSavingsAmount(), 0.0001);

            assertEquals(170.0, billDAO.capturedTotal, 0.0001);
            assertEquals(15.0, items.get(0).getSubscriptionDiscountPercent(), 0.0001);
            assertEquals(30.0, items.get(0).getSubscriptionDiscountAmount(), 0.0001);
            assertEquals("APPLIED", items.get(0).getSubscriptionRuleSource());
            assertTrue(reportService.lastSubscriptionSavings > 0.0);
        } finally {
            restoreProperty(releaseKey, previousRelease);
            if (previous == null) {
                System.clearProperty(flagKey);
            } else {
                System.setProperty(flagKey, previous);
            }
        }
    }

    @Test
    void completeCheckoutWritesLocalizedExplanationToInvoiceReport() throws Exception {
        String releaseKey = "medimanage.feature.subscription.release.enabled";
        String flagKey = "medimanage.feature.subscription.commerce.enabled";
        String previousRelease = System.getProperty(releaseKey);
        String previous = System.getProperty(flagKey);
        System.setProperty(releaseKey, "true");
        System.setProperty(flagKey, "true");

        try {
            FakeBillDAO billDAO = new FakeBillDAO();
            FakeSubscriptionDAO subscriptionDAO = new FakeSubscriptionDAO();
            FakeReportService reportService = new FakeReportService();
            BillingService service = new BillingService(
                    new MedicineDAO(),
                    new CustomerDAO(),
                    billDAO,
                    subscriptionDAO,
                    reportService,
                    null,
                    null,
                    new SubscriptionDiscountEngine());

            List<BillItem> items = new ArrayList<>();
            items.add(new BillItem(101, "Drug A", "2030-12-31", 1, 100.0, 0.0));
            Customer customer = new Customer(5, "Alice", "9999999999");
            BillingService.LocalizedDiscountExplanation localized = new BillingService.LocalizedDiscountExplanation(
                    "hi",
                    "Hindi",
                    "इस बिल पर आपकी सदस्यता योजना के अनुसार बचत लागू की गई है।",
                    false,
                    true,
                    SubscriptionEligibilityCode.ELIGIBLE);

            service.completeCheckout(items, customer, 1, "Cash", "care-protocol", localized);

            assertEquals("Hindi", reportService.lastExplanationLanguage);
            assertTrue(reportService.lastExplanationSnippet.contains("बचत"));
        } finally {
            restoreProperty(releaseKey, previousRelease);
            restoreProperty(flagKey, previous);
        }
    }

    @Test
    void requestManualDiscountOverrideUsesCustomerAndEnrollmentContext() throws Exception {
        String releaseKey = "medimanage.feature.subscription.release.enabled";
        String commerceKey = "medimanage.feature.subscription.commerce.enabled";
        String approvalsKey = "medimanage.feature.subscription.approvals.enabled";
        String overridesKey = "medimanage.feature.subscription.discount.overrides.enabled";
        String previousRelease = System.getProperty(releaseKey);
        String previousCommerce = System.getProperty(commerceKey);
        String previousApprovals = System.getProperty(approvalsKey);
        String previousOverrides = System.getProperty(overridesKey);
        System.setProperty(releaseKey, "true");
        System.setProperty(commerceKey, "true");
        System.setProperty(approvalsKey, "true");
        System.setProperty(overridesKey, "true");

        UserSession.getInstance().login(new User(101, "cashier", "", UserRole.CASHIER));
        try {
            FakeSubscriptionDAO subscriptionDAO = new FakeSubscriptionDAO();
            FakeSubscriptionApprovalService approvalService = new FakeSubscriptionApprovalService(subscriptionDAO);
            BillingService service = new BillingService(
                    new MedicineDAO(),
                    new CustomerDAO(),
                    new FakeBillDAO(),
                    subscriptionDAO,
                    new FakeReportService(),
                    null,
                    null,
                    new SubscriptionDiscountEngine(),
                    approvalService);

            Customer customer = new Customer(5, "Alice", "9999999999");
            BillingService.OverrideRequestSummary summary = service.requestManualDiscountOverride(
                    customer,
                    12.0,
                    "Special retention request");

            assertEquals(7001, summary.overrideId());
            assertEquals(8001, summary.approvalId());
            assertEquals(5, summary.customerId());
            assertEquals(10, summary.enrollmentId());
            assertEquals(5, approvalService.capturedCustomerId);
            assertEquals(10, approvalService.capturedEnrollmentId);
            assertEquals(12.0, approvalService.capturedRequestedPercent, 0.0001);
        } finally {
            UserSession.getInstance().logout();
            restoreProperty(releaseKey, previousRelease);
            restoreProperty(commerceKey, previousCommerce);
            restoreProperty(approvalsKey, previousApprovals);
            restoreProperty(overridesKey, previousOverrides);
        }
    }

    @Test
    void completeCheckoutKeepsTaxPortionIntactWhileApplyingDiscountedSubtotal() throws Exception {
        String releaseKey = "medimanage.feature.subscription.release.enabled";
        String flagKey = "medimanage.feature.subscription.commerce.enabled";
        String previousRelease = System.getProperty(releaseKey);
        String previous = System.getProperty(flagKey);
        System.setProperty(releaseKey, "true");
        System.setProperty(flagKey, "true");

        try {
            FakeBillDAO billDAO = new FakeBillDAO();
            FakeSubscriptionDAO subscriptionDAO = new FakeSubscriptionDAO();
            BillingService service = new BillingService(
                    new MedicineDAO(),
                    new CustomerDAO(),
                    billDAO,
                    subscriptionDAO,
                    new FakeReportService(),
                    null,
                    null,
                    new SubscriptionDiscountEngine());

            List<BillItem> items = new ArrayList<>();
            items.add(new BillItem(101, "Eligible Drug", "2030-12-31", 1, 100.0, 18.0));
            items.add(new BillItem(202, "Excluded Drug", "2030-12-31", 2, 50.0, 18.0));
            Customer customer = new Customer(5, "Alice", "9999999999");

            BillingService.CheckoutResult result = service.completeCheckout(
                    items,
                    customer,
                    1,
                    "Cash",
                    "care-protocol");

            assertEquals(15.0, result.subscriptionSavings(), 0.0001);
            assertNotNull(billDAO.capturedContext);
            assertEquals(7.5, billDAO.capturedContext.subscriptionDiscountPercent(), 0.0001);
            assertEquals(15.0, items.get(0).getSubscriptionDiscountAmount(), 0.0001);
            assertEquals(0.0, items.get(1).getSubscriptionDiscountAmount(), 0.0001);

            // Tax is preserved; only subtotal is discounted for eligible lines.
            assertEquals(18.0, items.get(0).getGst(), 0.0001);
            assertEquals(18.0, items.get(1).getGst(), 0.0001);
            assertEquals(103.0, items.get(0).getTotal(), 0.0001);
            assertEquals(118.0, items.get(1).getTotal(), 0.0001);
            assertEquals(221.0, billDAO.capturedTotal, 0.0001);
        } finally {
            restoreProperty(releaseKey, previousRelease);
            restoreProperty(flagKey, previous);
        }
    }

    @Test
    void completeCheckoutWithSubscriptionFlagOffPreservesLegacyInvoiceTotal() throws Exception {
        String releaseKey = "medimanage.feature.subscription.release.enabled";
        String flagKey = "medimanage.feature.subscription.commerce.enabled";
        String previousRelease = System.getProperty(releaseKey);
        String previous = System.getProperty(flagKey);
        System.setProperty(releaseKey, "true");
        System.setProperty(flagKey, "false");

        try {
            FakeBillDAO billDAO = new FakeBillDAO();
            FakeSubscriptionDAO subscriptionDAO = new FakeSubscriptionDAO();
            BillingService service = new BillingService(
                    new MedicineDAO(),
                    new CustomerDAO(),
                    billDAO,
                    subscriptionDAO,
                    new FakeReportService(),
                    null,
                    null,
                    new SubscriptionDiscountEngine());

            List<BillItem> items = new ArrayList<>();
            items.add(new BillItem(101, "Drug A", "2030-12-31", 2, 100.0, 36.0));
            Customer customer = new Customer(5, "Alice", "9999999999");

            BillingService.CheckoutResult result = service.completeCheckout(
                    items,
                    customer,
                    1,
                    "Cash",
                    "care-protocol");

            assertEquals(0.0, result.subscriptionSavings(), 0.0001);
            assertNull(billDAO.capturedContext);
            assertEquals(236.0, items.get(0).getTotal(), 0.0001);
            assertEquals(236.0, billDAO.capturedTotal, 0.0001);
        } finally {
            restoreProperty(releaseKey, previousRelease);
            restoreProperty(flagKey, previous);
        }
    }

    @Test
    void completeCheckoutWithReleaseGateOffPreservesLegacyInvoiceTotalEvenWhenCommerceOn() throws Exception {
        String releaseKey = "medimanage.feature.subscription.release.enabled";
        String flagKey = "medimanage.feature.subscription.commerce.enabled";
        String previousRelease = System.getProperty(releaseKey);
        String previous = System.getProperty(flagKey);
        System.setProperty(releaseKey, "false");
        System.setProperty(flagKey, "true");

        try {
            FakeBillDAO billDAO = new FakeBillDAO();
            FakeSubscriptionDAO subscriptionDAO = new FakeSubscriptionDAO();
            BillingService service = new BillingService(
                    new MedicineDAO(),
                    new CustomerDAO(),
                    billDAO,
                    subscriptionDAO,
                    new FakeReportService(),
                    null,
                    null,
                    new SubscriptionDiscountEngine());

            List<BillItem> items = new ArrayList<>();
            items.add(new BillItem(101, "Drug A", "2030-12-31", 2, 100.0, 36.0));
            Customer customer = new Customer(5, "Alice", "9999999999");

            BillingService.CheckoutResult result = service.completeCheckout(
                    items,
                    customer,
                    1,
                    "Cash",
                    "care-protocol");

            assertEquals(0.0, result.subscriptionSavings(), 0.0001);
            assertNull(billDAO.capturedContext);
            assertEquals(236.0, items.get(0).getTotal(), 0.0001);
            assertEquals(236.0, billDAO.capturedTotal, 0.0001);
        } finally {
            restoreProperty(releaseKey, previousRelease);
            restoreProperty(flagKey, previous);
        }
    }

    @Test
    void completeCheckoutAppliesCategoryExclusionRuleWhenMedicineRuleMissing() throws Exception {
        String releaseKey = "medimanage.feature.subscription.release.enabled";
        String flagKey = "medimanage.feature.subscription.commerce.enabled";
        String previousRelease = System.getProperty(releaseKey);
        String previous = System.getProperty(flagKey);
        System.setProperty(releaseKey, "true");
        System.setProperty(flagKey, "true");

        try {
            FakeBillDAO billDAO = new FakeBillDAO();
            BillingService service = new BillingService(
                    new MedicineDAO(),
                    new CustomerDAO(),
                    billDAO,
                    new FakeCategoryRuleSubscriptionDAO(),
                    new FakeReportService(),
                    null,
                    null,
                    new SubscriptionDiscountEngine());

            List<BillItem> items = new ArrayList<>();
            items.add(new BillItem(303, "Category Drug", "2030-12-31", 1, 100.0, 0.0));
            Customer customer = new Customer(5, "Alice", "9999999999");

            BillingService.CheckoutResult result = service.completeCheckout(
                    items,
                    customer,
                    1,
                    "Cash",
                    "care-protocol");

            assertEquals(0.0, result.subscriptionSavings(), 0.0001);
            assertNull(billDAO.capturedContext);
            assertEquals(100.0, billDAO.capturedTotal, 0.0001);
            assertEquals(100.0, items.get(0).getTotal(), 0.0001);
        } finally {
            restoreProperty(releaseKey, previousRelease);
            restoreProperty(flagKey, previous);
        }
    }

    @Test
    void explainSubscriptionDiscountDecisionSummarizesAppliedOutcome() {
        String releaseKey = "medimanage.feature.subscription.release.enabled";
        String flagKey = "medimanage.feature.subscription.commerce.enabled";
        String previousRelease = System.getProperty(releaseKey);
        String previous = System.getProperty(flagKey);
        System.setProperty(releaseKey, "true");
        System.setProperty(flagKey, "true");

        try {
            BillingService service = new BillingService(
                    new MedicineDAO(),
                    new CustomerDAO(),
                    new FakeBillDAO(),
                    new FakeSubscriptionDAO(),
                    new FakeReportService(),
                    null,
                    null,
                    new SubscriptionDiscountEngine());

            List<BillItem> items = new ArrayList<>();
            items.add(new BillItem(101, "Drug A", "2030-12-31", 2, 100.0, 0.0));
            BillingService.DiscountDecisionExplanation explanation = service.explainSubscriptionDiscountDecision(
                    items,
                    new Customer(5, "Alice", "9999999999"));

            assertTrue(explanation.discountApplied());
            assertEquals(SubscriptionEligibilityCode.ELIGIBLE, explanation.eligibilityCode());
            assertTrue(explanation.summary().contains("Applied"));
            assertTrue(explanation.talkingPoints().stream().anyMatch(line -> line.contains("Discount affected")));
        } finally {
            restoreProperty(releaseKey, previousRelease);
            restoreProperty(flagKey, previous);
        }
    }

    @Test
    void explainSubscriptionDiscountDecisionSummarizesRejectedOutcome() {
        String releaseKey = "medimanage.feature.subscription.release.enabled";
        String flagKey = "medimanage.feature.subscription.commerce.enabled";
        String previousRelease = System.getProperty(releaseKey);
        String previous = System.getProperty(flagKey);
        System.setProperty(releaseKey, "true");
        System.setProperty(flagKey, "true");

        try {
            BillingService service = new BillingService(
                    new MedicineDAO(),
                    new CustomerDAO(),
                    new FakeBillDAO(),
                    new NoEnrollmentSubscriptionDAO(),
                    new FakeReportService(),
                    null,
                    null,
                    new SubscriptionDiscountEngine());

            List<BillItem> items = new ArrayList<>();
            items.add(new BillItem(101, "Drug A", "2030-12-31", 1, 100.0, 0.0));
            BillingService.DiscountDecisionExplanation explanation = service.explainSubscriptionDiscountDecision(
                    items,
                    new Customer(5, "Alice", "9999999999"));

            assertFalse(explanation.discountApplied());
            assertEquals(SubscriptionEligibilityCode.NO_ENROLLMENT, explanation.eligibilityCode());
            assertTrue(explanation.summary().contains("not applied"));
            assertTrue(explanation.talkingPoints().stream().anyMatch(line -> line.contains("Next step")));
        } finally {
            restoreProperty(releaseKey, previousRelease);
            restoreProperty(flagKey, previous);
        }
    }

    @Test
    void generateLocalizedSubscriptionExplanationFallsBackWhenAiUnavailable() {
        String releaseKey = "medimanage.feature.subscription.release.enabled";
        String flagKey = "medimanage.feature.subscription.commerce.enabled";
        String previousRelease = System.getProperty(releaseKey);
        String previous = System.getProperty(flagKey);
        System.setProperty(releaseKey, "true");
        System.setProperty(flagKey, "true");

        try {
            BillingService service = new BillingService(
                    new MedicineDAO(),
                    new CustomerDAO(),
                    new FakeBillDAO(),
                    new FakeSubscriptionDAO(),
                    new FakeReportService(),
                    null,
                    null,
                    new SubscriptionDiscountEngine());

            List<BillItem> items = new ArrayList<>();
            items.add(new BillItem(101, "Drug A", "2030-12-31", 1, 100.0, 0.0));
            BillingService.LocalizedDiscountExplanation localized = service
                    .generateLocalizedSubscriptionExplanation(
                            items,
                            new Customer(5, "Alice", "9999999999"),
                            "hi")
                    .join();

            assertEquals("hi", localized.languageCode());
            assertEquals("Hindi", localized.languageName());
            assertFalse(localized.aiTranslated());
            assertTrue(localized.snippet() != null && !localized.snippet().isBlank());
        } finally {
            restoreProperty(releaseKey, previousRelease);
            restoreProperty(flagKey, previous);
        }
    }

    @Test
    void generateCheckoutCareProtocolFallsBackToRulesEngineWhenAiUnavailable() {
        BillingService service = new BillingService(
                new MedicineDAO(),
                new CustomerDAO(),
                new FakeBillDAO(),
                new FakeSubscriptionDAO(),
                new FakeReportService(),
                null,
                null,
                new SubscriptionDiscountEngine());

        List<BillItem> items = new ArrayList<>();
        items.add(new BillItem(101, "Drug A", "2030-12-31", 2, 100.0, 0.0));

        String protocol = service.generateCheckoutCareProtocol(items).join();

        assertNotNull(protocol);
        assertTrue(protocol.contains("Rule-based care protocol"));
        assertTrue(protocol.contains("Drug A"));
    }

    private static void restoreProperty(String key, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }

    private static class FakeBillDAO extends BillDAO {
        private SubscriptionInvoiceContext capturedContext;
        private double capturedTotal;

        @Override
        public int generateInvoice(
                double totalAmount,
                List<BillItem> items,
                Integer customerId,
                Integer userId,
                String paymentMode,
                SubscriptionInvoiceContext subscriptionContext) {
            this.capturedTotal = totalAmount;
            this.capturedContext = subscriptionContext;
            return 1;
        }
    }

    private static class FakeSubscriptionDAO extends SubscriptionDAO {
        @Override
        public Optional<EligibilityContext> findLatestEligibilityContext(int customerId) {
            CustomerSubscription enrollment = new CustomerSubscription(
                    10,
                    customerId,
                    20,
                    SubscriptionEnrollmentStatus.ACTIVE,
                    "2026-01-01 00:00:00",
                    "2027-01-01 00:00:00",
                    null,
                    "POS",
                    1,
                    1,
                    "APR-001",
                    null,
                    null,
                    "2026-01-01 00:00:00",
                    "2026-01-01 00:00:00");
            return Optional.of(new EligibilityContext(enrollment, SubscriptionPlanStatus.ACTIVE, "Gold Plan"));
        }

        @Override
        public Optional<ApplicableSubscription> findApplicableSubscription(int customerId) {
            return Optional.of(new ApplicableSubscription(
                    10,
                    20,
                    "GOLD",
                    "Gold Plan",
                    10.0,
                    20.0,
                    0.0,
                    "APR-001"));
        }

        @Override
        public Map<Integer, SubscriptionDiscountEngine.DiscountRule> loadMedicineRules(int planId) {
            return Map.of(
                    101, new SubscriptionDiscountEngine.DiscountRule(true, 15.0, null, null, true),
                    202, new SubscriptionDiscountEngine.DiscountRule(false, 15.0, null, null, true));
        }

        @Override
        public Map<String, SubscriptionDiscountEngine.DiscountRule> loadCategoryRules(int planId) {
            return Map.of();
        }

        @Override
        public Map<Integer, String> loadMedicineCategoryById(List<Integer> medicineIds) {
            return Map.of();
        }
    }

    private static class FakeCategoryRuleSubscriptionDAO extends FakeSubscriptionDAO {
        @Override
        public Map<Integer, SubscriptionDiscountEngine.DiscountRule> loadMedicineRules(int planId) {
            return Map.of();
        }

        @Override
        public Map<String, SubscriptionDiscountEngine.DiscountRule> loadCategoryRules(int planId) {
            return Map.of("antibiotic", new SubscriptionDiscountEngine.DiscountRule(false, 15.0, null, null, true));
        }

        @Override
        public Map<Integer, String> loadMedicineCategoryById(List<Integer> medicineIds) {
            return Map.of(303, "antibiotic");
        }
    }

    private static class NoEnrollmentSubscriptionDAO extends FakeSubscriptionDAO {
        @Override
        public Optional<EligibilityContext> findLatestEligibilityContext(int customerId) {
            return Optional.empty();
        }
    }

    private static class FakeReportService extends ReportService {
        private double lastSubscriptionSavings;
        private String lastExplanationLanguage;
        private String lastExplanationSnippet;

        @Override
        public void generateInvoicePDF(
                List<BillItem> items,
                double totalAmount,
                String customerName,
                String filePath,
                String careProtocol,
                String subscriptionPlanName,
                double subscriptionSavings,
                double subscriptionDiscountPercent,
                String subscriptionExplanationLanguage,
                String subscriptionExplanationSnippet) throws JRException {
            this.lastSubscriptionSavings = subscriptionSavings;
            this.lastExplanationLanguage = subscriptionExplanationLanguage;
            this.lastExplanationSnippet = subscriptionExplanationSnippet;
        }
    }

    private static class FakeSubscriptionApprovalService extends SubscriptionApprovalService {
        private Integer capturedCustomerId;
        private Integer capturedEnrollmentId;
        private double capturedRequestedPercent;

        FakeSubscriptionApprovalService(SubscriptionDAO dao) {
            super(dao);
        }

        @Override
        public OverrideRequestResult requestManualOverride(
                Integer billId,
                Integer billItemId,
                Integer customerId,
                Integer enrollmentId,
                double requestedDiscountPercent,
                String reason) {
            capturedCustomerId = customerId;
            capturedEnrollmentId = enrollmentId;
            capturedRequestedPercent = requestedDiscountPercent;
            return new OverrideRequestResult(
                    7001,
                    8001,
                    SubscriptionDiscountOverrideStatus.PENDING,
                    reason);
        }
    }
}
