package org.example.MediManage.model;

public record LocationStockRow(
        int locationStockId,
        int medicineId,
        String medicineName,
        String genericName,
        String company,
        int quantity,
        int minStock,
        String updatedAt) {
}
