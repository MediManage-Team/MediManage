package org.example.MediManage.controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.example.MediManage.model.Location;
import org.example.MediManage.model.LocationStockRow;
import org.example.MediManage.model.LocationTransferRow;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.model.UserRole;
import org.example.MediManage.security.Permission;
import org.example.MediManage.security.RbacPolicy;
import org.example.MediManage.service.LocationService;
import org.example.MediManage.util.AppExecutors;
import org.example.MediManage.util.SupervisorApprovalDialogs;
import org.example.MediManage.util.UserSession;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

public class LocationsController {
    @FXML private TableView<Location> locationTable;
    @FXML private TableColumn<Location, String> colLocationName;
    @FXML private TableColumn<Location, String> colLocationType;
    @FXML private TableColumn<Location, String> colLocationPhone;
    @FXML private TableColumn<Location, String> colLocationAddress;

    @FXML private TextField txtLocationName;
    @FXML private TextField txtLocationAddress;
    @FXML private TextField txtLocationPhone;
    @FXML private ComboBox<String> cmbLocationType;
    @FXML private Button btnSaveLocation;

    @FXML private Label lblSelectedLocation;
    @FXML private TableView<LocationStockRow> locationStockTable;
    @FXML private TableColumn<LocationStockRow, String> colStockMedicine;
    @FXML private TableColumn<LocationStockRow, String> colStockGeneric;
    @FXML private TableColumn<LocationStockRow, String> colStockCompany;
    @FXML private TableColumn<LocationStockRow, Number> colStockQty;
    @FXML private TableColumn<LocationStockRow, Number> colStockMin;
    @FXML private ComboBox<Medicine> cmbStockMedicine;
    @FXML private TextField txtStockQuantity;

    @FXML private ComboBox<Location> cmbTransferFrom;
    @FXML private ComboBox<Location> cmbTransferTo;
    @FXML private ComboBox<Medicine> cmbTransferMedicine;
    @FXML private TextField txtTransferQuantity;
    @FXML private TableView<LocationTransferRow> transferTable;
    @FXML private TableColumn<LocationTransferRow, String> colTransferRoute;
    @FXML private TableColumn<LocationTransferRow, String> colTransferMedicine;
    @FXML private TableColumn<LocationTransferRow, Number> colTransferQty;
    @FXML private TableColumn<LocationTransferRow, String> colTransferStatus;
    @FXML private TableColumn<LocationTransferRow, String> colTransferRequestedAt;

    private final LocationService locationService = new LocationService();
    private final ObservableList<Location> locations = FXCollections.observableArrayList();
    private final ObservableList<Medicine> medicines = FXCollections.observableArrayList();
    private final ObservableList<LocationStockRow> stockRows = FXCollections.observableArrayList();
    private final ObservableList<LocationTransferRow> transferRows = FXCollections.observableArrayList();
    private Location selectedLocation;

    @FXML
    public void initialize() {
        RbacPolicy.requireCurrentUser(Permission.MANAGE_MEDICINES);
        setupTables();
        setupCombos();
        loadReferenceData();
    }

