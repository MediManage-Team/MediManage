package org.example.MediManage.model;

import javafx.beans.property.*;

public class Medicine {
    private final IntegerProperty id;
    private final StringProperty name;
    private final StringProperty company;
    private final StringProperty expiry;
    private final IntegerProperty stock;
    private final DoubleProperty price;

    public Medicine(int id, String name, String company, String expiry, int stock, double price) {
        this.id = new SimpleIntegerProperty(id);
        this.name = new SimpleStringProperty(name);
        this.company = new SimpleStringProperty(company);
        this.expiry = new SimpleStringProperty(expiry);
        this.stock = new SimpleIntegerProperty(stock);
        this.price = new SimpleDoubleProperty(price);
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

    @Override
    public String toString() {
        return String.format("%s (%s) - ₹%.2f", getName(), getCompany(), getPrice());
    }
}
