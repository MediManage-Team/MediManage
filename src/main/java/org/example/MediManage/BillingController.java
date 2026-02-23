package org.example.MediManage;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.example.MediManage.model.BillItem;
import org.example.MediManage.model.Customer;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.service.BillingService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.prefs.Preferences;

public class BillingController {
    private static final String CUSTOMER_NAME_SELECTED_CLASS = "customer-name-selected";
    private static final String CUSTOMER_NAME_MUTED_CLASS = "customer-name-muted";
    private static final String WALK_IN_CUSTOMER_LABEL = "Walk-in Customer";
    private static final String CHECKOUT_READY_LABEL = "CHECKOUT (PRINT)";
    private static final String CHECKOUT_BUSY_LABEL = "Generating Care Protocol...";
    private static final String CARE_PROTOCOL_READY_LABEL = "✨ Generate Care Protocol";
    private static final String CARE_PROTOCOL_BUSY_LABEL = "⏳ Generating...";
    private static final String OVERRIDE_REQUEST_READY_LABEL = "Request Discount Override";
    private static final String OVERRIDE_REQUESTED_BUTTON_LABEL = "Override Requested";
    private static final String OVERRIDE_DEFAULT_STATUS = "No override requested for current bill.";
    private static final String PREF_EXPLANATION_LANGUAGE_CODE = "billing.subscription.explanation.language";
    private static final String DEFAULT_EXPLANATION_LANGUAGE_CODE = "en";

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
    @FXML
    private Button btnRequestOverride;
    @FXML
    private Label lblOverrideStatus;
    @FXML
    private Button btnExplainDiscountDecision;
    @FXML
    private ComboBox<String> cmbDiscountExplanationLanguage;

    private final BillingService billingService = new BillingService();
    private final Preferences prefs = Preferences.userNodeForPackage(BillingController.class);
    private final Map<String, String> explanationLanguageCodeByLabel = new LinkedHashMap<>();

    private ObservableList<BillItem> billList = FXCollections.observableArrayList();
    private ObservableList<Medicine> allMedicines = FXCollections.observableArrayList();
    private Customer selectedCustomer = null;
    private Medicine selectedMedicine = null;
    private boolean keyboardShortcutsRegistered = false;
    private BillingService.OverrideRequestSummary pendingOverrideRequest = null;

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
        setupExplanationLanguageSelector();
        applyOverrideFeatureGuards();
        updateOverrideUiState();
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

    private void setupExplanationLanguageSelector() {
        if (cmbDiscountExplanationLanguage == null) {
            return;
        }
        explanationLanguageCodeByLabel.clear();
        explanationLanguageCodeByLabel.putAll(billingService.supportedExplanationLanguages());
        cmbDiscountExplanationLanguage.getItems().setAll(explanationLanguageCodeByLabel.keySet());

        String savedCode = prefs.get(PREF_EXPLANATION_LANGUAGE_CODE, DEFAULT_EXPLANATION_LANGUAGE_CODE);
        String selectedLabel = labelForExplanationLanguageCode(savedCode);
        if (selectedLabel == null && !cmbDiscountExplanationLanguage.getItems().isEmpty()) {
            selectedLabel = cmbDiscountExplanationLanguage.getItems().get(0);
        }
        cmbDiscountExplanationLanguage.setValue(selectedLabel);
        cmbDiscountExplanationLanguage.setOnAction(event ->
                prefs.put(PREF_EXPLANATION_LANGUAGE_CODE, selectedExplanationLanguageCode()));
    }

    private String selectedExplanationLanguageCode() {
        if (cmbDiscountExplanationLanguage == null) {
            return DEFAULT_EXPLANATION_LANGUAGE_CODE;
        }
        String selectedLabel = cmbDiscountExplanationLanguage.getValue();
        if (selectedLabel == null || selectedLabel.isBlank()) {
            return DEFAULT_EXPLANATION_LANGUAGE_CODE;
        }
        return explanationLanguageCodeByLabel.getOrDefault(selectedLabel, DEFAULT_EXPLANATION_LANGUAGE_CODE);
    }

