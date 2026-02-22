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
    public void addMedicine(String name, String genericName, String company, String expiry, double price, int initialStock) {
        medicineDAO.addMedicine(name, genericName, company, expiry, price, initialStock);
    }

    @Override
    public void updateMedicine(Medicine medicine) {
        medicineDAO.updateMedicine(medicine);
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
