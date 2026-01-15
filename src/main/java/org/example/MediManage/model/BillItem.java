package org.example.MediManage.model;

import javafx.beans.property.*;

public class BillItem {
    private final int medicineId;
    private final StringProperty name;
    private final StringProperty expiry;
    private final IntegerProperty qty;
    private final DoubleProperty price;
    private final DoubleProperty gst;
    private final DoubleProperty total;

    public BillItem(int medicineId, String name, String expiry, int qty, double price, double gst) {
        this.medicineId = medicineId;
        this.name = new SimpleStringProperty(name);
        this.expiry = new SimpleStringProperty(expiry != null ? expiry : "N/A");
        this.qty = new SimpleIntegerProperty(qty);
        this.price = new SimpleDoubleProperty(price);
        this.gst = new SimpleDoubleProperty(gst);
        this.total = new SimpleDoubleProperty((price * qty) + gst);
    }

    public StringProperty nameProperty() {
        return name;
    }

    public StringProperty expiryProperty() {
        return expiry;
    }

    public String getExpiry() {
        return expiry.get();
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

    public int getQty() {
        return qty.get();
    }

    public double getPrice() {
        return price.get();
    }

    public double getTotal() {
        return total.get();
    }

    public String getName() {
        return name.get();
    }

    // Setters if needed for updates
    public void setQty(int q) {
        this.qty.set(q);
    }

    public void setTotal(double t) {
        this.total.set(t);
    }
}
