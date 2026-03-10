package org.example.MediManage.model;

import javafx.beans.property.*;

/**
 * Represents a single payment split within a mixed-payment bill.
 */
public class PaymentSplit {
    private final IntegerProperty splitId = new SimpleIntegerProperty();
    private final IntegerProperty billId = new SimpleIntegerProperty();
    private final StringProperty paymentMethod = new SimpleStringProperty();
    private final DoubleProperty amount = new SimpleDoubleProperty();
    private final StringProperty referenceNumber = new SimpleStringProperty();

    public PaymentSplit() {
    }

    public PaymentSplit(String paymentMethod, double amount) {
        this.paymentMethod.set(paymentMethod);
        this.amount.set(amount);
    }

    public PaymentSplit(String paymentMethod, double amount, String referenceNumber) {
        this(paymentMethod, amount);
        this.referenceNumber.set(referenceNumber);
    }

    // splitId
    public int getSplitId() {
        return splitId.get();
    }

    public void setSplitId(int v) {
        splitId.set(v);
    }

    public IntegerProperty splitIdProperty() {
        return splitId;
    }

    // billId
    public int getBillId() {
        return billId.get();
    }

    public void setBillId(int v) {
        billId.set(v);
    }

    public IntegerProperty billIdProperty() {
        return billId;
    }

    // paymentMethod
    public String getPaymentMethod() {
        return paymentMethod.get();
    }

    public void setPaymentMethod(String v) {
        paymentMethod.set(v);
    }

    public StringProperty paymentMethodProperty() {
        return paymentMethod;
    }

    // amount
    public double getAmount() {
        return amount.get();
    }

    public void setAmount(double v) {
        amount.set(v);
    }

    public DoubleProperty amountProperty() {
        return amount;
    }

    // referenceNumber
    public String getReferenceNumber() {
        return referenceNumber.get();
    }

    public void setReferenceNumber(String v) {
        referenceNumber.set(v);
    }

    public StringProperty referenceNumberProperty() {
        return referenceNumber;
    }

    @Override
    public String toString() {
        return paymentMethod.get() + ": ₹" + String.format("%.2f", amount.get());
    }
}
