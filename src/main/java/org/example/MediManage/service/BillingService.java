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
import org.example.MediManage.service.subscription.SubscriptionDiscountEngine;
import org.example.MediManage.service.subscription.SubscriptionEligibilityCode;
import org.example.MediManage.service.subscription.SubscriptionEligibilityResult;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
                new SubscriptionApprovalService(subscriptionDAO));
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
        return aiOrchestrator.processQuery(AIPromptCatalog.checkoutCareProtocolPrompt(billItems), true, false);
    }

    public CompletableFuture<String> generateDetailedCareProtocol(List<BillItem> billItems) {
        return aiOrchestrator.cloudQuery(AIPromptCatalog.detailedCareProtocolPrompt(billItems));
    }

    public String getCloudProviderInfo() {
        return cloudService.getProviderName() + " — " + cloudService.getActiveModel();
    }

    public CheckoutResult completeCheckout(
            List<BillItem> billItems,
            Customer selectedCustomer,
            int userId,
            String paymentMode,
            String careProtocol) throws SQLException, JRException {
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
        String pdfPath = "Invoice_" + billId + ".pdf";
        reportService.generateInvoicePDF(
                billItems,
                total,
                customerName,
                pdfPath,
                careProtocol,
                appliedDiscount.planName(),
                appliedDiscount.totalSavings(),
                appliedDiscount.discountPercent());

        return new CheckoutResult(
                billId,
                pdfPath,
                total,
                customerName,
                appliedDiscount.planName(),
                appliedDiscount.totalSavings());
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
        return FeatureFlags.isEnabled(FeatureFlag.SUBSCRIPTION_COMMERCE)
                && FeatureFlags.isEnabled(FeatureFlag.SUBSCRIPTION_APPROVALS)
                && FeatureFlags.isEnabled(FeatureFlag.SUBSCRIPTION_DISCOUNT_OVERRIDES);
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

    private AppliedSubscriptionDiscount applySubscriptionDiscountIfEligible(
            List<BillItem> billItems,
            Customer selectedCustomer) {
        if (!FeatureFlags.isEnabled(FeatureFlag.SUBSCRIPTION_COMMERCE)) {
            return AppliedSubscriptionDiscount.none(SubscriptionEligibilityCode.FEATURE_DISABLED);
        }
        if (selectedCustomer == null) {
            return AppliedSubscriptionDiscount.none(SubscriptionEligibilityCode.NO_CUSTOMER_SELECTED);
        }
        if (billItems == null || billItems.isEmpty()) {
            return AppliedSubscriptionDiscount.none(SubscriptionEligibilityCode.INVALID_SUBSCRIPTION_STATE);
        }

        SubscriptionEligibilityResult eligibilityResult = subscriptionService
                .evaluateEligibility(selectedCustomer.getCustomerId());
        if (!eligibilityResult.eligible()) {
            return AppliedSubscriptionDiscount.none(eligibilityResult.code());
        }

        Optional<SubscriptionDAO.ApplicableSubscription> applicableOpt = subscriptionDAO
                .findApplicableSubscription(selectedCustomer.getCustomerId());
        if (applicableOpt.isEmpty()) {
            return AppliedSubscriptionDiscount.none(SubscriptionEligibilityCode.INVALID_SUBSCRIPTION_STATE);
        }

        SubscriptionDAO.ApplicableSubscription applicable = applicableOpt.get();
        Map<Integer, SubscriptionDiscountEngine.DiscountRule> medicineRules = subscriptionDAO.loadMedicineRules(
                applicable.planId());

        SubscriptionDiscountEngine.EvaluationContext context = new SubscriptionDiscountEngine.EvaluationContext(
                new SubscriptionDiscountEngine.PlanPolicy(
                        true,
                        applicable.defaultDiscountPercent(),
                        applicable.maxDiscountPercent(),
                        applicable.minimumMarginPercent()),
                medicineRules,
                Map.of(),
                Map.of(),
                Map.of());

        SubscriptionDiscountEngine.EvaluationResult evaluation = subscriptionDiscountEngine.evaluate(billItems, context);
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

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
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
