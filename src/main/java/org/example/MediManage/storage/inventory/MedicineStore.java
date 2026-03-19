package org.example.MediManage.storage.inventory;

import org.example.MediManage.model.Medicine;

import java.util.List;

public interface MedicineStore {
    List<Medicine> getAllMedicines();

    Medicine getMedicineById(int medicineId);

    List<Medicine> getMedicinesPage(int offset, int limit);

    int countActiveMedicines();

    List<Medicine> searchMedicines(String keyword, int offset, int limit);

    int countMedicines(String keyword);

    int addMedicine(String name, String genericName, String company, String expiry, double price, int initialStock,
            double purchasePrice, int reorderThreshold);

    void updateMedicine(Medicine medicine, int reorderThreshold);

    void updateBarcode(int medicineId, String barcode);

    void updateStock(int medicineId, int newQuantity);

    void deleteMedicine(int medicineId);
}
