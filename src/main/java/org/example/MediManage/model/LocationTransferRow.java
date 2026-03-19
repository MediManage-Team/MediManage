package org.example.MediManage.model;

public record LocationTransferRow(
        int transferId,
        int medicineId,
        String medicineName,
        String fromLocationName,
        String toLocationName,
        int quantity,
        String status,
        String requestedAt,
        String completedAt,
        String requestedByUsername) {
}
