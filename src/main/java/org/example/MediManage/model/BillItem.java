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
    private final DoubleProperty subscriptionDiscountPercent;
    private final DoubleProperty subscriptionDiscountAmount;
    private final StringProperty subscriptionRuleSource;

    public BillItem(int medicineId, String name, String expiry, int qty, double price, double gst) {
        this.medicineId = medicineId;
        this.name = new SimpleStringProperty(name);
        this.expiry = new SimpleStringProperty(expiry != null ? expiry : "N/A");
        this.qty = new SimpleIntegerProperty(qty);
        this.price = new SimpleDoubleProperty(price);
        this.gst = new SimpleDoubleProperty(gst);
        this.total = new SimpleDoubleProperty((price * qty) + gst);
        this.subscriptionDiscountPercent = new SimpleDoubleProperty(0.0);
        this.subscriptionDiscountAmount = new SimpleDoubleProperty(0.0);
        this.subscriptionRuleSource = new SimpleStringProperty("");
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

    public DoubleProperty subscriptionDiscountPercentProperty() {
        return subscriptionDiscountPercent;
    }

    public DoubleProperty subscriptionDiscountAmountProperty() {
        return subscriptionDiscountAmount;
    }

    public StringProperty subscriptionRuleSourceProperty() {
        return subscriptionRuleSource;
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

    public double getGst() {
        return gst.get();
    }

    public double getSubscriptionDiscountPercent() {
        return subscriptionDiscountPercent.get();
    }

    public double getSubscriptionDiscountAmount() {
        return subscriptionDiscountAmount.get();
    }

    public String getSubscriptionRuleSource() {
        return subscriptionRuleSource.get();
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

    public void setSubscriptionDiscountPercent(double percent) {
        this.subscriptionDiscountPercent.set(percent);
    }

    public void setSubscriptionDiscountAmount(double amount) {
        this.subscriptionDiscountAmount.set(amount);
    }

    public void setSubscriptionRuleSource(String source) {
        this.subscriptionRuleSource.set(source == null ? "" : source);
    }
}
