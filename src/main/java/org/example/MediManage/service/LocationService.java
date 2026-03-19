package org.example.MediManage.service;

import org.example.MediManage.dao.AuditLogDAO;
import org.example.MediManage.dao.LocationDAO;
import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.model.Location;
import org.example.MediManage.model.LocationStockRow;
import org.example.MediManage.model.LocationTransferRow;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.util.UserSession;
import org.json.JSONObject;

import java.sql.SQLException;
import java.util.List;

public class LocationService {
    private final LocationDAO locationDAO;
    private final MedicineDAO medicineDAO;
    private final AuditLogDAO auditLogDAO;

    public LocationService() {
        this(new LocationDAO(), new MedicineDAO(), new AuditLogDAO());
    }

    LocationService(LocationDAO locationDAO, MedicineDAO medicineDAO, AuditLogDAO auditLogDAO) {
        this.locationDAO = locationDAO;
        this.medicineDAO = medicineDAO;
        this.auditLogDAO = auditLogDAO;
    }

    public List<Location> loadLocations() throws SQLException {
        return locationDAO.getActiveLocations();
    }

    public List<Medicine> loadMedicines() {
        return medicineDAO.getAllMedicines();
    }

    public int saveLocation(Location location) throws SQLException {
        if (location == null || location.getName() == null || location.getName().isBlank()) {
            throw new SQLException("Location name is required.");
        }
        if (location.getLocationId() > 0) {
            locationDAO.updateLocation(location);
            logLocationEvent(
                    "LOCATION_UPDATED",
                    location.getLocationId(),
                    "Updated location " + location.getName(),
                    new JSONObject()
                            .put("name", location.getName())
                            .put("address", location.getAddress() == null ? "" : location.getAddress())
                            .put("phone", location.getPhone() == null ? "" : location.getPhone())
                            .put("locationType", location.getLocationType() == null ? "" : location.getLocationType()));
            return location.getLocationId();
        }
        int locationId = locationDAO.addLocation(location);
        location.setLocationId(locationId);
        logLocationEvent(
                "LOCATION_CREATED",
                locationId,
                "Created location " + location.getName(),
                new JSONObject()
                        .put("name", location.getName())
                        .put("address", location.getAddress() == null ? "" : location.getAddress())
                        .put("phone", location.getPhone() == null ? "" : location.getPhone())
                        .put("locationType", location.getLocationType() == null ? "" : location.getLocationType()));
        return locationId;
    }

    public List<LocationStockRow> loadLocationStock(int locationId) throws SQLException {
        return locationDAO.getStockRowsForLocation(locationId);
    }

    public void setLocationStock(int locationId, int medicineId, int quantity) throws SQLException {
        if (locationId <= 0) {
            throw new SQLException("Choose a location first.");
        }
        if (medicineId <= 0) {
            throw new SQLException("Choose a medicine first.");
        }
        if (quantity < 0) {
            throw new SQLException("Quantity cannot be negative.");
        }

        locationDAO.setStockAtLocation(locationId, medicineId, quantity);
        medicineDAO.updateStock(medicineId, locationDAO.sumAllocatedStock(medicineId));
        logLocationEvent(
                "LOCATION_STOCK_SET",
                locationId,
                "Set allocated stock for medicine #" + medicineId + " at location #" + locationId,
                new JSONObject()
                        .put("locationId", locationId)
                        .put("medicineId", medicineId)
                        .put("quantity", quantity));
    }

    public int createTransfer(int fromLocationId, int toLocationId, int medicineId, int quantity, int requestedBy)
            throws SQLException {
        if (fromLocationId <= 0 || toLocationId <= 0) {
            throw new SQLException("Choose both source and destination locations.");
        }
        if (fromLocationId == toLocationId) {
            throw new SQLException("Source and destination locations must be different.");
        }
        if (medicineId <= 0) {
            throw new SQLException("Choose a medicine to transfer.");
        }
        if (quantity <= 0) {
            throw new SQLException("Transfer quantity must be greater than zero.");
        }
        int transferId = locationDAO.createTransfer(fromLocationId, toLocationId, medicineId, quantity, requestedBy);
        logLocationEvent(
                "TRANSFER_REQUESTED",
                transferId,
                "Requested transfer #" + transferId,
                new JSONObject()
                        .put("transferId", transferId)
                        .put("fromLocationId", fromLocationId)
                        .put("toLocationId", toLocationId)
                        .put("medicineId", medicineId)
                        .put("quantity", quantity)
                        .put("requestedBy", requestedBy));
        return transferId;
    }

    public boolean completeTransfer(int transferId) throws SQLException {
        if (transferId <= 0) {
            throw new SQLException("Choose a pending transfer first.");
        }
        boolean completed = locationDAO.completeTransfer(transferId);
        if (completed) {
            logLocationEvent(
                    "TRANSFER_COMPLETED",
                    transferId,
                    "Completed transfer #" + transferId,
                    new JSONObject().put("transferId", transferId));
        }
        return completed;
    }

    public List<LocationTransferRow> loadRecentTransfers(int limit) throws SQLException {
        return locationDAO.getRecentTransfers(limit);
    }

    private void logLocationEvent(String eventType, int entityId, String summary, JSONObject details) {
        try {
            Integer actorUserId = UserSession.getInstance().getUser() == null
                    ? null
                    : UserSession.getInstance().getUser().getId();
            auditLogDAO.logEvent(
                    actorUserId,
                    eventType,
                    "LOCATION",
                    entityId,
                    summary,
                    details == null ? "" : details.toString());
        } catch (Exception ignored) {
        }
    }
}
