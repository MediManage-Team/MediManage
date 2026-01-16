package org.example.MediManage.model;

import javafx.beans.property.*;

public class Expense {
    private final IntegerProperty id;
    private final StringProperty category;
    private final DoubleProperty amount;
    private final StringProperty date;
    private final StringProperty description;

    public Expense(int id, String category, double amount, String date, String description) {
        this.id = new SimpleIntegerProperty(id);
        this.category = new SimpleStringProperty(category);
        this.amount = new SimpleDoubleProperty(amount);
        this.date = new SimpleStringProperty(date);
        this.description = new SimpleStringProperty(description);
    }

    public IntegerProperty idProperty() {
        return id;
    }

    public StringProperty categoryProperty() {
        return category;
    }

    public DoubleProperty amountProperty() {
        return amount;
    }

    public StringProperty dateProperty() {
        return date;
    }

    public StringProperty descriptionProperty() {
        return description;
    }

    public int getId() {
        return id.get();
    }

    public String getCategory() {
        return category.get();
    }

    public double getAmount() {
        return amount.get();
    }

    public String getDate() {
        return date.get();
    }

    public String getDescription() {
        return description.get();
    }
}
