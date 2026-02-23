package org.example.MediManage.service;

import net.sf.jasperreports.engine.JRException;
import org.example.MediManage.dao.BillDAO;
import org.example.MediManage.dao.CustomerDAO;
import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.dao.SubscriptionDAO;
import org.example.MediManage.config.FeatureFlag;
import org.example.MediManage.config.FeatureFlags;
import org.example.MediManage.model.BillItem;
import org.example.MediManage.model.Customer;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.service.ai.AIOrchestrator;
import org.example.MediManage.service.ai.AIPromptCatalog;
import org.example.MediManage.service.ai.AIServiceProvider;
import org.example.MediManage.service.ai.CloudAIService;
import org.example.MediManage.security.Permission;
import org.example.MediManage.security.RbacPolicy;
import org.example.MediManage.service.subscription.SubscriptionDiscountConversationAssistant;
import org.example.MediManage.service.subscription.SubscriptionDiscountEngine;
import org.example.MediManage.service.subscription.SubscriptionEligibilityCode;
import org.example.MediManage.service.subscription.SubscriptionEligibilityResult;
import org.example.MediManage.service.subscription.SubscriptionMultilingualExplanationService;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class BillingService {
    public enum AddItemStatus {
        ADDED,
        INVALID_REQUEST,
        INVALID_QTY,
        OUT_OF_STOCK
    }

    public record AddItemResult(AddItemStatus status, boolean requiresTableRefresh, int availableStock) {
    }

    public record CheckoutResult(
            int billId,
            String pdfPath,
            double total,
            String customerName,
            String subscriptionPlanName,
            double subscriptionSavings) {
    }

    public record OverrideRequestSummary(
            int overrideId,
            int approvalId,
            double requestedDiscountPercent,
            Integer customerId,
            Integer enrollmentId,
            SubscriptionEligibilityCode eligibilityCode,
            String eligibilityMessage) {
    }

    public record DiscountDecisionExplanation(
            boolean discountApplied,
            SubscriptionEligibilityCode eligibilityCode,
            String summary,
            List<String> talkingPoints) {
    }

    public record LocalizedDiscountExplanation(
            String languageCode,
            String languageName,
            String snippet,
            boolean aiTranslated,
            boolean discountApplied,
            SubscriptionEligibilityCode eligibilityCode) {
    }

    private final MedicineDAO medicineDAO;
    private final CustomerDAO customerDAO;
    private final BillDAO billDAO;
    private final SubscriptionDAO subscriptionDAO;
    private final SubscriptionService subscriptionService;
    private final ReportService reportService;
    private final AIOrchestrator aiOrchestrator;
    private final CloudAIService cloudService;
    private final SubscriptionDiscountEngine subscriptionDiscountEngine;
    private final SubscriptionApprovalService subscriptionApprovalService;
    private final SubscriptionDiscountConversationAssistant discountConversationAssistant;
    private final SubscriptionMultilingualExplanationService multilingualExplanationService;
    private final BillingCareProtocolFallbackRulesEngine careProtocolFallbackRulesEngine;

    public BillingService() {
        this(
                new MedicineDAO(),
                new CustomerDAO(),
                new BillDAO(),
                new SubscriptionDAO(),
                new ReportService(),
                AIServiceProvider.get().getOrchestrator(),
                AIServiceProvider.get().getCloudService(),
                new SubscriptionDiscountEngine());
    }

    BillingService(
            MedicineDAO medicineDAO,
            CustomerDAO customerDAO,
            BillDAO billDAO,
            SubscriptionDAO subscriptionDAO,
            ReportService reportService,
            AIOrchestrator aiOrchestrator,
            CloudAIService cloudService,
            SubscriptionDiscountEngine subscriptionDiscountEngine) {
        this(
                medicineDAO,
                customerDAO,
                billDAO,
                subscriptionDAO,
                reportService,
                aiOrchestrator,
                cloudService,
                subscriptionDiscountEngine,
                new SubscriptionApprovalService(subscriptionDAO),
                new SubscriptionDiscountConversationAssistant(),
                new SubscriptionMultilingualExplanationService());
    }

    BillingService(
            MedicineDAO medicineDAO,
            CustomerDAO customerDAO,
            BillDAO billDAO,
            SubscriptionDAO subscriptionDAO,
            ReportService reportService,
            AIOrchestrator aiOrchestrator,
            CloudAIService cloudService,
            SubscriptionDiscountEngine subscriptionDiscountEngine,
            SubscriptionApprovalService subscriptionApprovalService) {
        this(
                medicineDAO,
                customerDAO,
                billDAO,
                subscriptionDAO,
                reportService,
                aiOrchestrator,
                cloudService,
                subscriptionDiscountEngine,
                subscriptionApprovalService,
                new SubscriptionDiscountConversationAssistant(),
                new SubscriptionMultilingualExplanationService());
    }

    BillingService(
            MedicineDAO medicineDAO,
            CustomerDAO customerDAO,
            BillDAO billDAO,
            SubscriptionDAO subscriptionDAO,
            ReportService reportService,
            AIOrchestrator aiOrchestrator,
            CloudAIService cloudService,
            SubscriptionDiscountEngine subscriptionDiscountEngine,
            SubscriptionApprovalService subscriptionApprovalService,
            SubscriptionDiscountConversationAssistant discountConversationAssistant,
            SubscriptionMultilingualExplanationService multilingualExplanationService) {
        this(
                medicineDAO,
                customerDAO,
                billDAO,
                subscriptionDAO,
                reportService,
                aiOrchestrator,
                cloudService,
                subscriptionDiscountEngine,
                subscriptionApprovalService,
                discountConversationAssistant,
                multilingualExplanationService,
                new BillingCareProtocolFallbackRulesEngine());
    }

    BillingService(
            MedicineDAO medicineDAO,
            CustomerDAO customerDAO,
            BillDAO billDAO,
            SubscriptionDAO subscriptionDAO,
            ReportService reportService,
            AIOrchestrator aiOrchestrator,
            CloudAIService cloudService,
            SubscriptionDiscountEngine subscriptionDiscountEngine,
            SubscriptionApprovalService subscriptionApprovalService,
            SubscriptionDiscountConversationAssistant discountConversationAssistant,
            SubscriptionMultilingualExplanationService multilingualExplanationService,
            BillingCareProtocolFallbackRulesEngine careProtocolFallbackRulesEngine) {
        this.medicineDAO = medicineDAO;
        this.customerDAO = customerDAO;
        this.billDAO = billDAO;
        this.subscriptionDAO = subscriptionDAO;
        this.subscriptionService = new SubscriptionService(subscriptionDAO);
        this.reportService = reportService;
        this.aiOrchestrator = aiOrchestrator;
        this.cloudService = cloudService;
        this.subscriptionDiscountEngine = subscriptionDiscountEngine;
        this.subscriptionApprovalService = subscriptionApprovalService;
        this.discountConversationAssistant = discountConversationAssistant;
        this.multilingualExplanationService = multilingualExplanationService;
        this.careProtocolFallbackRulesEngine = careProtocolFallbackRulesEngine;
    }

    public List<Medicine> loadActiveMedicines() {
        return medicineDAO.getAllMedicines();
    }

    public List<Customer> searchCustomers(String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }
        return customerDAO.searchCustomer(query.trim());
    }

    public Customer addCustomerAndFind(String name, String phone) throws SQLException {
        Customer newCustomer = new Customer(0, name, phone);
        customerDAO.addCustomer(newCustomer);
        List<Customer> matches = customerDAO.searchCustomer(phone);
        return matches.isEmpty() ? null : matches.get(0);
    }

    public AddItemResult addMedicineToBill(List<BillItem> billItems, Medicine medicine, int qty) {
        if (billItems == null || medicine == null) {
            return new AddItemResult(AddItemStatus.INVALID_REQUEST, false, 0);
        }
        if (qty <= 0) {
            return new AddItemResult(AddItemStatus.INVALID_QTY, false, medicine.getStock());
        }
        if (qty > medicine.getStock()) {
            return new AddItemResult(AddItemStatus.OUT_OF_STOCK, false, medicine.getStock());
        }

        Optional<BillItem> existing = billItems.stream()
                .filter(item -> item.getMedicineId() == medicine.getId())
                .findFirst();

        if (existing.isPresent()) {
            BillItem item = existing.get();
            item.setQty(item.getQty() + qty);
            // Preserve current pricing behavior for existing-line updates.
            item.setTotal(item.getTotal() + (medicine.getPrice() * qty));
            return new AddItemResult(AddItemStatus.ADDED, true, medicine.getStock());
        }

        double gst = (medicine.getPrice() * qty) * 0.18;
        billItems.add(new BillItem(
                medicine.getId(),
                medicine.getName(),
                medicine.getExpiry(),
                qty,
                medicine.getPrice(),
                gst));
        return new AddItemResult(AddItemStatus.ADDED, false, medicine.getStock());
    }

    public double calculateTotal(List<BillItem> billItems) {
        return billItems == null ? 0.0 : billItems.stream().mapToDouble(BillItem::getTotal).sum();
    }

    public CompletableFuture<String> generateCheckoutCareProtocol(List<BillItem> billItems) {
        String prompt = AIPromptCatalog.checkoutCareProtocolPrompt(billItems);
        if (aiOrchestrator == null) {
            return CompletableFuture.completedFuture(
                    fallbackCheckoutCareProtocol(billItems, "AI orchestrator unavailable"));
        }
        try {
            return aiOrchestrator.processQuery(prompt, true, false)
                    .handle((response, ex) -> {
                        if (ex != null || !isUsableCareProtocol(response)) {
                            String reason = ex == null ? "AI response unusable" : ex.getMessage();
                            return fallbackCheckoutCareProtocol(billItems, reason);
                        }
                        return response.trim();
                    });
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    fallbackCheckoutCareProtocol(billItems, e.getMessage()));
        }
    }

    public CompletableFuture<String> generateDetailedCareProtocol(List<BillItem> billItems) {
        return aiOrchestrator.cloudQuery(AIPromptCatalog.detailedCareProtocolPrompt(billItems));
    }

    public String getCloudProviderInfo() {
        return cloudService.getProviderName() + " — " + cloudService.getActiveModel();
    }

    public Map<String, String> supportedExplanationLanguages() {
        return multilingualExplanationService.supportedLanguages();
    }

    public CheckoutResult completeCheckout(
            List<BillItem> billItems,
            Customer selectedCustomer,
            int userId,
            String paymentMode,
            String careProtocol) throws SQLException, JRException {
        return completeCheckout(
                billItems,
                selectedCustomer,
                userId,
                paymentMode,
                careProtocol,
                null);
    }

    public CheckoutResult completeCheckout(
            List<BillItem> billItems,
            Customer selectedCustomer,
            int userId,
            String paymentMode,
            String careProtocol,
            LocalizedDiscountExplanation localizedExplanation) throws SQLException, JRException {
        if (billItems == null || billItems.isEmpty()) {
            throw new IllegalArgumentException("No bill items found for checkout.");
        }

        if ("Credit".equalsIgnoreCase(paymentMode) && selectedCustomer == null) {
            throw new IllegalArgumentException("Credit requires a selected customer.");
        }

        AppliedSubscriptionDiscount appliedDiscount = applySubscriptionDiscountIfEligible(billItems, selectedCustomer);
        double total = calculateTotal(billItems);
        Integer customerId = selectedCustomer != null ? selectedCustomer.getCustomerId() : null;
        String customerName = selectedCustomer != null ? selectedCustomer.getName() : "Walk-in";

        BillDAO.SubscriptionInvoiceContext invoiceContext = appliedDiscount.toInvoiceContext();
        int billId = billDAO.generateInvoice(total, billItems, customerId, userId, paymentMode, invoiceContext);
        DashboardKpiService.invalidateSalesMetrics();
        DashboardKpiService.invalidateSubscriptionMetrics();
        String pdfPath = "Invoice_" + billId + ".pdf";
        LocalizedDiscountExplanation explanationForInvoice = localizedExplanation == null
                ? defaultInvoiceExplanation(billItems, selectedCustomer)
                : localizedExplanation;
        reportService.generateInvoicePDF(
                billItems,
                total,
                customerName,
                pdfPath,
                careProtocol,
                appliedDiscount.planName(),
                appliedDiscount.totalSavings(),
                appliedDiscount.discountPercent(),
                explanationForInvoice.languageName(),
                explanationForInvoice.snippet());

        return new CheckoutResult(
                billId,
                pdfPath,
                total,
                customerName,
                appliedDiscount.planName(),
                appliedDiscount.totalSavings());
    }

    public CompletableFuture<LocalizedDiscountExplanation> generateLocalizedSubscriptionExplanation(
            List<BillItem> billItems,
            Customer selectedCustomer,
            String languageCode) {
        DiscountDecisionExplanation explanation = explainSubscriptionDiscountDecision(billItems, selectedCustomer);
        return multilingualExplanationService.localize(
                languageCode,
                explanation.discountApplied(),
                explanation.eligibilityCode(),
                explanation.summary(),
                explanation.talkingPoints(),
                aiOrchestrator)
                .thenApply(row -> new LocalizedDiscountExplanation(
                        row.languageCode(),
                        row.languageName(),
                        row.snippet(),
                        row.aiTranslated(),
                        explanation.discountApplied(),
                        explanation.eligibilityCode()))
                .exceptionally(ex -> defaultLocalizedExplanation(explanation));
    }

    public List<BillItem> snapshotItems(List<BillItem> billItems) {
        return billItems == null ? List.of() : new ArrayList<>(billItems);
    }

    public SubscriptionDiscountEngine.EvaluationResult previewSubscriptionDiscount(
            List<BillItem> billItems,
            SubscriptionDiscountEngine.EvaluationContext context) {
        return subscriptionDiscountEngine.evaluate(billItems, context);
    }

    public SubscriptionEligibilityResult evaluateSubscriptionEligibility(Customer selectedCustomer) {
        if (selectedCustomer == null) {
            return SubscriptionEligibilityResult.ineligible(
                    SubscriptionEligibilityCode.NO_CUSTOMER_SELECTED,
                    "No customer selected.");
        }
        return subscriptionService.evaluateEligibility(selectedCustomer.getCustomerId());
    }

    public boolean isManualOverrideRequestEnabled() {
        return FeatureFlags.isEnabledForCurrentUser(FeatureFlag.SUBSCRIPTION_COMMERCE)
                && FeatureFlags.isEnabledForCurrentUser(FeatureFlag.SUBSCRIPTION_APPROVALS)
                && FeatureFlags.isEnabledForCurrentUser(FeatureFlag.SUBSCRIPTION_DISCOUNT_OVERRIDES);
    }

    public boolean canCurrentUserRequestManualOverride() {
        try {
            RbacPolicy.requireCurrentUser(Permission.MANAGE_SUBSCRIPTION_ENROLLMENTS);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    public OverrideRequestSummary requestManualDiscountOverride(
            Customer selectedCustomer,
            double requestedDiscountPercent,
            String reason) throws SQLException {
        if (!isManualOverrideRequestEnabled()) {
            throw new IllegalStateException("Subscription override requests are disabled.");
        }
        if (selectedCustomer == null) {
            throw new IllegalArgumentException("A customer must be selected to request a discount override.");
        }

        SubscriptionEligibilityResult eligibility = subscriptionService
                .evaluateEligibility(selectedCustomer.getCustomerId());
        Integer enrollmentId = eligibility.eligible() ? eligibility.enrollmentId() : null;

        SubscriptionApprovalService.OverrideRequestResult requestResult = subscriptionApprovalService.requestManualOverride(
                null,
                null,
                selectedCustomer.getCustomerId(),
                enrollmentId,
                requestedDiscountPercent,
                reason);

        return new OverrideRequestSummary(
                requestResult.overrideId(),
                requestResult.approvalId(),
                requestedDiscountPercent,
                selectedCustomer.getCustomerId(),
                enrollmentId,
                eligibility.code(),
                eligibility.message());
    }

    public DiscountDecisionExplanation explainSubscriptionDiscountDecision(
            List<BillItem> billItems,
            Customer selectedCustomer) {
        DiscountEvaluationContext context = buildDiscountEvaluationContext(billItems, selectedCustomer);
        SubscriptionDiscountConversationAssistant.AssistantResponse response = discountConversationAssistant.explain(
                new SubscriptionDiscountConversationAssistant.AssistantInput(
                        context.featureEnabled(),
                        selectedCustomer == null ? "the customer" : selectedCustomer.getName(),
                        context.eligibilityCode(),
                        context.eligibilityMessage(),
                        context.enrollmentId(),
                        context.planName(),
                        context.evaluation()));
        return new DiscountDecisionExplanation(
                response.discountApplied(),
                response.eligibilityCode(),
                response.summary(),
                response.talkingPoints());
    }

    private LocalizedDiscountExplanation defaultInvoiceExplanation(
            List<BillItem> billItems,
            Customer selectedCustomer) {
        DiscountDecisionExplanation explanation = explainSubscriptionDiscountDecision(billItems, selectedCustomer);
        return defaultLocalizedExplanation(explanation);
    }

    private LocalizedDiscountExplanation defaultLocalizedExplanation(DiscountDecisionExplanation explanation) {
        String snippet = explanation == null || explanation.summary() == null || explanation.summary().isBlank()
                ? "Subscription discount decision is available in billing context."
                : explanation.summary().trim();
        if (explanation != null && explanation.talkingPoints() != null) {
            for (String point : explanation.talkingPoints()) {
                if (point == null || point.isBlank()) {
                    continue;
                }
                snippet = snippet + " " + point.trim();
                break;
            }
        }
        return new LocalizedDiscountExplanation(
                "en",
                "English",
                snippet,
                false,
                explanation != null && explanation.discountApplied(),
                explanation == null ? SubscriptionEligibilityCode.INVALID_SUBSCRIPTION_STATE : explanation.eligibilityCode());
    }

    private AppliedSubscriptionDiscount applySubscriptionDiscountIfEligible(
            List<BillItem> billItems,
            Customer selectedCustomer) {
        DiscountEvaluationContext context = buildDiscountEvaluationContext(billItems, selectedCustomer);
        if (context.eligibilityCode() != SubscriptionEligibilityCode.ELIGIBLE) {
            return AppliedSubscriptionDiscount.none(context.eligibilityCode());
        }
        if (context.applicable() == null || context.evaluation() == null) {
            return AppliedSubscriptionDiscount.none(SubscriptionEligibilityCode.INVALID_SUBSCRIPTION_STATE);
        }

        SubscriptionDAO.ApplicableSubscription applicable = context.applicable();
        SubscriptionDiscountEngine.EvaluationResult evaluation = context.evaluation();
        if (evaluation.totalDiscount() <= 0.0) {
            return AppliedSubscriptionDiscount.none(SubscriptionEligibilityCode.ELIGIBLE);
        }

        Map<Integer, SubscriptionDiscountEngine.ItemEvaluation> itemByMedicine = new HashMap<>();
        for (SubscriptionDiscountEngine.ItemEvaluation itemEvaluation : evaluation.items()) {
            itemByMedicine.put(itemEvaluation.medicineId(), itemEvaluation);
        }

        for (BillItem item : billItems) {
            SubscriptionDiscountEngine.ItemEvaluation itemEvaluation = itemByMedicine.get(item.getMedicineId());
            if (itemEvaluation == null) {
                continue;
            }

            item.setSubscriptionDiscountPercent(itemEvaluation.appliedPercent());
            item.setSubscriptionDiscountAmount(itemEvaluation.discountAmount());
            item.setSubscriptionRuleSource(itemEvaluation.reasonCode());

            double discountedLine = (item.getPrice() * item.getQty()) - itemEvaluation.discountAmount() + item.getGst();
            item.setTotal(round2(Math.max(0.0, discountedLine)));
        }

        double discountPercent = evaluation.subtotal() <= 0.0
                ? 0.0
                : round4((evaluation.totalDiscount() / evaluation.subtotal()) * 100.0);

        return new AppliedSubscriptionDiscount(
                applicable.enrollmentId(),
                applicable.planId(),
                applicable.planName(),
                applicable.approvalReference(),
                discountPercent,
                evaluation.totalDiscount(),
                SubscriptionEligibilityCode.ELIGIBLE);
    }

    private DiscountEvaluationContext buildDiscountEvaluationContext(
            List<BillItem> billItems,
            Customer selectedCustomer) {
        boolean featureEnabled = FeatureFlags.isEnabledForCurrentUser(FeatureFlag.SUBSCRIPTION_COMMERCE);
        if (!featureEnabled) {
            return new DiscountEvaluationContext(
                    false,
                    SubscriptionEligibilityCode.FEATURE_DISABLED,
                    "Subscription discount feature is disabled.",
                    null,
                    null,
                    null,
                    null);
        }
        if (selectedCustomer == null) {
            return new DiscountEvaluationContext(
                    true,
                    SubscriptionEligibilityCode.NO_CUSTOMER_SELECTED,
                    "No customer selected.",
                    null,
                    null,
                    null,
                    null);
        }
        if (billItems == null || billItems.isEmpty()) {
            return new DiscountEvaluationContext(
                    true,
                    SubscriptionEligibilityCode.INVALID_SUBSCRIPTION_STATE,
                    "No bill items available for discount evaluation.",
                    null,
                    null,
                    null,
                    null);
        }

        SubscriptionEligibilityResult eligibilityResult = subscriptionService
                .evaluateEligibility(selectedCustomer.getCustomerId());
        if (!eligibilityResult.eligible()) {
            return new DiscountEvaluationContext(
                    true,
                    eligibilityResult.code(),
                    eligibilityResult.message(),
                    eligibilityResult.enrollmentId(),
                    eligibilityResult.planName(),
                    null,
                    null);
        }

        Optional<SubscriptionDAO.ApplicableSubscription> applicableOpt = subscriptionDAO
                .findApplicableSubscription(selectedCustomer.getCustomerId());
        if (applicableOpt.isEmpty()) {
            return new DiscountEvaluationContext(
                    true,
                    SubscriptionEligibilityCode.INVALID_SUBSCRIPTION_STATE,
                    "Applicable subscription was not found for this customer.",
                    eligibilityResult.enrollmentId(),
                    eligibilityResult.planName(),
                    null,
                    null);
        }

        SubscriptionDAO.ApplicableSubscription applicable = applicableOpt.get();
        SubscriptionDiscountEngine.EvaluationResult evaluation = subscriptionDiscountEngine.evaluate(
                billItems,
                buildDiscountEngineContext(applicable, billItems));

        return new DiscountEvaluationContext(
                true,
                SubscriptionEligibilityCode.ELIGIBLE,
                eligibilityResult.message(),
                applicable.enrollmentId(),
                applicable.planName(),
                applicable,
                evaluation);
    }

    private SubscriptionDiscountEngine.EvaluationContext buildDiscountEngineContext(
            SubscriptionDAO.ApplicableSubscription applicable,
            List<BillItem> billItems) {
        List<Integer> medicineIds = collectMedicineIds(billItems);
        Map<Integer, SubscriptionDiscountEngine.DiscountRule> medicineRules = subscriptionDAO.loadMedicineRules(
                applicable.planId());
        Map<String, SubscriptionDiscountEngine.DiscountRule> categoryRules = subscriptionDAO.loadCategoryRules(
                applicable.planId());
        Map<Integer, String> medicineCategoryById = subscriptionDAO.loadMedicineCategoryById(medicineIds);
        return new SubscriptionDiscountEngine.EvaluationContext(
                new SubscriptionDiscountEngine.PlanPolicy(
                        true,
                        applicable.defaultDiscountPercent(),
                        applicable.maxDiscountPercent(),
                        applicable.minimumMarginPercent()),
                medicineRules,
                categoryRules,
                medicineCategoryById,
                Map.of());
    }

    private String fallbackCheckoutCareProtocol(List<BillItem> billItems, String reason) {
        return careProtocolFallbackRulesEngine.build(billItems, reason);
    }

    private boolean isUsableCareProtocol(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return !(normalized.startsWith("error:")
                || normalized.contains("ai service unavailable")
                || normalized.contains("cloud ai required but not available"));
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private List<Integer> collectMedicineIds(List<BillItem> billItems) {
        if (billItems == null || billItems.isEmpty()) {
            return List.of();
        }
        Set<Integer> distinct = new HashSet<>();
        List<Integer> ids = new ArrayList<>();
        for (BillItem item : billItems) {
            if (item == null || item.getMedicineId() <= 0) {
                continue;
            }
            if (distinct.add(item.getMedicineId())) {
                ids.add(item.getMedicineId());
            }
        }
        return ids;
    }

    private record DiscountEvaluationContext(
            boolean featureEnabled,
            SubscriptionEligibilityCode eligibilityCode,
            String eligibilityMessage,
            Integer enrollmentId,
            String planName,
            SubscriptionDAO.ApplicableSubscription applicable,
            SubscriptionDiscountEngine.EvaluationResult evaluation) {
    }

    private record AppliedSubscriptionDiscount(
            Integer enrollmentId,
            Integer planId,
            String planName,
            String approvalReference,
            double discountPercent,
            double totalSavings,
            SubscriptionEligibilityCode eligibilityCode) {
        static AppliedSubscriptionDiscount none(SubscriptionEligibilityCode code) {
            return new AppliedSubscriptionDiscount(null, null, "", null, 0.0, 0.0, code);
        }

        BillDAO.SubscriptionInvoiceContext toInvoiceContext() {
            if (enrollmentId == null || planId == null || totalSavings <= 0.0) {
                return null;
            }
            return new BillDAO.SubscriptionInvoiceContext(
                    enrollmentId,
                    planId,
                    discountPercent,
                    totalSavings,
                    approvalReference);
        }
    }
}
