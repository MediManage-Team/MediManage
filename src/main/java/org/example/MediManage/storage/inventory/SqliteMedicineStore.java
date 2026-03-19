package org.example.MediManage.storage.inventory;

import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.model.Medicine;

import java.util.List;

public class SqliteMedicineStore implements MedicineStore {
    private final MedicineDAO medicineDAO;

    public SqliteMedicineStore() {
        this(new MedicineDAO());
    }

    public SqliteMedicineStore(MedicineDAO medicineDAO) {
        this.medicineDAO = medicineDAO;
    }

    @Override
    public List<Medicine> getAllMedicines() {
        return medicineDAO.getAllMedicines();
    }

    @Override
    public Medicine getMedicineById(int medicineId) {
        return medicineDAO.getMedicineById(medicineId);
    }

    @Override
    public List<Medicine> getMedicinesPage(int offset, int limit) {
        return medicineDAO.getMedicinesPage(offset, limit);
    }

    @Override
    public int countActiveMedicines() {
        return medicineDAO.countActiveMedicines();
    }

    @Override
    public List<Medicine> searchMedicines(String keyword, int offset, int limit) {
        return medicineDAO.searchMedicines(keyword, offset, limit);
    }

    @Override
    public int countMedicines(String keyword) {
        return medicineDAO.countMedicines(keyword);
    }

    @Override
    public int addMedicine(String name, String genericName, String company, String expiry, double price,
            int initialStock, double purchasePrice, int reorderThreshold) {
        return medicineDAO.addMedicine(name, genericName, company, expiry, price, initialStock, purchasePrice,
                reorderThreshold);
    }

    @Override
    public void updateMedicine(Medicine medicine, int reorderThreshold) {
        medicineDAO.updateMedicine(medicine, reorderThreshold);
    }

    @Override
    public void updateBarcode(int medicineId, String barcode) {
        try {
            medicineDAO.updateBarcode(medicineId, barcode);
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to update barcode", e);
        }
    }

    @Override
    public void updateStock(int medicineId, int newQuantity) {
        medicineDAO.updateStock(medicineId, newQuantity);
    }

    @Override
    public void deleteMedicine(int medicineId) {
        medicineDAO.deleteMedicine(medicineId);
    }
}
