package org.example.MediManage.model;

import javafx.beans.property.*;

/**
 * Represents a physical location (pharmacy, warehouse, or clinic).
 */
public class Location {
    private final IntegerProperty locationId = new SimpleIntegerProperty();
    private final StringProperty name = new SimpleStringProperty();
    private final StringProperty address = new SimpleStringProperty();
    private final StringProperty phone = new SimpleStringProperty();
    private final StringProperty locationType = new SimpleStringProperty("PHARMACY");
    private final BooleanProperty active = new SimpleBooleanProperty(true);

    public Location() {
    }

    public Location(int id, String name, String address, String phone, String locationType) {
        this.locationId.set(id);
        this.name.set(name);
        this.address.set(address);
        this.phone.set(phone);
        this.locationType.set(locationType);
    }

    public int getLocationId() {
        return locationId.get();
    }

    public void setLocationId(int v) {
        locationId.set(v);
    }

    public IntegerProperty locationIdProperty() {
        return locationId;
    }

    public String getName() {
        return name.get();
    }

    public void setName(String v) {
        name.set(v);
    }

    public StringProperty nameProperty() {
        return name;
    }

    public String getAddress() {
        return address.get();
    }

    public void setAddress(String v) {
        address.set(v);
    }

    public StringProperty addressProperty() {
        return address;
    }

    public String getPhone() {
        return phone.get();
    }

    public void setPhone(String v) {
        phone.set(v);
    }

    public StringProperty phoneProperty() {
        return phone;
    }

    public String getLocationType() {
        return locationType.get();
    }

    public void setLocationType(String v) {
        locationType.set(v);
    }

    public StringProperty locationTypeProperty() {
        return locationType;
    }

    public boolean isActive() {
        return active.get();
    }

    public void setActive(boolean v) {
        active.set(v);
    }

    public BooleanProperty activeProperty() {
        return active;
    }

    @Override
    public String toString() {
        return name.get() + " (" + locationType.get() + ")";
    }
}
