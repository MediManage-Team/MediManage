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
import java.util.ArrayList;
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

    public record CheckoutResult(int billId, String pdfPath, double total, String customerName) {
    }

    private final MedicineDAO medicineDAO;
    private final CustomerDAO customerDAO;
    private final BillDAO billDAO;
    private final ReportService reportService;
    private final AIOrchestrator aiOrchestrator;
    private final CloudAIService cloudService;

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

        double total = calculateTotal(billItems);
        Integer customerId = selectedCustomer != null ? selectedCustomer.getCustomerId() : null;
        String customerName = selectedCustomer != null ? selectedCustomer.getName() : "Walk-in";

        int billId = billDAO.generateInvoice(total, billItems, customerId, userId, paymentMode);
        DashboardKpiService.invalidateSalesMetrics();
        String pdfPath = "Invoice_" + billId + ".pdf";
        reportService.generateInvoicePDF(billItems, total, customerName, pdfPath, careProtocol);

        return new CheckoutResult(billId, pdfPath, total, customerName);
    }

    public List<BillItem> snapshotItems(List<BillItem> billItems) {
        return billItems == null ? List.of() : new ArrayList<>(billItems);
    }
}
