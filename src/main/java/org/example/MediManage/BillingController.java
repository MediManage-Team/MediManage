package org.example.MediManage;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.example.MediManage.model.BillItem;
import org.example.MediManage.model.Customer;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.service.BillingService;

import java.util.List;

public class BillingController {
    private static final String CUSTOMER_NAME_SELECTED_CLASS = "customer-name-selected";
    private static final String CUSTOMER_NAME_MUTED_CLASS = "customer-name-muted";
    private static final String WALK_IN_CUSTOMER_LABEL = "Walk-in Customer";
    private static final String CHECKOUT_READY_LABEL = "CHECKOUT (PRINT)";
    private static final String CHECKOUT_BUSY_LABEL = "Generating Care Protocol...";
    private static final String CARE_PROTOCOL_READY_LABEL = "✨ Generate Care Protocol";
    private static final String CARE_PROTOCOL_BUSY_LABEL = "⏳ Generating...";

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
    private javafx.scene.layout.VBox careProtocolContainer;
    @FXML
    private Button btnGenerateCareProtocol;

    private final BillingService billingService = new BillingService();

    private ObservableList<BillItem> billList = FXCollections.observableArrayList();
    private ObservableList<Medicine> allMedicines = FXCollections.observableArrayList();
    private Customer selectedCustomer = null;
    private Medicine selectedMedicine = null;
    private boolean keyboardShortcutsRegistered = false;

