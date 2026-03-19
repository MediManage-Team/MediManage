package org.example.MediManage.model;

import javafx.beans.property.*;

public class PurchaseOrderItem {
    private final IntegerProperty poiId = new SimpleIntegerProperty();
    private final IntegerProperty poId = new SimpleIntegerProperty();
    private final IntegerProperty medicineId = new SimpleIntegerProperty();
    private final StringProperty medicineName = new SimpleStringProperty(); // Helper for UI
    private final StringProperty genericName = new SimpleStringProperty();
    private final StringProperty company = new SimpleStringProperty(); // Helper for UI
    private final StringProperty batchNumber = new SimpleStringProperty();
    private final StringProperty expiryDate = new SimpleStringProperty();
    private final StringProperty purchaseDate = new SimpleStringProperty();
    private final IntegerProperty orderedQty = new SimpleIntegerProperty();
    private final IntegerProperty receivedQty = new SimpleIntegerProperty();
    private final DoubleProperty unitCost = new SimpleDoubleProperty();
    private final DoubleProperty sellingPrice = new SimpleDoubleProperty();
    private final IntegerProperty reorderThreshold = new SimpleIntegerProperty(10);

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

    public String getGenericName() { return genericName.get(); }
    public StringProperty genericNameProperty() { return genericName; }
    public void setGenericName(String genericName) { this.genericName.set(genericName); }

    public String getCompany() { return company.get(); }
    public StringProperty companyProperty() { return company; }
    public void setCompany(String company) { this.company.set(company); }

    public String getBatchNumber() { return batchNumber.get(); }
    public StringProperty batchNumberProperty() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber.set(batchNumber); }

    public String getExpiryDate() { return expiryDate.get(); }
    public StringProperty expiryDateProperty() { return expiryDate; }
    public void setExpiryDate(String expiryDate) { this.expiryDate.set(expiryDate); }

    public String getPurchaseDate() { return purchaseDate.get(); }
    public StringProperty purchaseDateProperty() { return purchaseDate; }
    public void setPurchaseDate(String purchaseDate) { this.purchaseDate.set(purchaseDate); }

    public int getOrderedQty() { return orderedQty.get(); }
    public IntegerProperty orderedQtyProperty() { return orderedQty; }
    public void setOrderedQty(int orderedQty) { this.orderedQty.set(orderedQty); }

    public int getReceivedQty() { return receivedQty.get(); }
    public IntegerProperty receivedQtyProperty() { return receivedQty; }
    public void setReceivedQty(int receivedQty) { this.receivedQty.set(receivedQty); }

    public double getUnitCost() { return unitCost.get(); }
    public DoubleProperty unitCostProperty() { return unitCost; }
    public void setUnitCost(double unitCost) { this.unitCost.set(unitCost); }

    public double getSellingPrice() { return sellingPrice.get(); }
    public DoubleProperty sellingPriceProperty() { return sellingPrice; }
    public void setSellingPrice(double sellingPrice) { this.sellingPrice.set(sellingPrice); }

    public int getReorderThreshold() { return reorderThreshold.get(); }
    public IntegerProperty reorderThresholdProperty() { return reorderThreshold; }
    public void setReorderThreshold(int reorderThreshold) { this.reorderThreshold.set(reorderThreshold); }
    
    // Derived property for UI
    public double getTotalCost() { return unitCost.get() * receivedQty.get(); }
    public DoubleProperty totalCostProperty() { return new SimpleDoubleProperty(getTotalCost()); }
}
