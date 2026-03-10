package org.example.MediManage.model;

import javafx.beans.property.*;

/**
 * Represents a held (layaway) order that can be recalled later.
 */
public class HeldOrder {
    private final IntegerProperty holdId = new SimpleIntegerProperty();
    private final IntegerProperty customerId = new SimpleIntegerProperty();
    private final IntegerProperty userId = new SimpleIntegerProperty();
    private final StringProperty itemsJson = new SimpleStringProperty();
    private final DoubleProperty totalAmount = new SimpleDoubleProperty();
    private final StringProperty notes = new SimpleStringProperty();
    private final StringProperty status = new SimpleStringProperty("HELD");
    private final StringProperty heldAt = new SimpleStringProperty();
    private final StringProperty recalledAt = new SimpleStringProperty();

    // Extra display-only fields
    private final StringProperty customerName = new SimpleStringProperty();
    private final StringProperty userName = new SimpleStringProperty();
    private final IntegerProperty itemCount = new SimpleIntegerProperty();

    public HeldOrder() {
    }

    // holdId
    public int getHoldId() {
        return holdId.get();
    }

    public void setHoldId(int v) {
        holdId.set(v);
    }

    public IntegerProperty holdIdProperty() {
        return holdId;
    }

    // customerId
    public int getCustomerId() {
        return customerId.get();
    }

    public void setCustomerId(int v) {
        customerId.set(v);
    }

    public IntegerProperty customerIdProperty() {
        return customerId;
    }

    // userId
    public int getUserId() {
        return userId.get();
    }

    public void setUserId(int v) {
        userId.set(v);
    }

    public IntegerProperty userIdProperty() {
        return userId;
    }

    // itemsJson
    public String getItemsJson() {
        return itemsJson.get();
    }

    public void setItemsJson(String v) {
        itemsJson.set(v);
    }

    public StringProperty itemsJsonProperty() {
        return itemsJson;
    }

    // totalAmount
    public double getTotalAmount() {
        return totalAmount.get();
    }

    public void setTotalAmount(double v) {
        totalAmount.set(v);
    }

    public DoubleProperty totalAmountProperty() {
        return totalAmount;
    }

    // notes
    public String getNotes() {
        return notes.get();
    }

    public void setNotes(String v) {
        notes.set(v);
    }

    public StringProperty notesProperty() {
        return notes;
    }

    // status
    public String getStatus() {
        return status.get();
    }

    public void setStatus(String v) {
        status.set(v);
    }

    public StringProperty statusProperty() {
        return status;
    }

    // heldAt
    public String getHeldAt() {
        return heldAt.get();
    }

    public void setHeldAt(String v) {
        heldAt.set(v);
    }

    public StringProperty heldAtProperty() {
        return heldAt;
    }

    // recalledAt
    public String getRecalledAt() {
        return recalledAt.get();
    }

    public void setRecalledAt(String v) {
        recalledAt.set(v);
    }

    public StringProperty recalledAtProperty() {
        return recalledAt;
    }

    // customerName (display-only)
    public String getCustomerName() {
        return customerName.get();
    }

    public void setCustomerName(String v) {
        customerName.set(v);
    }

    public StringProperty customerNameProperty() {
        return customerName;
    }

    // userName (display-only)
    public String getUserName() {
        return userName.get();
    }

    public void setUserName(String v) {
        userName.set(v);
    }

    public StringProperty userNameProperty() {
        return userName;
    }

    // itemCount (display-only)
    public int getItemCount() {
        return itemCount.get();
    }

    public void setItemCount(int v) {
        itemCount.set(v);
    }

    public IntegerProperty itemCountProperty() {
        return itemCount;
    }

    @Override
    public String toString() {
        String customer = customerName.get() != null ? customerName.get() : "Walk-in";
        String time = heldAt.get() != null ? heldAt.get() : "?";
        return String.format("#%d | %s | ₹%.2f | %s", holdId.get(), customer, totalAmount.get(), time);
    }
}
