package org.example.MediManage.service;

import org.example.MediManage.dao.InventoryBatchDAO;
import org.example.MediManage.dao.InventoryAdjustmentDAO;
import org.example.MediManage.dao.AuditLogDAO;
import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.model.InventoryBatch;
import org.example.MediManage.model.InventoryAdjustment;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.service.ai.AIAssistantService;
import org.example.MediManage.storage.StorageFactory;
import org.example.MediManage.storage.inventory.MedicineStore;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.example.MediManage.util.UserSession;
import org.json.JSONObject;

public class InventoryService {
    public record RestockPreparation(boolean requiresAi, String message, String snapshot) {
    }

    private static final int DEFAULT_PAGE_SIZE = 200;
    private static final int MAX_PAGE_SIZE = 500;
    private static final int RESTOCK_ANALYSIS_CAP = 500;
    private static final int LOW_STOCK_THRESHOLD = 20;

    private final MedicineStore medicineStore;
    private final AIAssistantService aiService;
    private final InventoryAdjustmentDAO inventoryAdjustmentDAO;
    private final MedicineDAO medicineDAO;
    private final InventoryBatchDAO inventoryBatchDAO;
    private final AuditLogDAO auditLogDAO;

    public InventoryService() {
        this(
                StorageFactory.medicineStore(),
                new AIAssistantService(),
                new InventoryAdjustmentDAO(),
                new MedicineDAO(),
                new InventoryBatchDAO(),
                new AuditLogDAO());
    }

    InventoryService(MedicineStore medicineStore, AIAssistantService aiService) {
        this(medicineStore, aiService, new InventoryAdjustmentDAO(), new MedicineDAO(), new InventoryBatchDAO(), new AuditLogDAO());
    }

    InventoryService(
            MedicineStore medicineStore,
            AIAssistantService aiService,
            InventoryAdjustmentDAO inventoryAdjustmentDAO,
            MedicineDAO medicineDAO,
            InventoryBatchDAO inventoryBatchDAO,
            AuditLogDAO auditLogDAO) {
        this.medicineStore = medicineStore;
        this.aiService = aiService;
        this.inventoryAdjustmentDAO = inventoryAdjustmentDAO;
        this.medicineDAO = medicineDAO;
        this.inventoryBatchDAO = inventoryBatchDAO;
        this.auditLogDAO = auditLogDAO;
    }

    public List<Medicine> loadInventory() {
        return medicineStore.getAllMedicines();
    }

    public Medicine loadMedicine(int medicineId) {
        return medicineStore.getMedicineById(medicineId);
    }

    public List<Medicine> loadInventoryPage(String query, int pageIndex, int pageSize) {
        int safePageIndex = Math.max(0, pageIndex);
        int safePageSize = normalizePageSize(pageSize);
        int offset = safePageIndex * safePageSize;
        String safeQuery = query == null ? "" : query.trim();

        if (safeQuery.isEmpty()) {
            return medicineStore.getMedicinesPage(offset, safePageSize);
        }
        return medicineStore.searchMedicines(safeQuery, offset, safePageSize);
    }

