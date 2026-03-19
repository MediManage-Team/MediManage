package org.example.MediManage.model;

import javafx.beans.property.*;

public class Medicine {
    private final IntegerProperty id;
    private final StringProperty name;
    private final StringProperty genericName;
    private final StringProperty company;
    private final StringProperty expiry;
    private final IntegerProperty stock;
    private final DoubleProperty price;
    private final DoubleProperty purchasePrice;
    private final IntegerProperty reorderThreshold;
    private final StringProperty barcode;

    public Medicine(int id, String name, String genericName, String company, String expiry, int stock, double price) {
        this(id, name, genericName, company, expiry, stock, price, 0.0, 10, "");
    }

    public Medicine(int id, String name, String genericName, String company, String expiry, int stock, double price,
            double purchasePrice) {
        this(id, name, genericName, company, expiry, stock, price, purchasePrice, 10, "");
    }

    public Medicine(int id, String name, String genericName, String company, String expiry, int stock, double price,
            double purchasePrice, int reorderThreshold, String barcode) {
        this.id = new SimpleIntegerProperty(id);
        this.name = new SimpleStringProperty(name);
        this.genericName = new SimpleStringProperty(genericName != null ? genericName : "");
        this.company = new SimpleStringProperty(company);
        this.expiry = new SimpleStringProperty(expiry);
        this.stock = new SimpleIntegerProperty(stock);
        this.price = new SimpleDoubleProperty(price);
        this.purchasePrice = new SimpleDoubleProperty(purchasePrice);
        this.reorderThreshold = new SimpleIntegerProperty(reorderThreshold);
        this.barcode = new SimpleStringProperty(barcode != null ? barcode : "");
    }

    public Medicine(int id, String name, String company, String expiry, int stock, double price) {
        this(id, name, "", company, expiry, stock, price, 0.0, 10, "");
    }

    public StringProperty genericNameProperty() {
        return genericName;
    }

    public String getGenericName() {
        return genericName.get();
    }

    public void setGenericName(String genericName) {
        this.genericName.set(genericName);
    }

    @Override
    public String toString() {
        return String.format("%s (%s) - ₹%.2f", getName(), getCompany(), getPrice());
    }

    // Getters for properties (JavaFX use)
    public IntegerProperty idProperty() {
        return id;
    }

    public StringProperty nameProperty() {
        return name;
    }

    public StringProperty companyProperty() {
        return company;
    }

    public StringProperty expiryProperty() {
        return expiry;
    }

    public IntegerProperty stockProperty() {
        return stock;
    }

    public DoubleProperty priceProperty() {
        return price;
    }

    // Standard getters
    public int getId() {
        return id.get();
    }

    public String getName() {
        return name.get();
    }

    public String getCompany() {
        return company.get();
    }

    public String getExpiry() {
        return expiry.get();
    }

    public int getStock() {
        return stock.get();
    }

    public double getPrice() {
        return price.get();
    }

    // Setters
    public void setId(int id) {
        this.id.set(id);
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public void setCompany(String company) {
        this.company.set(company);
    }

    public void setExpiry(String expiry) {
        this.expiry.set(expiry);
    }

    public void setStock(int stock) {
        this.stock.set(stock);
    }

    public void setPrice(double price) {
        this.price.set(price);
    }

    // Purchase price
    public DoubleProperty purchasePriceProperty() {
        return purchasePrice;
    }

    public double getPurchasePrice() {
        return purchasePrice.get();
    }

    public void setPurchasePrice(double purchasePrice) {
        this.purchasePrice.set(purchasePrice);
    }

    public IntegerProperty reorderThresholdProperty() {
        return reorderThreshold;
    }

    public int getReorderThreshold() {
        return reorderThreshold.get();
    }

    public void setReorderThreshold(int reorderThreshold) {
        this.reorderThreshold.set(reorderThreshold);
    }

    public StringProperty barcodeProperty() {
        return barcode;
    }

    public String getBarcode() {
        return barcode.get();
    }

    public void setBarcode(String barcode) {
        this.barcode.set(barcode != null ? barcode : "");
    }

    /**
     * Computes profit margin as a percentage: ((price - purchasePrice) / price) *
     * 100.
     * Returns 0.0 if price is zero or purchasePrice is not set.
     */
    public double getProfitMarginPercent() {
        double sell = getPrice();
        double cost = getPurchasePrice();
        if (sell <= 0 || cost <= 0)
            return 0.0;
        return ((sell - cost) / sell) * 100.0;
    }

}
