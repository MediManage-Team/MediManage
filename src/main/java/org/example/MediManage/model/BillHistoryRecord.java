package org.example.MediManage.model;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class BillHistoryRecord {
    private final int billId;
    private final StringProperty date;
    private final DoubleProperty total;
    private final StringProperty customerName;
    private final StringProperty phone;
    private final StringProperty username;

    public BillHistoryRecord(int billId, String date, double total, String customerName, String phone, String username) {
        this.billId = billId;
        this.date = new SimpleStringProperty(date);
        this.total = new SimpleDoubleProperty(total);
        this.customerName = new SimpleStringProperty(customerName != null ? customerName : "N/A");
        this.phone = new SimpleStringProperty(phone != null ? phone : "N/A");
        this.username = new SimpleStringProperty(username != null ? username : "N/A");
    }

    public int getBillId() {
        return billId;
    }

    public StringProperty dateProperty() {
        return date;
    }

    public DoubleProperty totalProperty() {
        return total;
    }

    public StringProperty customerNameProperty() {
        return customerName;
    }

    public StringProperty phoneProperty() {
        return phone;
    }

    public StringProperty usernameProperty() {
        return username;
    }
}
