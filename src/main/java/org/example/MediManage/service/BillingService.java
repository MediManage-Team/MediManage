package org.example.MediManage.service;

import net.sf.jasperreports.engine.JRException;
import org.example.MediManage.dao.BillDAO;
import org.example.MediManage.dao.CustomerDAO;
import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.model.BillItem;
import org.example.MediManage.model.Customer;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.service.ai.AIOrchestrator;
import org.example.MediManage.service.ai.AIPromptCatalog;
import org.example.MediManage.service.ai.AIServiceProvider;
import org.example.MediManage.service.ai.CloudAIService;

import java.sql.SQLException;
import java.util.List;
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
            String customerName) {
    }

    private final MedicineDAO medicineDAO;
    private final CustomerDAO customerDAO;
    private final BillDAO billDAO;
    private final ReportService reportService;
    private final AIOrchestrator aiOrchestrator;
    private final CloudAIService cloudService;
    private final BillingCareProtocolFallbackRulesEngine careProtocolFallbackRulesEngine;

    public BillingService() {
        this(
                new MedicineDAO(),
                new CustomerDAO(),
                new BillDAO(),
                new ReportService(),
                AIServiceProvider.get().getOrchestrator(),
                AIServiceProvider.get().getCloudService());
    }

    BillingService(
            MedicineDAO medicineDAO,
            CustomerDAO customerDAO,
            BillDAO billDAO,
            ReportService reportService,
            AIOrchestrator aiOrchestrator,
            CloudAIService cloudService) {
        this.medicineDAO = medicineDAO;
        this.customerDAO = customerDAO;
        this.billDAO = billDAO;
        this.reportService = reportService;
        this.aiOrchestrator = aiOrchestrator;
        this.cloudService = cloudService;
        this.careProtocolFallbackRulesEngine = new BillingCareProtocolFallbackRulesEngine();
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
            item.setQty(item.getQty() + qty);
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

    // ═══════════════════════════════════════════════
    // CHECKOUT
    // ═══════════════════════════════════════════════

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

        System.out.println("=== CHECKOUT HIT ===");
        System.out.println("RAW CARE PROTOCOL: [" + careProtocol + "]");

        double total = calculateTotal(billItems);
        Integer customerId = selectedCustomer != null ? selectedCustomer.getCustomerId() : null;
        String customerName = selectedCustomer != null ? selectedCustomer.getName() : "Walk-in";

        int billId = billDAO.generateInvoice(total, billItems, customerId, userId, paymentMode);
        DashboardKpiService.invalidateSalesMetrics();

        // Save AI protocol to the database so the Dashboard History can rebuild the invoice PDFs later
        if (careProtocol != null && !careProtocol.trim().isEmpty()) {
            billDAO.saveAICareProtocol(billId, careProtocol);
        }

        String pdfPath = "Invoice_" + billId + ".pdf";

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

        reportService.generateInvoicePDF(billItems, total, customerName, pdfPath, plainCareProtocol);

        return new CheckoutResult(billId, pdfPath, total, customerName);
    }

    // ═══════════════════════════════════════════════
    // AI CARE PROTOCOL
    // ═══════════════════════════════════════════════

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
        return billItems.stream()
                .map(i -> new BillItem(i.getMedicineId(), i.getName(), i.getExpiry(), i.getQty(), i.getPrice(),
                        i.getGst()))
                .toList();
    }
}
