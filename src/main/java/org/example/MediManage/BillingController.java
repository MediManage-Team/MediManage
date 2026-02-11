package org.example.MediManage;

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
import java.util.List;
import javafx.scene.layout.VBox;

import javafx.geometry.Pos;
import javafx.geometry.Insets;

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

    @FXML
    private VBox aiContentBox;

    private MedicineDAO medicineDAO = new MedicineDAO();
    private CustomerDAO customerDAO = new CustomerDAO();
    private BillDAO billDAO = new BillDAO();
    private org.example.MediManage.service.GeminiService geminiService = new org.example.MediManage.service.GeminiService();

    private ObservableList<BillItem> billList = FXCollections.observableArrayList();
    private ObservableList<Medicine> allMedicines = FXCollections.observableArrayList();
    private Customer selectedCustomer = null;
    private Medicine selectedMedicine = null;

    @FXML
    public void handleAiInsights() {
        if (billList.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Cart Empty", "Please add medicines to the cart first.");
            return;
        }

        aiContentBox.getChildren().clear();
        aiContentBox.getChildren().add(new Label("Generating insights..."));

        List<BillItem> items = new java.util.ArrayList<>(billList);
        geminiService.generateCareProtocol(items)
                .thenAccept(jsonResponse -> {
                    javafx.application.Platform.runLater(() -> {
                        renderAiNodes(jsonResponse);
                    });
                })
                .exceptionally(ex -> {
                    javafx.application.Platform.runLater(() -> {
                        aiContentBox.getChildren().clear();
                        Label err = new Label("Error: " + ex.getMessage());
                        err.setWrapText(true);
                        err.setStyle("-fx-text-fill: red;");
                        aiContentBox.getChildren().add(err);
                        ex.printStackTrace();
                    });
                    return null;
                });
    }

    private void renderAiNodes(String jsonResponse) {
        aiContentBox.getChildren().clear();
        try {
            // Clean markdown code blocks if present
            String cleanJson = jsonResponse.replace("```json", "").replace("```", "").trim();
            com.google.gson.JsonArray array = com.google.gson.JsonParser.parseString(cleanJson).getAsJsonArray();

            for (com.google.gson.JsonElement elem : array) {
                com.google.gson.JsonObject obj = elem.getAsJsonObject();
                aiContentBox.getChildren().add(createMedicineNode(obj));
            }
        } catch (Exception e) {
            Label err = new Label("Failed to parse AI response. Raw output:\n" + jsonResponse);
            err.setWrapText(true);
            aiContentBox.getChildren().add(err);
            e.printStackTrace();
        }
    }

    private VBox createMedicineNode(com.google.gson.JsonObject obj) {
        VBox node = new VBox(10);
        node.setStyle(
                "-fx-background-color: white; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 2); -fx-background-radius: 8; -fx-padding: 15;");

        String name = obj.has("medicineName") ? obj.get("medicineName").getAsString() : "Medicine";
        Label title = new Label(name);
        title.setStyle(
                "-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: #2c3e50; -fx-padding: 0 0 5 0; -fx-border-color: transparent transparent #eee transparent; -fx-border-width: 2;");
        title.setMaxWidth(Double.MAX_VALUE);
        node.getChildren().add(title);

        if (obj.has("substitutes")) {
            node.getChildren()
                    .add(createDetailNode("Substitutes", obj.get("substitutes").getAsString(), "#e8f8f5", "#16a085"));
        }

        if (obj.has("mechanism"))
            node.getChildren()
                    .add(createDetailNode("Mechanism", obj.get("mechanism").getAsString(), "#f8f9fa", "#7f8c8d"));
        if (obj.has("usage"))
            node.getChildren()
                    .add(createDetailNode("Usage Guide", obj.get("usage").getAsString(), "#eafaf1", "#27ae60"));
        if (obj.has("dietary"))
            node.getChildren()
                    .add(createDetailNode("Dietary Advice", obj.get("dietary").getAsString(), "#fff8e1", "#f1c40f"));
        if (obj.has("sideEffects"))
            node.getChildren()
                    .add(createDetailNode("Side Effects", obj.get("sideEffects").getAsString(), "#fce4ec", "#c2185b"));

        if (obj.has("safety")) {
            String safety = obj.get("safety").getAsString();
            node.getChildren().add(createDetailNode("Safety Check", safety, "#fff3cd", "#856404"));
        }

        if (obj.has("stopProtocol"))
            node.getChildren().add(
                    createDetailNode("Stop Protocol", obj.get("stopProtocol").getAsString(), "#ffebee", "#c0392b"));

        return node;
    }

    private VBox createDetailNode(String title, String content, String bgColor, String titleColor) {
        VBox box = new VBox(3);
        box.setStyle(
                "-fx-background-color: " + bgColor + "; -fx-background-radius: 5; -fx-padding: 8; -fx-border-color: "
                        + bgColor + "; -fx-border-radius: 5;");

        Label lblTitle = new Label(title);
        lblTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: " + titleColor + ";");

        Label lblContent = new Label(content);
        lblContent.setWrapText(true);
        lblContent.setStyle("-fx-font-size: 13px; -fx-text-fill: #333;");

        box.getChildren().addAll(lblTitle, lblContent);
        return box;
    }

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

        try {
            // Ask for Payment Mode
            java.util.List<String> choices = new java.util.ArrayList<>();
            choices.add("Cash");
            choices.add("Credit");
            choices.add("UPI");

            ChoiceDialog<String> dialog = new ChoiceDialog<>("Cash", choices);
            dialog.setTitle("Payment Mode");
            dialog.setHeaderText("Select Payment Mode");
            dialog.setContentText("Mode:");

            java.util.Optional<String> result = dialog.showAndWait();
            String paymentMode = result.orElse("Cash");

            // Feature 4: Credit validation
            if ("Credit".equals(paymentMode) && cid == null) {
                showAlert(Alert.AlertType.WARNING, "Credit Error", "Credit requires a selected customer!");
                return;
            }

            int userId = org.example.MediManage.util.UserSession.getInstance().getUser().getId();
            int billId = billDAO.generateInvoice(total, billList, cid, userId, paymentMode);
            showAlert(Alert.AlertType.INFORMATION, "Success", "Bill Generated: #" + billId + "\nMode: " + paymentMode);
            billList.clear();
            updateTotal();
            selectedCustomer = null;
            lblCustomerName.setText("Walk-in Customer");
            txtSearchCustomer.clear();

            // Refresh Inventory Data just in case
            allMedicines.clear();
            allMedicines.addAll(medicineDAO.getAllMedicines());

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Checkout Failed");
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }
}
