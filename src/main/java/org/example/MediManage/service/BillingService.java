package org.example.MediManage.service;

import org.example.MediManage.dao.BillDAO;
import org.example.MediManage.dao.CustomerDAO;
import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.model.BillItem;
import org.example.MediManage.model.Customer;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.model.PaymentSplit;
import org.example.MediManage.service.ai.AIOrchestrator;
import org.example.MediManage.service.ai.AIServiceProvider;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.nio.file.Path;

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
            boolean pdfAvailable,
            String pdfErrorMessage) {
    }

    private final MedicineDAO medicineDAO;
    private final CustomerDAO customerDAO;
    private final BillDAO billDAO;
    private final ReportService reportService;
    private final AIOrchestrator aiOrchestrator;
    private final BillingCareProtocolFallbackRulesEngine careProtocolFallbackRulesEngine;
    private final LoyaltyService loyaltyService;

    public BillingService() {
        this(
                new MedicineDAO(),
                new CustomerDAO(),
                new BillDAO(),
                new ReportService(),
                AIServiceProvider.get().getOrchestrator());
    }

    BillingService(
            MedicineDAO medicineDAO,
            CustomerDAO customerDAO,
            BillDAO billDAO,
            ReportService reportService,
            AIOrchestrator aiOrchestrator) {
        this.medicineDAO = medicineDAO;
        this.customerDAO = customerDAO;
        this.billDAO = billDAO;
        this.reportService = reportService;
        this.aiOrchestrator = aiOrchestrator;
        this.careProtocolFallbackRulesEngine = new BillingCareProtocolFallbackRulesEngine();
        this.loyaltyService = new LoyaltyService();
    }

    // ═══════════════════════════════════════════════
    // CORE BILLING
    // ═══════════════════════════════════════════════

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
            int remainingStock = Math.max(0, medicine.getStock() - item.getQty());
            if (qty > remainingStock) {
                return new AddItemResult(AddItemStatus.OUT_OF_STOCK, false, remainingStock);
            }
            double gstIncrement = (medicine.getPrice() * qty) * 0.18;
            item.setQty(item.getQty() + qty);
            item.setGst(item.getGst() + gstIncrement);
            item.setTotal(item.getTotal() + (medicine.getPrice() * qty) + gstIncrement);
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

    public double calculateTotal(List<BillItem> billItems, double discountPercent) {
        double total = calculateTotal(billItems);
        if (discountPercent <= 0) {
            return total;
        }
        return roundCurrency(total * (1.0 - discountPercent / 100.0));
    }

    // ═══════════════════════════════════════════════
    // CHECKOUT
    // ═══════════════════════════════════════════════

    public CheckoutResult completeCheckout(
            List<BillItem> billItems,
            Customer selectedCustomer,
            int userId,
            List<PaymentSplit> paymentSplits,
            String paymentMode,
            String careProtocol,
            boolean redeemLoyalty) throws SQLException {
        if (billItems == null || billItems.isEmpty()) {
            throw new IllegalArgumentException("No bill items found for checkout.");
        }

        if (containsCreditPayment(paymentMode, paymentSplits) && selectedCustomer == null) {
            throw new IllegalArgumentException("Credit requires a selected customer.");
        }

        System.out.println("=== CHECKOUT HIT ===");
        System.out.println("RAW CARE PROTOCOL: [" + careProtocol + "]");

        double total = calculateTotal(billItems);
        Integer customerId = selectedCustomer != null ? selectedCustomer.getCustomerId() : null;
        String customerName = selectedCustomer != null ? selectedCustomer.getName() : "Walk-in";
        int pointsToAward = customerId != null ? loyaltyService.calculateAwardPoints(total) : 0;
        int pointsToRedeem = redeemLoyalty && customerId != null ? loyaltyService.getRedemptionThreshold() : 0;

        int billId = billDAO.generateInvoice(
                total,
                billItems,
                customerId,
                userId,
                paymentSplits,
                paymentMode,
                pointsToRedeem,
                pointsToAward);
        DashboardKpiService.invalidateSalesMetrics();

        // Save AI protocol to the database so the Dashboard History can rebuild the invoice PDFs later
        if (careProtocol != null && !careProtocol.trim().isEmpty()) {
            billDAO.saveAICareProtocol(billId, careProtocol);
        }

        String pdfPath = resolveInvoicePath(billId);

        // JasperReports text fields are simplest and most reliable with plain text,
        // but since we updated JRXML to markup="html", we can convert markdown bold (**text**) to HTML (<b>text</b>)
        String plainCareProtocol = "";
        if (careProtocol != null) {
            plainCareProtocol = careProtocol
                .replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>") // Convert **bold** to <b>bold</b>
                .replaceAll("(?m)^#+\\s+", "<b>")             // Convert headers to open bold tag
                .replaceAll("(?m)^#+\\s+.*$", "$0</b>")       // Close header bold tags at end of line
                .replaceAll("`", "")                          // Remove code blocks
                .trim();
        }

        System.out.println("PLAIN CARE PROTOCOL SIZE: " + plainCareProtocol.length());
        System.out.println("PLAIN CARE PROTOCOL START: [" + (plainCareProtocol.length() > 50 ? plainCareProtocol.substring(0, 50) : plainCareProtocol) + "]");

        try {
            reportService.generateInvoicePDF(billItems, total, customerName, pdfPath, plainCareProtocol);
            return new CheckoutResult(billId, pdfPath, total, customerName, true, "");
        } catch (Exception pdfError) {
            return new CheckoutResult(
                    billId,
                    pdfPath,
                    total,
                    customerName,
                    false,
                    pdfError.getMessage() == null ? "Unknown PDF generation error" : pdfError.getMessage());
        }
    }

    // ═══════════════════════════════════════════════
    // AI CARE PROTOCOL
    // ═══════════════════════════════════════════════

    public CompletableFuture<String> generateCheckoutCareProtocol(List<BillItem> billItems) {
        if (aiOrchestrator == null) {
            return CompletableFuture.completedFuture(
                    fallbackCheckoutCareProtocol(billItems, "AI orchestrator unavailable"));
        }
        try {
            org.json.JSONObject data = new org.json.JSONObject();
            org.json.JSONArray meds = new org.json.JSONArray();
            for (BillItem item : billItems) meds.put(item.getName());
            data.put("medicines", meds);

            return aiOrchestrator.processOrchestration("checkout_protocol", data, "cloud_only", false)
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
        org.json.JSONObject data = new org.json.JSONObject();
        org.json.JSONArray meds = new org.json.JSONArray();
        for (BillItem item : billItems) meds.put(item.getName());
        data.put("medicines", meds);
        return aiOrchestrator.processOrchestration("detailed_protocol", data, "cloud_only", false);
    }

    public String getCloudProviderInfo() {
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(org.example.MediManage.MediManageApplication.class);
        return prefs.get("cloud_provider", "GEMINI") + " — " + prefs.get("cloud_model", "Auto");
    }

    // ═══════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════

    private String fallbackCheckoutCareProtocol(List<BillItem> billItems, String reason) {
        return careProtocolFallbackRulesEngine.build(billItems, reason);
    }

    private boolean isUsableCareProtocol(String value) {
        if (value == null || value.isBlank())
            return false;
        String trimmed = value.trim().toLowerCase();
        if (trimmed.length() < 20)
            return false;
        if (trimmed.startsWith("sorry") || trimmed.startsWith("i cannot")
                || trimmed.startsWith("i'm sorry") || trimmed.startsWith("as an ai"))
            return false;
        return true;
    }

    public List<BillItem> snapshotItems(List<BillItem> billItems) {
        if (billItems == null) {
            return List.of();
        }
        return billItems.stream()
                .map(i -> new BillItem(i.getMedicineId(), i.getName(), i.getExpiry(), i.getQty(), i.getPrice(),
                        i.getGst()))
                .toList();
    }

    public List<BillItem> snapshotItems(List<BillItem> billItems, double discountPercent) {
        List<BillItem> snapshot = snapshotItems(billItems);
        if (snapshot.isEmpty() || discountPercent <= 0) {
            return snapshot;
        }

        double originalTotal = calculateTotal(snapshot);
        double discountedTotal = calculateTotal(snapshot, discountPercent);
        double totalDiscount = roundCurrency(originalTotal - discountedTotal);
        double allocatedDiscount = 0.0;

        for (int i = 0; i < snapshot.size(); i++) {
            BillItem item = snapshot.get(i);
            double itemDiscount;
            if (i == snapshot.size() - 1) {
                itemDiscount = roundCurrency(totalDiscount - allocatedDiscount);
            } else {
                double ratio = originalTotal == 0.0 ? 0.0 : item.getTotal() / originalTotal;
                itemDiscount = roundCurrency(totalDiscount * ratio);
                allocatedDiscount = roundCurrency(allocatedDiscount + itemDiscount);
            }
            item.setTotal(roundCurrency(Math.max(0.0, item.getTotal() - itemDiscount)));
        }

        return snapshot;
    }

    private String resolveInvoicePath(int billId) {
        return Path.of(System.getProperty("user.home"), "MediManage", "invoices", "Invoice_" + billId + ".pdf")
                .toString();
    }

    private boolean containsCreditPayment(String paymentMode, List<PaymentSplit> paymentSplits) {
        if (paymentSplits != null && paymentSplits.stream()
                .anyMatch(split -> split != null && "Credit".equalsIgnoreCase(split.getPaymentMethod()))) {
            return true;
        }
        return "Credit".equalsIgnoreCase(paymentMode);
    }

    private double roundCurrency(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