    private String labelForExplanationLanguageCode(String languageCode) {
        String normalized = languageCode == null ? DEFAULT_EXPLANATION_LANGUAGE_CODE : languageCode.trim().toLowerCase();
        for (Map.Entry<String, String> entry : explanationLanguageCodeByLabel.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(normalized)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void applyOverrideFeatureGuards() {
        boolean enabled = billingService.isManualOverrideRequestEnabled();
        boolean allowedForRole = billingService.canCurrentUserRequestManualOverride();
        boolean visible = enabled && allowedForRole;
        if (btnRequestOverride != null) {
            btnRequestOverride.setManaged(visible);
            btnRequestOverride.setVisible(visible);
        }
        if (lblOverrideStatus != null) {
            lblOverrideStatus.setManaged(visible || enabled || !allowedForRole);
            lblOverrideStatus.setVisible(visible || enabled || !allowedForRole);
            if (!enabled) {
                lblOverrideStatus.setText("Subscription override requests are disabled.");
            } else if (!allowedForRole) {
                lblOverrideStatus.setText("Your role cannot request subscription overrides.");
            }
        }
    }

    private void clearPendingOverrideRequest(String reason) {
        pendingOverrideRequest = null;
        if (lblOverrideStatus != null && reason != null && !reason.isBlank()) {
            lblOverrideStatus.setText(reason);
        }
        updateOverrideUiState();
    }

    private void updateOverrideUiState() {
        if (btnExplainDiscountDecision != null) {
            btnExplainDiscountDecision.setDisable(billList == null || billList.isEmpty());
        }
        if (btnRequestOverride == null || !btnRequestOverride.isVisible()) {
            return;
        }
        boolean canRequest = selectedCustomer != null && !billList.isEmpty() && pendingOverrideRequest == null;
        btnRequestOverride.setDisable(!canRequest);
        if (pendingOverrideRequest == null) {
            btnRequestOverride.setText(OVERRIDE_REQUEST_READY_LABEL);
            if (lblOverrideStatus != null
                    && (lblOverrideStatus.getText() == null
                            || lblOverrideStatus.getText().isBlank()
                            || OVERRIDE_REQUESTED_BUTTON_LABEL.equals(lblOverrideStatus.getText()))) {
                lblOverrideStatus.setText(OVERRIDE_DEFAULT_STATUS);
            }
            return;
        }

        btnRequestOverride.setText(OVERRIDE_REQUESTED_BUTTON_LABEL);
        if (lblOverrideStatus != null) {
            String enrollmentText = pendingOverrideRequest.enrollmentId() == null
                    ? "no active enrollment"
                    : "enrollment #" + pendingOverrideRequest.enrollmentId();
            lblOverrideStatus.setText(
                    "Pending override #"
                            + pendingOverrideRequest.overrideId()
                            + " requested at "
                            + String.format("%.2f", pendingOverrideRequest.requestedDiscountPercent())
                            + "% ("
                            + enrollmentText
                            + ").");
        }
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
            clearPendingOverrideRequest(OVERRIDE_DEFAULT_STATUS);
        } else {
            lblCustomerName.setText("Not Found (Walk-in)");
            if (lblCustomerPhone != null)
                lblCustomerPhone.setText("-");
            applyCustomerNameStyle(false);
            selectedCustomer = null;
            clearPendingOverrideRequest(OVERRIDE_DEFAULT_STATUS);
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
                        clearPendingOverrideRequest(OVERRIDE_DEFAULT_STATUS);
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

        if (pendingOverrideRequest != null) {
            clearPendingOverrideRequest("Pending override cleared because bill items changed. Request again if needed.");
        }

        updateTotal();
        updateOverrideUiState();

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
    private void handleRequestDiscountOverride() {
        if (!billingService.isManualOverrideRequestEnabled()) {
            showAlert(Alert.AlertType.WARNING, "Feature Disabled", "Override requests are disabled by feature flags.");
            return;
        }
        if (billList.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Empty Bill", "Add bill items before requesting an override.");
            return;
        }
        if (selectedCustomer == null) {
            showAlert(Alert.AlertType.WARNING, "Customer Required",
                    "Select a customer before requesting a discount override.");
            return;
        }
        if (pendingOverrideRequest != null) {
            showAlert(Alert.AlertType.INFORMATION, "Already Requested",
                    "An override request is already pending for this bill context.");
            return;
        }

        java.util.Optional<OverrideRequestInput> requestInput = showOverrideRequestDialog();
        if (requestInput.isEmpty()) {
            return;
        }

        try {
            pendingOverrideRequest = billingService.requestManualDiscountOverride(
                    selectedCustomer,
                    requestInput.get().requestedPercent(),
                    requestInput.get().reason());
            updateOverrideUiState();
            showAlert(Alert.AlertType.INFORMATION, "Override Requested",
                    "Override request #" + pendingOverrideRequest.overrideId()
                            + " submitted and pending Manager/Admin approval.");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Override Request Failed", e.getMessage());
        }
    }

    @FXML
    private void handleExplainDiscountDecision() {
        if (billList == null || billList.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Empty Bill", "Add bill items before requesting an explanation.");
            return;
        }

        BillingService.DiscountDecisionExplanation explanation = billingService.explainSubscriptionDiscountDecision(
                billingService.snapshotItems(billList),
                selectedCustomer);

        StringBuilder body = new StringBuilder(explanation.summary());
        List<String> points = explanation.talkingPoints();
        if (points != null) {
            for (String point : points) {
                if (point == null || point.isBlank()) {
                    continue;
                }
                body.append("\n- ").append(point.trim());
            }
        }
        if (pendingOverrideRequest != null) {
            body.append("\n- Pending manual override #")
                    .append(pendingOverrideRequest.overrideId())
                    .append(" may still change the final discount after approval.");
        }

        showAlert(
                Alert.AlertType.INFORMATION,
                explanation.discountApplied() ? "Discount Applied Explanation" : "Discount Rejection Explanation",
                body.toString());
    }

    @FXML
    private void handleCheckout() {
        if (billList.isEmpty())
            return;

        if (pendingOverrideRequest != null) {
            Alert pending = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "Override request #" + pendingOverrideRequest.overrideId()
                            + " is still pending approval.\nCheckout will proceed without that override unless already approved.\n\nContinue?",
                    ButtonType.OK,
                    ButtonType.CANCEL);
            pending.setTitle("Pending Override");
            pending.setHeaderText("Override not yet approved");
            java.util.Optional<ButtonType> pendingDecision = pending.showAndWait();
            if (pendingDecision.isEmpty() || pendingDecision.get() != ButtonType.OK) {
                return;
            }
        }

        Customer checkoutCustomer = selectedCustomer;
        Integer cid = checkoutCustomer != null ? checkoutCustomer.getCustomerId() : null;

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
        String explanationLanguageCode = selectedExplanationLanguageCode();
        CompletableFuture<String> careProtocolFuture = billingService.generateCheckoutCareProtocol(checkoutItems);
        CompletableFuture<BillingService.LocalizedDiscountExplanation> explanationFuture = billingService
                .generateLocalizedSubscriptionExplanation(checkoutItems, checkoutCustomer, explanationLanguageCode);

        careProtocolFuture
                .thenCombine(explanationFuture, CheckoutAiContext::new)
                .thenAccept(aiContext -> {
                    // Return to FX Thread for UI & PDF generation
                    Platform.runLater(() -> {
                        try {
                            int userId = org.example.MediManage.util.UserSession.getInstance().getUser().getId();
                            BillingService.CheckoutResult checkoutResult = billingService.completeCheckout(
                                    checkoutItems,
                                    checkoutCustomer,
                                    userId,
                                    paymentMode,
                                    aiContext.careProtocol(),
                                    aiContext.localizedExplanation());

                            String localizedSnippet = aiContext.localizedExplanation() == null
                                    ? ""
                                    : aiContext.localizedExplanation().snippet();
                            String localizedLanguage = aiContext.localizedExplanation() == null
                                    ? "English"
                                    : aiContext.localizedExplanation().languageName();
                            showAlert(Alert.AlertType.INFORMATION, "Success",
                                    "Bill #" + checkoutResult.billId()
                                            + " & Care Protocol Generated!\nSaved to: " + checkoutResult.pdfPath()
                                            + (checkoutResult.subscriptionSavings() > 0.0
                                                    ? "\nSubscription: " + checkoutResult.subscriptionPlanName()
                                                            + " | Savings: ₹"
                                                            + String.format("%.2f", checkoutResult.subscriptionSavings())
                                                    : "")
                                            + (localizedSnippet == null || localizedSnippet.isBlank()
                                                    ? ""
                                                    : "\nSavings Explanation (" + localizedLanguage + "): "
                                                            + localizedSnippet));

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
                            clearPendingOverrideRequest(OVERRIDE_DEFAULT_STATUS);

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
                                "Failed to generate AI checkout context: " + ex.getMessage());
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

    private java.util.Optional<OverrideRequestInput> showOverrideRequestDialog() {
        Dialog<OverrideRequestInput> dialog = new Dialog<>();
        dialog.setTitle("Request Discount Override");
        dialog.setHeaderText("Manual override request (Manager/Admin approval required)");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Label eligibilityLabel = new Label();
        BillingService.OverrideRequestSummary existing = pendingOverrideRequest;
        if (existing != null) {
            eligibilityLabel.setText("Pending override already exists.");
        } else {
            org.example.MediManage.service.subscription.SubscriptionEligibilityResult eligibility = billingService
                    .evaluateSubscriptionEligibility(selectedCustomer);
            String enrollmentText = eligibility.eligible()
                    ? "Active enrollment #" + eligibility.enrollmentId()
                    : "No active enrollment";
            eligibilityLabel.setText("Eligibility: " + eligibility.code().name() + " | " + enrollmentText);
        }
        eligibilityLabel.getStyleClass().add("text-muted");

        TextField requestedPercentField = new TextField("10");
        requestedPercentField.setPromptText("Requested discount %");
        TextArea reasonArea = new TextArea();
        reasonArea.setPromptText("Mandatory reason for override request");
        reasonArea.setPrefRowCount(3);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.add(new Label("Requested %"), 0, 0);
        grid.add(requestedPercentField, 1, 0);
        grid.add(new Label("Reason"), 0, 1);
        grid.add(reasonArea, 1, 1);

        VBox content = new VBox(8, eligibilityLabel, grid);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(buttonType -> {
            if (buttonType != ButtonType.OK) {
                return null;
            }
            String rawPercent = requestedPercentField.getText() == null ? "" : requestedPercentField.getText().trim();
            String rawReason = reasonArea.getText() == null ? "" : reasonArea.getText().trim();
            if (rawPercent.isEmpty()) {
                throw new IllegalArgumentException("Requested discount percent is required.");
            }
            if (rawReason.isEmpty()) {
                throw new IllegalArgumentException("Reason is mandatory.");
            }
            double requestedPercent;
            try {
                requestedPercent = Double.parseDouble(rawPercent);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Requested discount percent must be numeric.");
            }
            if (requestedPercent <= 0.0 || requestedPercent > 100.0) {
                throw new IllegalArgumentException("Requested discount percent must be between 0 and 100.");
            }
            return new OverrideRequestInput(requestedPercent, rawReason);
        });

        try {
            return dialog.showAndWait();
        } catch (Exception e) {
            showAlert(Alert.AlertType.WARNING, "Validation Error", e.getMessage());
            return java.util.Optional.empty();
        }
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

    private record OverrideRequestInput(double requestedPercent, String reason) {
    }

    private record CheckoutAiContext(
            String careProtocol,
            BillingService.LocalizedDiscountExplanation localizedExplanation) {
    }
}
