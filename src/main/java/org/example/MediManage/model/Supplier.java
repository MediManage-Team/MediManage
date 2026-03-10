package org.example.MediManage.model;

import javafx.beans.property.*;

/**
 * Represents a supplier/vendor who provides medicines.
 */
public class Supplier {
    private final IntegerProperty supplierId = new SimpleIntegerProperty();
    private final StringProperty name = new SimpleStringProperty();
    private final StringProperty contactPerson = new SimpleStringProperty();
    private final StringProperty phone = new SimpleStringProperty();
    private final StringProperty email = new SimpleStringProperty();
    private final StringProperty address = new SimpleStringProperty();
    private final StringProperty gstNumber = new SimpleStringProperty();
    private final BooleanProperty active = new SimpleBooleanProperty(true);
    private final StringProperty createdAt = new SimpleStringProperty();

    public Supplier() {
    }

    public Supplier(int id, String name, String contactPerson, String phone, String email, String address,
            String gstNumber) {
        this.supplierId.set(id);
        this.name.set(name);
        this.contactPerson.set(contactPerson);
        this.phone.set(phone);
        this.email.set(email);
        this.address.set(address);
        this.gstNumber.set(gstNumber);
    }

    // supplierId
    public int getSupplierId() {
        return supplierId.get();
    }

    public void setSupplierId(int v) {
        supplierId.set(v);
    }

    public IntegerProperty supplierIdProperty() {
        return supplierId;
    }

    // name
    public String getName() {
        return name.get();
    }

    public void setName(String v) {
        name.set(v);
    }

    public StringProperty nameProperty() {
        return name;
    }

    // contactPerson
    public String getContactPerson() {
        return contactPerson.get();
    }

    public void setContactPerson(String v) {
        contactPerson.set(v);
    }

    public StringProperty contactPersonProperty() {
        return contactPerson;
    }

    // phone
    public String getPhone() {
        return phone.get();
    }

    public void setPhone(String v) {
        phone.set(v);
    }

    public StringProperty phoneProperty() {
        return phone;
    }

    // email
    public String getEmail() {
        return email.get();
    }

    public void setEmail(String v) {
        email.set(v);
    }

    public StringProperty emailProperty() {
        return email;
    }

    // address
    public String getAddress() {
        return address.get();
    }

    public void setAddress(String v) {
        address.set(v);
    }

    public StringProperty addressProperty() {
        return address;
    }

    // gstNumber
    public String getGstNumber() {
        return gstNumber.get();
    }

    public void setGstNumber(String v) {
        gstNumber.set(v);
    }

    public StringProperty gstNumberProperty() {
        return gstNumber;
    }

    // active
    public boolean isActive() {
        return active.get();
    }

    public void setActive(boolean v) {
        active.set(v);
    }

    public BooleanProperty activeProperty() {
        return active;
    }

    // createdAt
    public String getCreatedAt() {
        return createdAt.get();
    }

    public void setCreatedAt(String v) {
        createdAt.set(v);
    }

    public StringProperty createdAtProperty() {
        return createdAt;
    }

    @Override
    public String toString() {
        return name.get() + (contactPerson.get() != null ? " (" + contactPerson.get() + ")" : "");
    }
}
