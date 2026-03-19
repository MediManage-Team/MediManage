package org.example.MediManage.storage.inventory;

import org.example.MediManage.model.Medicine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class InMemoryMedicineStore implements MedicineStore {
    private final Map<Integer, Medicine> medicines = new ConcurrentHashMap<>();
    private final AtomicInteger medicineIdSequence = new AtomicInteger(1);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public InMemoryMedicineStore() {
    }

    public InMemoryMedicineStore(List<Medicine> seedMedicines) {
        if (seedMedicines == null || seedMedicines.isEmpty()) {
            return;
        }

        lock.writeLock().lock();
        try {
            int maxId = 0;
            for (Medicine medicine : seedMedicines) {
                Medicine copy = copyMedicine(medicine);
                medicines.put(copy.getId(), copy);
                maxId = Math.max(maxId, copy.getId());
            }
            medicineIdSequence.set(Math.max(1, maxId + 1));
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<Medicine> getAllMedicines() {
        return sortedSnapshot(null);
    }

    @Override
    public Medicine getMedicineById(int medicineId) {
        lock.readLock().lock();
        try {
            Medicine medicine = medicines.get(medicineId);
            return medicine == null ? null : copyMedicine(medicine);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Medicine> getMedicinesPage(int offset, int limit) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = limit <= 0 ? 50 : limit;

        List<Medicine> sorted = sortedSnapshot(null);
        if (safeOffset >= sorted.size()) {
            return List.of();
        }
        int toIndex = Math.min(sorted.size(), safeOffset + safeLimit);
        return new ArrayList<>(sorted.subList(safeOffset, toIndex));
    }

    @Override
    public int countActiveMedicines() {
        lock.readLock().lock();
        try {
            return medicines.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Medicine> searchMedicines(String keyword, int offset, int limit) {
        String safeKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        int safeOffset = Math.max(0, offset);
        int safeLimit = limit <= 0 ? 50 : limit;

        List<Medicine> filtered = sortedSnapshot(safeKeyword.isEmpty() ? null : safeKeyword);
        if (safeOffset >= filtered.size()) {
            return List.of();
        }
        int toIndex = Math.min(filtered.size(), safeOffset + safeLimit);
        return new ArrayList<>(filtered.subList(safeOffset, toIndex));
    }

    @Override
    public int countMedicines(String keyword) {
        String safeKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        if (safeKeyword.isEmpty()) {
            return countActiveMedicines();
        }

        lock.readLock().lock();
        try {
            int count = 0;
            for (Medicine medicine : medicines.values()) {
                if (matchesKeyword(medicine, safeKeyword)) {
                    count++;
                }
            }
            return count;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int addMedicine(String name, String genericName, String company, String expiry, double price,
            int initialStock, double purchasePrice, int reorderThreshold) {
        lock.writeLock().lock();
        try {
            int nextId = medicineIdSequence.getAndIncrement();
            Medicine medicine = new Medicine(
                    nextId,
                    name,
                    genericName,
                    company,
                    expiry,
                    initialStock,
                    price,
                    purchasePrice,
                    reorderThreshold,
                    "");
            medicines.put(nextId, medicine);
            return nextId;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void updateMedicine(Medicine medicine, int reorderThreshold) {
        if (medicine == null || medicine.getId() <= 0) {
            return;
        }

        lock.writeLock().lock();
        try {
            if (!medicines.containsKey(medicine.getId())) {
                return;
            }
            medicine.setReorderThreshold(reorderThreshold);
            medicines.put(medicine.getId(), copyMedicine(medicine));
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void updateBarcode(int medicineId, String barcode) {
        lock.writeLock().lock();
        try {
            Medicine existing = medicines.get(medicineId);
            if (existing == null) {
                return;
            }
            existing.setBarcode(barcode);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void updateStock(int medicineId, int newQuantity) {
        lock.writeLock().lock();
        try {
            Medicine existing = medicines.get(medicineId);
            if (existing == null) {
                return;
            }
            existing.setStock(newQuantity);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void deleteMedicine(int medicineId) {
        lock.writeLock().lock();
        try {
            medicines.remove(medicineId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private List<Medicine> sortedSnapshot(String keyword) {
        lock.readLock().lock();
        try {
            return medicines.values().stream()
                    .filter(medicine -> keyword == null || matchesKeyword(medicine, keyword))
                    .map(this::copyMedicine)
                    .sorted(Comparator.comparing(
                            medicine -> medicine.getName() == null ? "" : medicine.getName(),
                            String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    private boolean matchesKeyword(Medicine medicine, String lowerKeyword) {
        String name = medicine.getName() == null ? "" : medicine.getName().toLowerCase();
        String generic = medicine.getGenericName() == null ? "" : medicine.getGenericName().toLowerCase();
        String company = medicine.getCompany() == null ? "" : medicine.getCompany().toLowerCase();
        return name.contains(lowerKeyword) || generic.contains(lowerKeyword) || company.contains(lowerKeyword);
    }

    private Medicine copyMedicine(Medicine source) {
        return new Medicine(
                source.getId(),
                source.getName(),
                source.getGenericName(),
                source.getCompany(),
                source.getExpiry(),
                source.getStock(),
                source.getPrice(),
                source.getPurchasePrice(),
                source.getReorderThreshold(),
                source.getBarcode());
    }
}
