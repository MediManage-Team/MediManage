package org.example.MediManage.model;

import javafx.beans.property.*;

public class PurchaseOrderItem {
    private final IntegerProperty poiId = new SimpleIntegerProperty();
    private final IntegerProperty poId = new SimpleIntegerProperty();
    private final IntegerProperty medicineId = new SimpleIntegerProperty();
    private final StringProperty medicineName = new SimpleStringProperty(); // Helper for UI
    private final StringProperty company = new SimpleStringProperty(); // Helper for UI
    private final IntegerProperty orderedQty = new SimpleIntegerProperty();
    private final IntegerProperty receivedQty = new SimpleIntegerProperty();
    private final DoubleProperty unitCost = new SimpleDoubleProperty();

    public PurchaseOrderItem() {}

    public int getPoiId() { return poiId.get(); }
    public IntegerProperty poiIdProperty() { return poiId; }
    public void setPoiId(int poiId) { this.poiId.set(poiId); }

    public int getPoId() { return poId.get(); }
    public IntegerProperty poIdProperty() { return poId; }
    public void setPoId(int poId) { this.poId.set(poId); }

    public int getMedicineId() { return medicineId.get(); }
    public IntegerProperty medicineIdProperty() { return medicineId; }
    public void setMedicineId(int medicineId) { this.medicineId.set(medicineId); }

    public String getMedicineName() { return medicineName.get(); }
    public StringProperty medicineNameProperty() { return medicineName; }
    public void setMedicineName(String medicineName) { this.medicineName.set(medicineName); }

    public String getCompany() { return company.get(); }
    public StringProperty companyProperty() { return company; }
    public void setCompany(String company) { this.company.set(company); }

    public int getOrderedQty() { return orderedQty.get(); }
    public IntegerProperty orderedQtyProperty() { return orderedQty; }
    public void setOrderedQty(int orderedQty) { this.orderedQty.set(orderedQty); }

    public int getReceivedQty() { return receivedQty.get(); }
    public IntegerProperty receivedQtyProperty() { return receivedQty; }
    public void setReceivedQty(int receivedQty) { this.receivedQty.set(receivedQty); }

    public double getUnitCost() { return unitCost.get(); }
    public DoubleProperty unitCostProperty() { return unitCost; }
    public void setUnitCost(double unitCost) { this.unitCost.set(unitCost); }
    
    // Derived property for UI
    public double getTotalCost() { return unitCost.get() * receivedQty.get(); }
    public DoubleProperty totalCostProperty() { return new SimpleDoubleProperty(getTotalCost()); }
}
