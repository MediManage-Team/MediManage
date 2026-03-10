package org.example.MediManage.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.model.Medicine;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

public class MedicineSearchController {
    private static final long UNKNOWN_EXPIRY_DAYS = Long.MAX_VALUE;

    @FXML
    private TextField txtSearch;

    // Table
    @FXML
    private TableView<Medicine> searchTable;
    @FXML
    private TableColumn<Medicine, String> colName;
    @FXML
    private TableColumn<Medicine, String> colCompany;
    @FXML
    private TableColumn<Medicine, String> colExpiry;
    @FXML
    private TableColumn<Medicine, Integer> colStock;
    @FXML
    private TableColumn<Medicine, String> colLocation;

    // Details Panel
    @FXML
    private Label lblDetailName;
    @FXML
    private Label lblDetailCompany;
    @FXML
    private Label lblDetailGeneric;
    @FXML
    private Label lblDetailSideEffects;
    @FXML
    private VBox detailsPanel;

    private MedicineDAO medicineDAO = new MedicineDAO();
    private ObservableList<Medicine> masterData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTable();
        loadData();
        setupSearch();
    }

    private void setupTable() {
        colName.setCellValueFactory(data -> data.getValue().nameProperty());
        colCompany.setCellValueFactory(data -> data.getValue().companyProperty());
        colExpiry.setCellValueFactory(data -> data.getValue().expiryProperty());
        colStock.setCellValueFactory(data -> data.getValue().stockProperty().asObject());
        colLocation.setCellValueFactory(data -> {
            String generic = data.getValue().getGenericName();
            if (generic == null || generic.isBlank()) {
                generic = "-";
            }
            return new javafx.beans.property.SimpleStringProperty(generic);
        });

        // Row Highlighting for Expiry
        searchTable.setRowFactory(tv -> new TableRow<Medicine>() {
            @Override
            protected void updateItem(Medicine item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setStyle("");
                    setDisable(false);
                } else {
                    long daysToExpiry = getDaysToExpiry(item.getExpiry());

                    if (daysToExpiry < 0) {
                        // Expired
                        setStyle("-fx-background-color: #ff6b6b20;"); // Expired (dark red tint)
                        // setDisable(true); // Maybe not disable selection, but indicate visually?
                        // User asked to disable selection if expired.
                        // setDisable(true) on a row might prevent scrolling or interactions oddly but
                        // let's try or just style deeply.
                        // Better to not strict disable row as it kills click for details. Let's just
                        // style.
                    } else if (daysToExpiry < 30) {
                        // Near Expiry
                        setStyle("-fx-background-color: #e8c66a20;"); // Near expiry (dark amber tint)
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        searchTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                showDetails(newVal);
            } else {
                detailsPanel.setVisible(false);
            }
        });
    }

    private long getDaysToExpiry(String expiryStr) {
        if (expiryStr == null || expiryStr.isEmpty())
            return UNKNOWN_EXPIRY_DAYS;
        try {
            // Try standard ISO first
            LocalDate expiry = LocalDate.parse(expiryStr);
            return ChronoUnit.DAYS.between(LocalDate.now(), expiry);
        } catch (DateTimeParseException e) {
            return UNKNOWN_EXPIRY_DAYS;
        }
    }

    private void loadData() {
        masterData.setAll(medicineDAO.getAllMedicines());
        searchTable.setItems(masterData);
    }

    private void setupSearch() {
        FilteredList<Medicine> filteredData = new FilteredList<>(masterData, p -> true);

        txtSearch.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredData.setPredicate(med -> {
                if (newVal == null || newVal.isEmpty())
                    return true;
                String lower = newVal.toLowerCase();
                return med.getName().toLowerCase().contains(lower) ||
                        med.getCompany().toLowerCase().contains(lower);
            });
        });

        SortedList<Medicine> sortedData = new SortedList<>(filteredData);
        sortedData.comparatorProperty().bind(searchTable.comparatorProperty());
        searchTable.setItems(sortedData);
    }

    private void showDetails(Medicine med) {
        detailsPanel.setVisible(true);
        lblDetailName.setText(med.getName());
        lblDetailCompany.setText(med.getCompany());

        long days = getDaysToExpiry(med.getExpiry());
        String status = days == UNKNOWN_EXPIRY_DAYS ? "Expiry Not Available"
                : (days < 0 ? "EXPIRED" : (days < 30 ? "Expiring Soon" : "Valid"));

        String genericName = med.getGenericName();
        if (genericName == null || genericName.isBlank()) {
            genericName = "N/A";
        }
        lblDetailGeneric.setText("Generic: " + genericName);

        String expiryText = med.getExpiry();
        if (expiryText == null || expiryText.isBlank()) {
            expiryText = "N/A";
        }
        String daysText;
        if (days == UNKNOWN_EXPIRY_DAYS) {
            daysText = "N/A";
        } else if (days >= 0) {
            daysText = days + " day(s) left";
        } else {
            daysText = Math.abs(days) + " day(s) overdue";
        }

        lblDetailSideEffects.setText(
                "Status: " + status
                        + "\nStock: " + med.getStock() + " unit(s)"
                        + "\nPrice: ₹" + String.format("%.2f", med.getPrice())
                        + "\nExpiry: " + expiryText + " (" + daysText + ")");
    }
}
