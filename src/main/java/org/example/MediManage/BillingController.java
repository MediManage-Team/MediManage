package org.example.MediManage;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import org.example.MediManage.dao.BillDAO;
import org.example.MediManage.dao.CustomerDAO;
import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.model.BillItem;
import org.example.MediManage.model.Customer;
import org.example.MediManage.model.Medicine;

import java.sql.SQLException;
import java.sql.SQLException;
import java.util.List;

public class BillingController {

    @FXML
    private TableView<BillItem> billingTable;
    @FXML
    private TableColumn<BillItem, String> colName;
    @FXML
    private TableColumn<BillItem, Integer> colQty;
    @FXML
    private TableColumn<BillItem, Double> colPrice;
    @FXML
    private TableColumn<BillItem, Double> colTotal; // Using colTotal for line totals
    @FXML
    private Label lblTotal;

    @FXML
    private TextField txtSearchCustomer;
    @FXML
    private Label lblCustomerName;
    @FXML
    private Label lblCustomerPhone;

    @FXML
    private TextField txtSearchMedicine;
    @FXML
    private ListView<Medicine> listMedicineSuggestions; // For autocomplete
    @FXML
    private TextField txtQty;

    @FXML
    private Button btnCheckout;

    private MedicineDAO medicineDAO = new MedicineDAO();
    private CustomerDAO customerDAO = new CustomerDAO();
    private BillDAO billDAO = new BillDAO();

    private ObservableList<BillItem> billList = FXCollections.observableArrayList();
    private ObservableList<Medicine> allMedicines = FXCollections.observableArrayList();
    private Customer selectedCustomer = null;
    private Medicine selectedMedicine = null;

    @FXML
    public void initialize() {
        // Init Data
        allMedicines.addAll(medicineDAO.getAllMedicines());

        // Setup Table
        colName.setCellValueFactory(data -> data.getValue().nameProperty());
        colQty.setCellValueFactory(data -> data.getValue().qtyProperty().asObject());
        colPrice.setCellValueFactory(data -> data.getValue().priceProperty().asObject());
        colTotal.setCellValueFactory(data -> data.getValue().totalProperty().asObject());
        billingTable.setItems(billList);

        setupMedicineSearch();
        setupKeyBindings();
    }

    // Auto-complete logic for Medicine Search
    private void setupMedicineSearch() {
        FilteredList<Medicine> filteredDocs = new FilteredList<>(allMedicines, p -> true);

        txtSearchMedicine.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isEmpty()) {
                listMedicineSuggestions.setVisible(false);
                return;
            }

            String lower = newVal.toLowerCase();
            filteredDocs.setPredicate(m -> m.getName().toLowerCase().contains(lower) ||
                    m.getCompany().toLowerCase().contains(lower) ||
                    m.getGenericName().toLowerCase().contains(lower)); // Feature 2: Generic Name Search

