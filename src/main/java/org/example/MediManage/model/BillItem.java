package org.example.MediManage.model;

import javafx.beans.property.*;

public class BillItem {
    private Integer itemId;
    private final int medicineId;
    private final StringProperty name;
    private final StringProperty expiry;
    private final IntegerProperty qty;
    private final DoubleProperty price;
    private final DoubleProperty gst;
    private final DoubleProperty total;
    private PrescriptionDirection prescriptionDirection;

    public BillItem(int medicineId, String name, String expiry, int qty, double price, double gst) {
        this.medicineId = medicineId;
        this.name = new SimpleStringProperty(name);
        this.expiry = new SimpleStringProperty(expiry != null ? expiry : "N/A");
        this.qty = new SimpleIntegerProperty(qty);
        this.price = new SimpleDoubleProperty(price);
        this.gst = new SimpleDoubleProperty(gst);
        this.total = new SimpleDoubleProperty((price * qty) + gst);
        this.prescriptionDirection = new PrescriptionDirection();
    }

    public StringProperty nameProperty() {
        return name;
    }

    public StringProperty expiryProperty() {
        return expiry;
    }

    public IntegerProperty qtyProperty() {
        return qty;
    }

    public DoubleProperty priceProperty() {
        return price;
    }

    public DoubleProperty gstProperty() {
        return gst;
    }

    public DoubleProperty totalProperty() {
        return total;
    }

    public int getMedicineId() {
        return medicineId;
    }

    public Integer getItemId() {
        return itemId;
    }

    public String getName() {
        return name.get();
    }

    public String getExpiry() {
        return expiry.get();
    }

    public int getQty() {
        return qty.get();
    }

    public double getPrice() {
        return price.get();
    }

    public double getGst() {
        return gst.get();
    }

    public double getTotal() {
        return total.get();
    }

    public PrescriptionDirection getPrescriptionDirection() {
        if (prescriptionDirection == null) {
            prescriptionDirection = new PrescriptionDirection();
        }
        return prescriptionDirection;
    }

    public boolean hasPrescriptionDirection() {
        return prescriptionDirection != null && !prescriptionDirection.isEmpty();
    }

    public String getPrescriptionSummary() {
        return hasPrescriptionDirection() ? prescriptionDirection.buildSummary() : "";
    }

    public String getMorningDose() {
        return getPrescriptionDirection().getMorningDose();
    }

    public String getAfternoonDose() {
        return getPrescriptionDirection().getAfternoonDose();
    }

    public String getEveningDose() {
        return getPrescriptionDirection().getEveningDose();
    }

    public String getNightDose() {
        return getPrescriptionDirection().getNightDose();
    }

    public String getExactTime() {
        return getPrescriptionDirection().getExactTime();
    }

    public String getMealRelation() {
        return getPrescriptionDirection().getMealRelation();
    }

    public String getDuration() {
        return getPrescriptionDirection().getDuration();
    }

    public String getShortNote() {
        return getPrescriptionDirection().getShortNote();
    }

    public void setQty(int q) {
        this.qty.set(q);
    }

    public void setGst(double gst) {
        this.gst.set(gst);
    }

    public void setTotal(double t) {
        this.total.set(t);
    }

    public void setItemId(Integer itemId) {
        this.itemId = itemId;
    }

    public void setPrescriptionDirection(PrescriptionDirection prescriptionDirection) {
        this.prescriptionDirection = prescriptionDirection == null ? new PrescriptionDirection() : prescriptionDirection.copy();
    }
}
