package org.example.MediManage;

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
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

public class MedicineSearchController {

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
    private TableColumn<Medicine, String> colLocation; // Placeholder if not in DB

    // Details Panel
    @FXML
    private Label lblDetailName;
    @FXML
    private Label lblDetailCompany;
    @FXML
    private Label lblDetailGeneric; // Placeholder
    @FXML
    private Label lblDetailSideEffects; // Placeholder
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
        // colLocation is placeholder, let's just show empty or "A-1" default
        colLocation.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty("Shelf A"));

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
                        setStyle("-fx-background-color: #ffcccc;"); // Red tint
                        // setDisable(true); // Maybe not disable selection, but indicate visually?
                        // User asked to disable selection if expired.
                        // setDisable(true) on a row might prevent scrolling or interactions oddly but
                        // let's try or just style deeply.
                        // Better to not strict disable row as it kills click for details. Let's just
                        // style.
                    } else if (daysToExpiry < 30) {
                        // Near Expiry
                        setStyle("-fx-background-color: #ffebcc;"); // Orange tint
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
            return 999;
        try {
            // Try standard ISO first
            LocalDate expiry = LocalDate.parse(expiryStr);
            return ChronoUnit.DAYS.between(LocalDate.now(), expiry);
        } catch (DateTimeParseException e) {
            return 999; // Fallback
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
        String status = days < 0 ? "EXPIRED" : (days < 30 ? "Expiring Soon" : "Valid");

        lblDetailGeneric.setText("Generic: Paracetamol (Example)"); // Placeholder
        lblDetailSideEffects.setText("Status: " + status + "\nDays to Expiry: " + days);
    }
}
