package org.example.MediManage.service;

import org.example.MediManage.model.Medicine;
import org.example.MediManage.service.ai.AIAssistantService;
import org.example.MediManage.storage.StorageFactory;
import org.example.MediManage.storage.inventory.MedicineStore;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class InventoryService {
    public record RestockPreparation(boolean requiresAi, String message, String snapshot) {
    }

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int RESTOCK_ANALYSIS_CAP = 500;
    private static final int LOW_STOCK_THRESHOLD = 20;

    private final MedicineStore medicineStore;
    private final AIAssistantService aiService;

    public InventoryService() {
        this(StorageFactory.medicineStore(), new AIAssistantService());
    }

    InventoryService(MedicineStore medicineStore, AIAssistantService aiService) {
        this.medicineStore = medicineStore;
        this.aiService = aiService;
    }

    public List<Medicine> loadInventory() {
        return medicineStore.getAllMedicines();
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

    public void addMedicine(String name, String company, LocalDate expiryDate, double price, int stock) {
        medicineStore.addMedicine(name, "", company, expiryDate.toString(), price, stock);
    }

    public void updateMedicine(Medicine selectedMedicine, String name, String company, LocalDate expiryDate, double price,
            int stock) {
        selectedMedicine.setName(name);
        selectedMedicine.setCompany(company);
        selectedMedicine.setExpiry(expiryDate.toString());
        selectedMedicine.setPrice(price);

        medicineStore.updateMedicine(selectedMedicine);
        medicineStore.updateStock(selectedMedicine.getId(), stock);
    }

    public void deleteMedicine(int medicineId) {
        medicineStore.deleteMedicine(medicineId);
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
}