    public int countInventory(String query) {
        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.isEmpty()) {
            return medicineStore.countActiveMedicines();
        }
        return medicineStore.countMedicines(safeQuery);
    }

    public int defaultPageSize() {
        return DEFAULT_PAGE_SIZE;
    }

    public List<Medicine> loadRestockAnalysisSnapshot() {
        return medicineStore.getMedicinesPage(0, RESTOCK_ANALYSIS_CAP);
    }

    public void addMedicine(String name, String genericName, String company, LocalDate expiryDate, double price, int stock,
            double purchasePrice, int reorderThreshold, String barcode) {
        int medicineId = medicineStore.addMedicine(name, genericName, company, expiryDate.toString(), price, stock, purchasePrice,
                reorderThreshold);
        if (medicineId > 0) {
            try (Connection conn = org.example.MediManage.util.DatabaseUtil.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    inventoryBatchDAO.setTargetStock(
                            conn,
                            medicineId,
                            Math.max(0, stock),
                            purchasePrice,
                            price,
                            expiryDate == null ? null : expiryDate.toString(),
                            null);
                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to initialize medicine batch stock: " + e.getMessage(), e);
            }
        }
        if (medicineId > 0 && barcode != null && !barcode.isBlank()) {
            medicineStore.updateBarcode(medicineId, barcode.trim());
        }
        logInventoryEvent(
                "MEDICINE_CREATED",
                medicineId,
                "Created medicine " + name,
                new JSONObject()
                        .put("medicineName", name)
                        .put("genericName", genericName == null ? "" : genericName)
                        .put("company", company == null ? "" : company)
                        .put("expiryDate", expiryDate == null ? "" : expiryDate.toString())
                        .put("price", price)
                        .put("purchasePrice", purchasePrice)
                        .put("stock", stock)
                        .put("reorderThreshold", reorderThreshold)
                        .put("barcode", barcode == null ? "" : barcode));
    }

    public void updateMedicine(Medicine selectedMedicine, String name, String genericName, String company, LocalDate expiryDate,
            double price,
            int stock, double purchasePrice, int reorderThreshold, String barcode) {
        String previousName = selectedMedicine.getName();
        String previousCompany = selectedMedicine.getCompany();
        String previousExpiry = selectedMedicine.getExpiry();
        double previousPrice = selectedMedicine.getPrice();
        double previousPurchasePrice = selectedMedicine.getPurchasePrice();
        int previousStock = selectedMedicine.getStock();
        int previousReorderThreshold = selectedMedicine.getReorderThreshold();
        String previousBarcode = selectedMedicine.getBarcode();
        int targetStock = Math.max(0, stock);
        selectedMedicine.setName(name);
        selectedMedicine.setGenericName(genericName);
        selectedMedicine.setCompany(company);
        selectedMedicine.setExpiry(expiryDate.toString());
        selectedMedicine.setPrice(price);
        selectedMedicine.setPurchasePrice(purchasePrice);
        selectedMedicine.setReorderThreshold(reorderThreshold);
        selectedMedicine.setBarcode(barcode);

        medicineStore.updateMedicine(selectedMedicine, reorderThreshold);
        try (Connection conn = org.example.MediManage.util.DatabaseUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                inventoryBatchDAO.setTargetStock(
                        conn,
                        selectedMedicine.getId(),
                        targetStock,
                        purchasePrice,
                        price,
                        expiryDate.toString(),
                        null);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to reconcile stock batches: " + e.getMessage(), e);
        }
        logInventoryEvent(
                "MEDICINE_UPDATED",
                selectedMedicine.getId(),
                "Updated medicine " + name,
                new JSONObject()
                        .put("before", new JSONObject()
                                .put("name", previousName)
                                .put("company", previousCompany)
                                .put("expiryDate", previousExpiry == null ? "" : previousExpiry)
                                .put("price", previousPrice)
                                .put("purchasePrice", previousPurchasePrice)
                                .put("stock", previousStock)
                                .put("reorderThreshold", previousReorderThreshold)
                                .put("barcode", previousBarcode == null ? "" : previousBarcode))
                        .put("after", new JSONObject()
                                .put("name", name)
                                .put("genericName", genericName == null ? "" : genericName)
                                .put("company", company == null ? "" : company)
                                .put("expiryDate", expiryDate == null ? "" : expiryDate.toString())
                                .put("price", price)
                                .put("purchasePrice", purchasePrice)
                                .put("stock", targetStock)
                                .put("reorderThreshold", reorderThreshold)
                                .put("barcode", barcode == null ? "" : barcode)));
    }

    public void updateBarcode(int medicineId, String barcode) {
        medicineStore.updateBarcode(medicineId, barcode == null ? "" : barcode.trim());
        logInventoryEvent(
                "BARCODE_UPDATED",
                medicineId,
                "Updated barcode for medicine #" + medicineId,
                new JSONObject().put("barcode", barcode == null ? "" : barcode.trim()));
    }

    public boolean isBarcodeAssignedToAnotherMedicine(int medicineId, String barcode) {
        if (barcode == null || barcode.isBlank()) {
            return false;
        }
        Medicine existing = medicineDAO.findByBarcode(barcode.trim());
        return existing != null && existing.getId() != medicineId;
    }

    public String generateBarcode(int medicineId) {
        return new BarcodeService().generateMedicineBarcode(medicineId);
    }

    public void deleteMedicine(int medicineId) {
        medicineStore.deleteMedicine(medicineId);
        logInventoryEvent(
                "MEDICINE_DELETED",
                medicineId,
                "Soft-deleted medicine #" + medicineId,
                new JSONObject().put("medicineId", medicineId));
    }

    public void recordAdjustment(
            Medicine medicine,
            String adjustmentType,
            int quantity,
            double unitPrice,
            String rootCauseTag,
            String notes,
            Integer createdByUserId) throws SQLException {
        if (medicine == null) {
            throw new SQLException("Select a medicine before recording an adjustment.");
        }
        inventoryAdjustmentDAO.recordAdjustment(
                medicine.getId(),
                adjustmentType,
                quantity,
                unitPrice,
                rootCauseTag,
                notes,
                createdByUserId);
        logInventoryEvent(
                "INVENTORY_ADJUSTMENT",
                medicine.getId(),
                "Recorded " + adjustmentType + " for " + medicine.getName(),
                new JSONObject()
                        .put("medicineId", medicine.getId())
                        .put("medicineName", medicine.getName())
                        .put("adjustmentType", adjustmentType == null ? "" : adjustmentType)
                        .put("quantity", quantity)
                        .put("unitPrice", unitPrice)
                        .put("rootCauseTag", rootCauseTag == null ? "" : rootCauseTag)
                        .put("notes", notes == null ? "" : notes));
    }

    public List<InventoryAdjustment> loadRecentAdjustments(int limit) throws SQLException {
        return inventoryAdjustmentDAO.getRecentAdjustments(limit);
    }

    public List<InventoryBatch> loadMedicineBatches(int medicineId) throws SQLException {
        return inventoryBatchDAO.getAvailableBatchesForMedicine(medicineId);
    }

    public List<InventoryBatch> loadExpiringBatches(int days, int limit) throws SQLException {
        LocalDate today = LocalDate.now();
        return inventoryBatchDAO.getExpiringBatches(today, today.plusDays(Math.max(0, days)), limit);
    }

    public List<InventoryBatchDAO.ExpiryLossExposureRow> loadExpiryLossExposure(int days, int limit) throws SQLException {
        return inventoryBatchDAO.getExpiryLossExposure(LocalDate.now().plusDays(Math.max(0, days)), limit);
    }

    public boolean matchesSearch(Medicine medicine, String query) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        String lowerCaseFilter = query.toLowerCase();
        return medicine.getName().toLowerCase().contains(lowerCaseFilter)
                || medicine.getCompany().toLowerCase().contains(lowerCaseFilter);
    }

    public RestockPreparation prepareRestock(List<Medicine> medicines) {
        if (medicines == null || medicines.isEmpty()) {
            return new RestockPreparation(false, "No inventory data loaded.", "");
        }

        String snapshot = medicines.stream()
                .filter(m -> m.getStock() < LOW_STOCK_THRESHOLD)
                .map(m -> m.getName() + " (" + m.getCompany() + ") — Stock: " + m.getStock() + ", Price: ₹"
                        + m.getPrice())
                .collect(Collectors.joining("\n"));

        if (snapshot.isEmpty()) {
            return new RestockPreparation(false, "All items have adequate stock (20+).", "");
        }

        return new RestockPreparation(true, "Analyzing inventory with AI...", snapshot);
    }

    public CompletableFuture<String> suggestRestock(String snapshot) {
        return aiService.suggestRestock(snapshot);
    }

    private int normalizePageSize(int pageSize) {
        if (pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private void logInventoryEvent(String eventType, int medicineId, String summary, JSONObject details) {
        try {
            Integer actorUserId = UserSession.getInstance().getUser() == null
                    ? null
                    : UserSession.getInstance().getUser().getId();
            auditLogDAO.logEvent(
                    actorUserId,
                    eventType,
                    "MEDICINE",
                    medicineId,
                    summary,
                    details == null ? "" : details.toString());
        } catch (Exception ignored) {
        }
    }
}