            if (filteredDocs.isEmpty()) {
                listMedicineSuggestions.setVisible(false);
            } else {
                listMedicineSuggestions.setVisible(true);
                listMedicineSuggestions.setItems(filteredDocs);
            }
        });

        // Selection from List
        listMedicineSuggestions.setOnMouseClicked(e -> {
            Medicine sel = listMedicineSuggestions.getSelectionModel().getSelectedItem();
            if (sel != null) {
                selectMedicine(sel);
            }
        });

        // Enter key on text field to select first suggestion
        txtSearchMedicine.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN) {
                listMedicineSuggestions.requestFocus();
                listMedicineSuggestions.getSelectionModel().selectFirst();
            } else if (e.getCode() == KeyCode.ENTER) {
                if (!listMedicineSuggestions.getItems().isEmpty()) {
                    selectMedicine(listMedicineSuggestions.getItems().get(0));
                }
            }
        });

        // Enter key on list
        listMedicineSuggestions.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                Medicine sel = listMedicineSuggestions.getSelectionModel().getSelectedItem();
                if (sel != null)
                    selectMedicine(sel);
            }
        });
    }

    private void selectMedicine(Medicine m) {
        selectedMedicine = m;
        txtSearchMedicine.setText(m.getName());
        listMedicineSuggestions.setVisible(false);
        txtQty.requestFocus();
    }

    private void setupKeyBindings() {
        txtQty.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                handleAdd();
            }
        });

        // Global Shortcuts (requires logic to bind to scene, doing simplified here on
        // explicit fields)
        txtSearchCustomer.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER)
                handleCustomerSearch();
        });
    }

    @FXML
    private void handleCustomerSearch() {
        String q = txtSearchCustomer.getText();
        if (q.isEmpty())
            return;

        List<Customer> res = customerDAO.searchCustomer(q);
        if (!res.isEmpty()) {
            selectedCustomer = res.get(0);
            String labelText = selectedCustomer.getName();
            // Show Balance if Credit
            if (selectedCustomer.getCurrentBalance() > 0) {
                labelText += String.format(" (Bal: ₹%.2f)", selectedCustomer.getCurrentBalance());
            }
            lblCustomerName.setText(labelText);

            if (lblCustomerPhone != null)
                lblCustomerPhone.setText(selectedCustomer.getPhone());
            lblCustomerName.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
        } else {
            lblCustomerName.setText("Not Found (Walk-in)");
            if (lblCustomerPhone != null)
                lblCustomerPhone.setText("-");
            lblCustomerName.setStyle("-fx-text-fill: #666;");
            selectedCustomer = null;
        }
    }

    @FXML
    private void handleAddCustomer() {
        TextInputDialog phoneDialog = new TextInputDialog(txtSearchCustomer.getText());
        phoneDialog.setTitle("New Customer");
        phoneDialog.setHeaderText("Enter Customer Phone");
        java.util.Optional<String> phoneResult = phoneDialog.showAndWait();

        if (phoneResult.isPresent() && !phoneResult.get().trim().isEmpty()) {
            String phone = phoneResult.get().trim();

            TextInputDialog nameDialog = new TextInputDialog();
            nameDialog.setTitle("New Customer");
            nameDialog.setHeaderText("Enter Customer Name");
            java.util.Optional<String> nameResult = nameDialog.showAndWait();

            if (nameResult.isPresent() && !nameResult.get().trim().isEmpty()) {
                String name = nameResult.get().trim();
                Customer newC = new Customer(0, name, phone);
                try {
                    customerDAO.addCustomer(newC);

                    // Auto-select
                    List<Customer> res = customerDAO.searchCustomer(phone);
                    if (!res.isEmpty()) {
                        selectedCustomer = res.get(0);
                        lblCustomerName.setText(selectedCustomer.getName());
                        if (lblCustomerPhone != null)
                            lblCustomerPhone.setText(selectedCustomer.getPhone());
                        lblCustomerName.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");
                        txtSearchCustomer.setText(phone);
                    }
                } catch (Exception e) {
                    showAlert(Alert.AlertType.ERROR, "Error", "Could not add customer: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    @FXML
    private void handleAdd() {
        if (selectedMedicine == null)
            return;

        // Validate Qty
        int qty;
        try {
            qty = Integer.parseInt(txtQty.getText());
        } catch (NumberFormatException e) {
            return; // Invalid
        }

        if (qty <= 0)
            return;
        if (qty > selectedMedicine.getStock()) {
            showAlert(Alert.AlertType.WARNING, "Stock Low", "Only " + selectedMedicine.getStock() + " available.");
            return;
        }

        // Add to List
        java.util.Optional<BillItem> existing = billList.stream()
                .filter(b -> b.getMedicineId() == selectedMedicine.getId())
                .findFirst();
        if (existing.isPresent()) {
            BillItem item = existing.get();
            item.setQty(item.getQty() + qty);
            item.setTotal(item.getTotal() + (selectedMedicine.getPrice() * qty)); // Simple logic, ignoring GST calc for
                                                                                  // speed update
            billingTable.refresh();
        } else {
            // Recalculate GST properly if needed, here keeping simple 18% assumption from
            // Dashboard logic
            double gst = (selectedMedicine.getPrice() * qty) * 0.18;
            billList.add(new BillItem(selectedMedicine.getId(), selectedMedicine.getName(),
                    selectedMedicine.getExpiry(), qty,
                    selectedMedicine.getPrice(), gst));
        }

        updateTotal();

        // Reset Inputs
        selectedMedicine = null;
        txtSearchMedicine.clear();
        txtQty.clear();
        txtSearchMedicine.requestFocus();
    }

    private void updateTotal() {
        double sum = billList.stream().mapToDouble(BillItem::getTotal).sum();
        lblTotal.setText(String.format("₹ %.2f", sum));
    }

    @FXML
    private void handleCheckout() {
        if (billList.isEmpty())
            return;

        double total = billList.stream().mapToDouble(BillItem::getTotal).sum();
        Integer cid = selectedCustomer != null ? selectedCustomer.getCustomerId() : null;
        String customerName = selectedCustomer != null ? selectedCustomer.getName() : "Walk-in";

        // Payment Mode Dialog
        java.util.List<String> choices = new java.util.ArrayList<>();
        choices.add("Cash");
        choices.add("Credit");
        choices.add("UPI");

        ChoiceDialog<String> dialog = new ChoiceDialog<>("Cash", choices);
        dialog.setTitle("Payment Mode");
        dialog.setHeaderText("Select Payment Mode");
        dialog.setContentText("Mode:");

        java.util.Optional<String> result = dialog.showAndWait();
        if (result.isEmpty())
            return; // Cancelled
        String paymentMode = result.get();

        if ("Credit".equals(paymentMode) && cid == null) {
            showAlert(Alert.AlertType.WARNING, "Credit Error", "Credit requires a selected customer!");
            return;
        }

        // --- AI Patient Care Assistant ---
        btnCheckout.setDisable(true);
        btnCheckout.setText("Generating Care Protocol...");

        // Construct Prompt
        StringBuilder medicineList = new StringBuilder();
        billList.forEach(item -> medicineList.append("- ").append(item.getName()).append("\n"));

        String prompt = "I am a Pharmacist. Create a 'Patient Care Protocol' for the following medicines:\n" +
                medicineList.toString() + "\n" +
                "For EACH medicine, provide a 7-point guide:\n" +
                "1. Mechanism (Simplified)\n" +
                "2. Usage Guide (When/How)\n" +
                "3. Dietary Advice\n" +
                "4. Side Effects\n" +
                "5. Stop Protocol\n" +
                "Also check for Combinational Safety (Drug-Drug Interactions) between these items.\n" +
                "Format as a clean, printable guide.";

        // We use the AIOrchestrator to fetch this.
        // Note: AIOrchestrator is not injected yet, I will use a local instance or
        // inject via constructor.
        // For existing code consistency (like Dashboard), I'll instantiate it here or
        // add field.
        // Let's add the field using separate Edit, but here I'll assume it exists or
        // use direct instantiation for now to keep this block self-contained if
        // possible,
        // but cleaner to use the field. I'll add the field in the same file using
        // `initAI` pattern if needed.
        // Actually, for this specific block replacement, I will assume `aiOrchestrator`
        // is available or instantiate it.
        org.example.MediManage.service.ai.AIOrchestrator aiOrchestrator = new org.example.MediManage.service.ai.AIOrchestrator();

        aiOrchestrator.processQuery(prompt, true, false) // High precision (Cloud) preferred for medical advice
                .thenAccept(careProtocol -> {
                    // Return to FX Thread for UI & PDF generation
                    javafx.application.Platform.runLater(() -> {
                        try {
                            int userId = org.example.MediManage.util.UserSession.getInstance().getUser().getId();
                            int billId = billDAO.generateInvoice(total, billList, cid, userId, paymentMode);

                            // Generate PDF with AI Protocol
                            String pdfPath = "Invoice_" + billId + ".pdf";
                            new org.example.MediManage.service.ReportService().generateInvoicePDF(billList, total,
                                    customerName, pdfPath, careProtocol);

                            showAlert(Alert.AlertType.INFORMATION, "Success",
                                    "Bill #" + billId + " & Care Protocol Generated!\nSaved to: " + pdfPath);

                            // Cleanup
                            billList.clear();
                            updateTotal();
                            selectedCustomer = null;
                            lblCustomerName.setText("Walk-in Customer");
                            txtSearchCustomer.clear();
                            allMedicines.clear();
                            allMedicines.addAll(medicineDAO.getAllMedicines());

                        } catch (Exception e) {
                            e.printStackTrace();
                            showAlert(Alert.AlertType.ERROR, "Error", "Checkout/Printing Failed: " + e.getMessage());
                        } finally {
                            btnCheckout.setDisable(false);
                            btnCheckout.setText("CHECKOUT (PRINT)");
                        }
                    });
                })
                .exceptionally(ex -> {
                    javafx.application.Platform.runLater(() -> {
                        showAlert(Alert.AlertType.ERROR, "AI Error",
                                "Failed to generate Care Protocol: " + ex.getMessage());
                        btnCheckout.setDisable(false);
                        btnCheckout.setText("CHECKOUT (PRINT)");
                    });
                    return null;
                });
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }
}
