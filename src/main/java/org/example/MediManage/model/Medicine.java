package org.example.MediManage.model;

import javafx.beans.property.*;

public class Medicine {
    private final IntegerProperty id;
    private final StringProperty name;
    private final StringProperty company;
    private final StringProperty expiry;
    private final IntegerProperty stock;
    private final DoubleProperty price;

    private final StringProperty genericName;

    public Medicine(int id, String name, String genericName, String company, String expiry, int stock, double price) {
        this.id = new SimpleIntegerProperty(id);
        this.name = new SimpleStringProperty(name);
        this.genericName = new SimpleStringProperty(genericName != null ? genericName : "");
        this.company = new SimpleStringProperty(company);
        this.expiry = new SimpleStringProperty(expiry);
        this.stock = new SimpleIntegerProperty(stock);
        this.price = new SimpleDoubleProperty(price);
    }

    // Legacy constructor overload if needed, or update calls.
    // Updating main constructor is better, but need to check usage sites.
    // DashboardController and MedicineDAO use constructor.
    // I will add a second constructor or update the existing one.
    // Let's overload for backward compatibility to minimize refactoring noise in
    // tests if any,
    // but here I should update the main usage in DAO.

    // Simplest: Add field, init in main constructor, provide an overloaded
    // constructor for legacy calls?
    // User asked to update the app. I will update main constructor.
    // Oops, I need to update all usages.
    // Let's add a secondary constructor for existing code if I don't want to break
    // everything immediately,
    // but the task is to update everything.

    public Medicine(int id, String name, String company, String expiry, int stock, double price) {
        this(id, name, "", company, expiry, stock, price);
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

}
