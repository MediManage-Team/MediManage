package org.example.MediManage.model;

import javafx.beans.property.*;

public class PurchaseOrder {
    private final IntegerProperty poId = new SimpleIntegerProperty();
    private final IntegerProperty supplierId = new SimpleIntegerProperty();
    private final StringProperty supplierName = new SimpleStringProperty(); // Helper for UI
    private final StringProperty orderDate = new SimpleStringProperty();
    private final StringProperty expectedDelivery = new SimpleStringProperty();
    private final StringProperty status = new SimpleStringProperty();
    private final DoubleProperty totalAmount = new SimpleDoubleProperty();
    private final StringProperty notes = new SimpleStringProperty();
    private final IntegerProperty createdByUserId = new SimpleIntegerProperty();
    private final StringProperty updatedAt = new SimpleStringProperty();

    public PurchaseOrder() {}

    public int getPoId() { return poId.get(); }
    public IntegerProperty poIdProperty() { return poId; }
    public void setPoId(int poId) { this.poId.set(poId); }

    public int getSupplierId() { return supplierId.get(); }
    public IntegerProperty supplierIdProperty() { return supplierId; }
    public void setSupplierId(int supplierId) { this.supplierId.set(supplierId); }

    public String getSupplierName() { return supplierName.get(); }
    public StringProperty supplierNameProperty() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName.set(supplierName); }

    public String getOrderDate() { return orderDate.get(); }
    public StringProperty orderDateProperty() { return orderDate; }
    public void setOrderDate(String orderDate) { this.orderDate.set(orderDate); }

    public String getExpectedDelivery() { return expectedDelivery.get(); }
    public StringProperty expectedDeliveryProperty() { return expectedDelivery; }
    public void setExpectedDelivery(String expectedDelivery) { this.expectedDelivery.set(expectedDelivery); }

    public String getStatus() { return status.get(); }
    public StringProperty statusProperty() { return status; }
    public void setStatus(String status) { this.status.set(status); }

    public double getTotalAmount() { return totalAmount.get(); }
    public DoubleProperty totalAmountProperty() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount.set(totalAmount); }

    public String getNotes() { return notes.get(); }
    public StringProperty notesProperty() { return notes; }
    public void setNotes(String notes) { this.notes.set(notes); }

    public int getCreatedByUserId() { return createdByUserId.get(); }
    public IntegerProperty createdByUserIdProperty() { return createdByUserId; }
    public void setCreatedByUserId(int createdByUserId) { this.createdByUserId.set(createdByUserId); }

    public String getUpdatedAt() { return updatedAt.get(); }
    public StringProperty updatedAtProperty() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt.set(updatedAt); }
}
