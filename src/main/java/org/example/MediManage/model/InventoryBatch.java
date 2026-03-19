package org.example.MediManage.model;

public record InventoryBatch(
        int batchId,
        int medicineId,
        String medicineName,
        String company,
        String supplierName,
        String batchNumber,
        String batchBarcode,
        String expiryDate,
        Integer daysToExpiry,
        int expirySequence,
        String purchaseDate,
        double unitCost,
        double sellingPrice,
        int initialQuantity,
        int availableQuantity,
        String createdAt) {
}