    private void setupTables() {
        colLocationName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getName()));
        colLocationType.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getLocationType()));
        colLocationPhone.setCellValueFactory(d -> new SimpleStringProperty(safe(d.getValue().getPhone())));
        colLocationAddress.setCellValueFactory(d -> new SimpleStringProperty(safe(d.getValue().getAddress())));
        locationTable.setItems(locations);
        locationTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectLocation(newVal);
            }
        });

        colStockMedicine.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().medicineName()));
        colStockGeneric.setCellValueFactory(d -> new SimpleStringProperty(safe(d.getValue().genericName())));
        colStockCompany.setCellValueFactory(d -> new SimpleStringProperty(safe(d.getValue().company())));
        colStockQty.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().quantity()));
        colStockMin.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().minStock()));
        locationStockTable.setItems(stockRows);
        locationStockTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selectLocationStock(newVal);
            }
        });

        colTransferRoute.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().fromLocationName() + " -> " + d.getValue().toLocationName()));
        colTransferMedicine.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().medicineName()));
        colTransferQty.setCellValueFactory(d -> new SimpleIntegerProperty(d.getValue().quantity()));
        colTransferStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().status()));
        colTransferRequestedAt.setCellValueFactory(d -> new SimpleStringProperty(safe(d.getValue().requestedAt())));
        transferTable.setItems(transferRows);
    }

    private void setupCombos() {
        cmbLocationType.setItems(FXCollections.observableArrayList("PHARMACY", "WAREHOUSE", "CLINIC"));
        cmbLocationType.setValue("PHARMACY");
        cmbStockMedicine.setItems(medicines);
        cmbTransferMedicine.setItems(medicines);
        cmbTransferFrom.setItems(locations);
        cmbTransferTo.setItems(locations);
    }

    private void loadReferenceData() {
        AppExecutors.runBackground(() -> {
            try {
                List<Location> loadedLocations = locationService.loadLocations();
                List<Medicine> loadedMedicines = locationService.loadMedicines();
                List<LocationTransferRow> loadedTransfers = locationService.loadRecentTransfers(25);

                Platform.runLater(() -> {
                    locations.setAll(loadedLocations);
                    medicines.setAll(loadedMedicines);
                    transferRows.setAll(loadedTransfers);
                    restoreLocationSelection();
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Location Load Failed", e.getMessage()));
            }
        });
    }

    private void restoreLocationSelection() {
        if (selectedLocation != null) {
            Location matching = locations.stream()
                    .filter(location -> location.getLocationId() == selectedLocation.getLocationId())
                    .findFirst()
                    .orElse(null);
            if (matching != null) {
                locationTable.getSelectionModel().select(matching);
                selectLocation(matching);
                return;
            }
        }

        if (!locations.isEmpty()) {
            locationTable.getSelectionModel().selectFirst();
            selectLocation(locationTable.getSelectionModel().getSelectedItem());
        } else {
            clearLocationForm();
            stockRows.clear();
            if (lblSelectedLocation != null) {
                lblSelectedLocation.setText("Select a location to manage allocated stock.");
            }
        }
    }

    private void selectLocation(Location location) {
        selectedLocation = location;
        txtLocationName.setText(location.getName());
        txtLocationAddress.setText(safe(location.getAddress()));
        txtLocationPhone.setText(safe(location.getPhone()));
        cmbLocationType.setValue(safe(location.getLocationType()).isBlank() ? "PHARMACY" : location.getLocationType());
        btnSaveLocation.setText("Update Location");
        if (lblSelectedLocation != null) {
            lblSelectedLocation.setText("Allocated stock for " + location.getName());
        }
        if (cmbTransferFrom != null) {
            cmbTransferFrom.setValue(location);
        }
        loadLocationStock(location.getLocationId());
    }

    private void selectLocationStock(LocationStockRow row) {
        if (cmbStockMedicine == null || txtStockQuantity == null) {
            return;
        }
        Medicine medicine = medicines.stream()
                .filter(item -> item.getId() == row.medicineId())
                .findFirst()
                .orElse(null);
        if (medicine != null) {
            cmbStockMedicine.setValue(medicine);
        }
        txtStockQuantity.setText(String.valueOf(row.quantity()));
    }

    private void loadLocationStock(int locationId) {
        AppExecutors.runBackground(() -> {
            try {
                List<LocationStockRow> loadedStock = locationService.loadLocationStock(locationId);
                Platform.runLater(() -> stockRows.setAll(loadedStock));
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Stock Load Failed", e.getMessage()));
            }
        });
    }

    @FXML
    private void handleSaveLocation() {
        Location location = selectedLocation == null ? new Location() : selectedLocation;
        location.setName(safe(txtLocationName.getText()));
        location.setAddress(safe(txtLocationAddress.getText()));
        location.setPhone(safe(txtLocationPhone.getText()));
        location.setLocationType(cmbLocationType.getValue() == null ? "PHARMACY" : cmbLocationType.getValue());

        AppExecutors.runBackground(() -> {
            try {
                locationService.saveLocation(location);
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.INFORMATION, "Location Saved",
                            "Location " + location.getName() + " was saved successfully.");
                    selectedLocation = location;
                    loadReferenceData();
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Location Save Failed", e.getMessage()));
            }
        });
    }

    @FXML
    private void handleClearLocation() {
        clearLocationForm();
        locationTable.getSelectionModel().clearSelection();
        selectedLocation = null;
        stockRows.clear();
        if (lblSelectedLocation != null) {
            lblSelectedLocation.setText("Select a location to manage allocated stock.");
        }
    }

    @FXML
    private void handleSetLocationStock() {
        if (selectedLocation == null) {
            showAlert(Alert.AlertType.WARNING, "No Location Selected", "Choose a location first.");
            return;
        }
        Medicine medicine = cmbStockMedicine.getValue();
        if (medicine == null) {
            showAlert(Alert.AlertType.WARNING, "No Medicine Selected", "Choose a medicine to allocate.");
            return;
        }

        int quantity;
        try {
            quantity = Integer.parseInt(txtStockQuantity.getText().trim());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Invalid Quantity", "Quantity must be a whole number.");
            return;
        }
        LocationStockRow existingRow = locationStockTable.getSelectionModel().getSelectedItem();
        if (existingRow != null
                && existingRow.medicineId() == medicine.getId()
                && quantity < existingRow.quantity()
                && !requireSupervisorApproval(
                        "Allocated Stock Reduction",
                        "Reducing stock allocated to a location requires supervisor approval.",
                        "LOCATION_STOCK_REDUCTION",
                        selectedLocation.getLocationId(),
                        Set.of(UserRole.ADMIN))) {
            return;
        }

        AppExecutors.runBackground(() -> {
            try {
                locationService.setLocationStock(selectedLocation.getLocationId(), medicine.getId(), quantity);
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.INFORMATION, "Location Stock Updated",
                            "Allocated stock for " + medicine.getName() + " was updated.");
                    loadLocationStock(selectedLocation.getLocationId());
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Stock Update Failed", e.getMessage()));
            }
        });
    }

    @FXML
    private void handleCreateTransfer() {
        Location from = cmbTransferFrom.getValue();
        Location to = cmbTransferTo.getValue();
        Medicine medicine = cmbTransferMedicine.getValue();
        int quantity;
        try {
            quantity = Integer.parseInt(txtTransferQuantity.getText().trim());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Invalid Quantity", "Transfer quantity must be a whole number.");
            return;
        }

        Integer currentUserId = UserSession.getInstance().getUser() == null
                ? null
                : UserSession.getInstance().getUser().getId();
        if (currentUserId == null) {
            showAlert(Alert.AlertType.ERROR, "Transfer Failed", "A logged-in user is required to request transfers.");
            return;
        }

        AppExecutors.runBackground(() -> {
            try {
                int transferId = locationService.createTransfer(
                        from == null ? 0 : from.getLocationId(),
                        to == null ? 0 : to.getLocationId(),
                        medicine == null ? 0 : medicine.getId(),
                        quantity,
                        currentUserId);
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.INFORMATION, "Transfer Requested",
                            "Transfer #" + transferId + " is pending completion.");
                    txtTransferQuantity.clear();
                    loadReferenceData();
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Transfer Request Failed", e.getMessage()));
            }
        });
    }

    @FXML
    private void handleCompleteTransfer() {
        LocationTransferRow selectedTransfer = transferTable.getSelectionModel().getSelectedItem();
        if (selectedTransfer == null) {
            showAlert(Alert.AlertType.WARNING, "No Transfer Selected", "Choose a pending transfer to complete.");
            return;
        }
        if (!"PENDING".equalsIgnoreCase(selectedTransfer.status())) {
            showAlert(Alert.AlertType.INFORMATION, "Already Processed",
                    "Only pending transfers can be completed from this screen.");
            return;
        }
        if (!requireSupervisorApproval(
                "Transfer Completion Approval",
                "Completing a stock transfer requires supervisor approval.",
                "COMPLETE_TRANSFER",
                selectedTransfer.transferId(),
                Set.of(UserRole.ADMIN))) {
            return;
        }

        AppExecutors.runBackground(() -> {
            try {
                boolean completed = locationService.completeTransfer(selectedTransfer.transferId());
                Platform.runLater(() -> {
                    if (completed) {
                        showAlert(Alert.AlertType.INFORMATION, "Transfer Completed",
                                "Transfer #" + selectedTransfer.transferId() + " was completed.");
                        loadReferenceData();
                        if (selectedLocation != null) {
                            loadLocationStock(selectedLocation.getLocationId());
                        }
                    } else {
                        showAlert(Alert.AlertType.WARNING, "Transfer Not Completed",
                                "Stock was insufficient or the transfer was already processed.");
                    }
                });
            } catch (SQLException e) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Transfer Completion Failed", e.getMessage()));
            }
        });
    }

    @FXML
    private void handleRefresh() {
        loadReferenceData();
        if (selectedLocation != null) {
            loadLocationStock(selectedLocation.getLocationId());
        }
    }

    private void clearLocationForm() {
        txtLocationName.clear();
        txtLocationAddress.clear();
        txtLocationPhone.clear();
        cmbLocationType.setValue("PHARMACY");
        btnSaveLocation.setText("Add Location");
    }

    private boolean requireSupervisorApproval(
            String title,
            String description,
            String actionType,
            Integer entityId,
            Set<UserRole> allowedApproverRoles) {
        var result = SupervisorApprovalDialogs.requestApproval(
                title,
                description,
                actionType,
                "LOCATION",
                entityId,
                allowedApproverRoles);
        if (!result.approved()) {
            if (result.message() != null && !"Approval cancelled.".equals(result.message())) {
                showAlert(Alert.AlertType.WARNING, "Approval Needed", result.message());
            }
            return false;
        }
        return true;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