    @FXML
    public void initialize() {
        // Init Data
        allMedicines.addAll(billingService.loadActiveMedicines());

        // Setup Table
        colName.setCellValueFactory(data -> data.getValue().nameProperty());
        colQty.setCellValueFactory(data -> data.getValue().qtyProperty().asObject());
        colPrice.setCellValueFactory(data -> data.getValue().priceProperty().asObject());
        colTotal.setCellValueFactory(data -> data.getValue().totalProperty().asObject());
        billingTable.setItems(billList);

        setupMedicineSearch();
        setupKeyBindings();
        setupGlobalShortcuts();
        applyCustomerNameStyle(false);
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

    private void setupGlobalShortcuts() {
        Platform.runLater(() -> {
            if (keyboardShortcutsRegistered || txtSearchMedicine == null || txtSearchMedicine.getScene() == null) {
                return;
            }
            txtSearchMedicine.getScene().addEventFilter(KeyEvent.KEY_PRESSED, this::handleGlobalShortcut);
            keyboardShortcutsRegistered = true;
        });
    }

    private void handleGlobalShortcut(KeyEvent event) {
        if (event.isControlDown() && event.getCode() == KeyCode.F) {
            txtSearchMedicine.requestFocus();
            txtSearchMedicine.selectAll();
            event.consume();
            return;
        }

        if (event.isControlDown() && event.getCode() == KeyCode.L) {
            txtSearchCustomer.requestFocus();
            txtSearchCustomer.selectAll();
            event.consume();
            return;
        }

        if (event.isControlDown() && event.getCode() == KeyCode.ENTER && !btnCheckout.isDisable() && !billList.isEmpty()) {
            handleCheckout();
            event.consume();
            return;
        }

        if (event.getCode() == KeyCode.ESCAPE) {
            clearMedicineSelection();
            event.consume();
        }
    }

    private void clearMedicineSelection() {
        selectedMedicine = null;
        txtSearchMedicine.clear();
        txtQty.clear();
        listMedicineSuggestions.setVisible(false);
    }

    private void applyCustomerNameStyle(boolean selected) {
        if (lblCustomerName == null) {
            return;
        }
        lblCustomerName.getStyleClass().removeAll(CUSTOMER_NAME_SELECTED_CLASS, CUSTOMER_NAME_MUTED_CLASS);
        lblCustomerName.getStyleClass().add(selected ? CUSTOMER_NAME_SELECTED_CLASS : CUSTOMER_NAME_MUTED_CLASS);
    }

    @FXML
    private void handleCustomerSearch() {
        String q = txtSearchCustomer.getText();
        if (q.isEmpty())
            return;

        List<Customer> res = billingService.searchCustomers(q);
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
            applyCustomerNameStyle(true);
        } else {
            lblCustomerName.setText("Not Found (Walk-in)");
            if (lblCustomerPhone != null)
                lblCustomerPhone.setText("-");
            applyCustomerNameStyle(false);
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
                try {
                    selectedCustomer = billingService.addCustomerAndFind(name, phone);
                    if (selectedCustomer != null) {
                        lblCustomerName.setText(selectedCustomer.getName());
                        if (lblCustomerPhone != null)
                            lblCustomerPhone.setText(selectedCustomer.getPhone());
                        applyCustomerNameStyle(true);
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

        BillingService.AddItemResult result = billingService.addMedicineToBill(billList, selectedMedicine, qty);
        if (result.status() == BillingService.AddItemStatus.OUT_OF_STOCK) {
            showAlert(Alert.AlertType.WARNING, "Stock Low", "Only " + result.availableStock() + " available.");
            return;
        }
        if (result.status() != BillingService.AddItemStatus.ADDED) {
            return;
        }
        if (result.requiresTableRefresh()) {
            billingTable.refresh();
        }

        updateTotal();

        // Reset Inputs
        selectedMedicine = null;
        txtSearchMedicine.clear();
        txtQty.clear();
        txtSearchMedicine.requestFocus();
    }

    private void updateTotal() {
        double sum = billingService.calculateTotal(billList);
        lblTotal.setText(String.format("₹ %.2f", sum));
    }

    @FXML
    private void handleCheckout() {
        if (billList.isEmpty())
            return;

        Integer cid = selectedCustomer != null ? selectedCustomer.getCustomerId() : null;

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
        btnCheckout.setText(CHECKOUT_BUSY_LABEL);

        List<BillItem> checkoutItems = billingService.snapshotItems(billList);
        billingService.generateCheckoutCareProtocol(checkoutItems)
                .thenAccept(careProtocol -> {
                    // Return to FX Thread for UI & PDF generation
                    Platform.runLater(() -> {
                        try {
                            int userId = org.example.MediManage.util.UserSession.getInstance().getUser().getId();
                            BillingService.CheckoutResult checkoutResult = billingService.completeCheckout(
                                    checkoutItems,
                                    selectedCustomer,
                                    userId,
                                    paymentMode,
                                    careProtocol);

                            showAlert(Alert.AlertType.INFORMATION, "Success",
                                    "Bill #" + checkoutResult.billId()
                                            + " & Care Protocol Generated!\nSaved to: " + checkoutResult.pdfPath());

                            // Cleanup
                            billList.clear();
                            updateTotal();
                            selectedCustomer = null;
                            lblCustomerName.setText(WALK_IN_CUSTOMER_LABEL);
                            applyCustomerNameStyle(false);
                            if (lblCustomerPhone != null) {
                                lblCustomerPhone.setText("-");
                            }
                            txtSearchCustomer.clear();
                            allMedicines.clear();
                            allMedicines.addAll(billingService.loadActiveMedicines());

                        } catch (Exception e) {
                            e.printStackTrace();
                            showAlert(Alert.AlertType.ERROR, "Error", "Checkout/Printing Failed: " + e.getMessage());
                        } finally {
                            btnCheckout.setDisable(false);
                            btnCheckout.setText(CHECKOUT_READY_LABEL);
                        }
                    });
                })
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        showAlert(Alert.AlertType.ERROR, "AI Error",
                                "Failed to generate Care Protocol: " + ex.getMessage());
                        btnCheckout.setDisable(false);
                        btnCheckout.setText(CHECKOUT_READY_LABEL);
                    });
                    return null;
                });
    }

    @FXML
    private void handleGenerateCareProtocol() {
        if (billList.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Empty Bill", "Add items to the bill first.");
            return;
        }

        btnGenerateCareProtocol.setDisable(true);
        btnGenerateCareProtocol.setText(CARE_PROTOCOL_BUSY_LABEL);
        careProtocolContainer.getChildren().clear();
        javafx.scene.control.Label loadingLabel = new javafx.scene.control.Label(
                "🔄 Generating AI Care Protocol...\nThis may take a few seconds.");
        loadingLabel.getStyleClass().add("care-loading-label");
        careProtocolContainer.getChildren().add(loadingLabel);

        String providerInfo = billingService.getCloudProviderInfo();
        List<BillItem> careItems = billingService.snapshotItems(billList);

        billingService.generateDetailedCareProtocol(careItems)
                .thenAccept(protocol -> {
                    Platform.runLater(() -> {
                        buildCareProtocolCards(protocol, providerInfo);
                        btnGenerateCareProtocol.setDisable(false);
                        btnGenerateCareProtocol.setText(CARE_PROTOCOL_READY_LABEL);
                    });
                })
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        careProtocolContainer.getChildren().clear();
                        javafx.scene.control.Label errLabel = new javafx.scene.control.Label(
                                "❌ Error: " + ex.getMessage()
                                        + "\n\n💡 Tip: Configure your Cloud AI API key in Settings.");
                        errLabel.setWrapText(true);
                        errLabel.getStyleClass().add("care-error-label");
                        careProtocolContainer.getChildren().add(errLabel);
                        btnGenerateCareProtocol.setDisable(false);
                        btnGenerateCareProtocol.setText(CARE_PROTOCOL_READY_LABEL);
                    });
                    return null;
                });
    }

    private void buildCareProtocolCards(String raw, String providerInfo) {
        careProtocolContainer.getChildren().clear();
        String text = raw.replaceAll("\\*\\*(.+?)\\*\\*", "$1").replaceAll("(?m)^#{1,3}\\s*", "").trim();

        // Provider header
        javafx.scene.layout.VBox headerCard = new javafx.scene.layout.VBox(3);
        headerCard.getStyleClass().add("care-header-card");
        javafx.scene.control.Label headerTitle = new javafx.scene.control.Label("🏥 Patient Care Protocol");
        headerTitle.getStyleClass().add("care-header-title");
        javafx.scene.control.Label headerProv = new javafx.scene.control.Label("☁️ " + providerInfo);
        headerProv.getStyleClass().add("care-header-provider");
        headerCard.getChildren().addAll(headerTitle, headerProv);
        careProtocolContainer.getChildren().add(headerCard);

        java.util.Map<String, String[]> sc = new java.util.LinkedHashMap<>();
        sc.put("substitutes", new String[] { "#5fe6b3", "#0f2920", "#5fe6b380" });
        sc.put("mechanism", new String[] { "#00d4ff", "#0f1724", "#00d4ff80" });
        sc.put("usage guide", new String[] { "#7aa2f7", "#0f1530", "#7aa2f780" });
        sc.put("dietary advice", new String[] { "#e8c66a", "#1a1a0f", "#e8c66a80" });
        sc.put("side effects", new String[] { "#ff6b6b", "#1a0f0f", "#ff6b6b80" });
        sc.put("safety check", new String[] { "#5fe6b3", "#0f2018", "#5fe6b380" });
        sc.put("stop protocol", new String[] { "#ff6b6b", "#200f0f", "#ff6b6b80" });
        sc.put("special precautions", new String[] { "#bb9af7", "#1a0f30", "#bb9af780" });
        sc.put("monitoring", new String[] { "#7aa2f7", "#0f1530", "#7aa2f780" });
        sc.put("combinational safety", new String[] { "#ff6b6b", "#200f0f", "#ff6b6b80" });
        sc.put("drug-drug interaction", new String[] { "#ff6b6b", "#200f0f", "#ff6b6b80" });

        String[] lines = text.split("\\n");
        String currentSection = null;
        StringBuilder currentContent = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty())
                continue;
            String lowerLine = trimmed.toLowerCase().replaceFirst("^\\d+\\.?\\s*", "");
            String matchedSection = null;
            for (String key : sc.keySet()) {
                if (lowerLine.startsWith(key)) {
                    matchedSection = key;
                    break;
                }
            }
            if (matchedSection != null) {
                if (currentSection != null && currentContent.length() > 0) {
                    careProtocolContainer.getChildren()
                            .add(createSectionCard(currentSection, currentContent.toString().trim(), sc));
                }
                currentSection = matchedSection;
                int colonIdx = trimmed.indexOf(':');
                currentContent = new StringBuilder(
                        colonIdx >= 0 && colonIdx < trimmed.length() - 1 ? trimmed.substring(colonIdx + 1).trim() : "");
            } else if (trimmed.matches("^[A-Z].*(?:Tablet|Capsule|Syrup|Injection|Cream|Drops|Gel|mg|ml|Sugar Free).*$")
                    && !trimmed.contains(":")) {
                if (currentSection != null && currentContent.length() > 0) {
                    careProtocolContainer.getChildren()
                            .add(createSectionCard(currentSection, currentContent.toString().trim(), sc));
                    currentSection = null;
                    currentContent = new StringBuilder();
                }
                javafx.scene.control.Label medLabel = new javafx.scene.control.Label("💊 " + trimmed);
                medLabel.getStyleClass().add("care-medicine-title");
                careProtocolContainer.getChildren().add(medLabel);
            } else {
                if (currentContent.length() > 0)
                    currentContent.append("\n");
                currentContent.append(trimmed);
            }
        }
        if (currentSection != null && currentContent.length() > 0) {
            careProtocolContainer.getChildren()
                    .add(createSectionCard(currentSection, currentContent.toString().trim(), sc));
        }
        javafx.scene.control.Label footer = new javafx.scene.control.Label("⚕️ Generated by MediManage AI");
        footer.getStyleClass().add("care-footer");
        careProtocolContainer.getChildren().add(footer);
    }

    private javafx.scene.layout.VBox createSectionCard(String sectionKey, String content,
            java.util.Map<String, String[]> colorMap) {
        String[] colors = colorMap.getOrDefault(sectionKey, new String[] { "#bfc9e6", "#0f1724", "#2d3555" });
        javafx.scene.layout.VBox card = new javafx.scene.layout.VBox(4);
        card.getStyleClass().add("care-section-card");
        card.setStyle("-fx-background-color: " + colors[1] + "; -fx-border-color: " + colors[2] + ";");
        String displayName = sectionKey.substring(0, 1).toUpperCase() + sectionKey.substring(1);
        javafx.scene.control.Label titleLabel = new javafx.scene.control.Label(displayName);
        titleLabel.getStyleClass().add("care-section-title");
        titleLabel.setStyle("-fx-text-fill: " + colors[0] + ";");
        javafx.scene.control.Label bodyLabel = new javafx.scene.control.Label(content);
        bodyLabel.setWrapText(true);
        bodyLabel.getStyleClass().add("care-section-body");
        card.getChildren().addAll(titleLabel, bodyLabel);
        return card;
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }
}
