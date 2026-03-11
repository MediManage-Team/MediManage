package org.example.MediManage.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.geometry.Insets;
import org.example.MediManage.model.BillItem;
import org.example.MediManage.model.Customer;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.model.HeldOrder;
import org.example.MediManage.model.PaymentSplit;
import org.example.MediManage.dao.HeldOrderDAO;
import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.service.BillingService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.example.MediManage.service.EmailService;
import org.example.MediManage.service.WhatsAppService;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class BillingController {
    private static final String CUSTOMER_NAME_SELECTED_CLASS = "customer-name-selected";
    private static final String CUSTOMER_NAME_MUTED_CLASS = "customer-name-muted";
    private static final String WALK_IN_CUSTOMER_LABEL = "Walk-in Customer";
    private static final String CHECKOUT_READY_LABEL = "CHECKOUT (PRINT)";
    private static final String CHECKOUT_BUSY_LABEL = "Generating Care Protocol...";
    private static final String CARE_PROTOCOL_READY_LABEL = "\u2728 Generate Care Protocol";
    private static final String CARE_PROTOCOL_BUSY_LABEL = "\u23f3 Generating...";

    @FXML
    private TableView<BillItem> billingTable;
    @FXML
    private TableColumn<BillItem, String> colName;
    @FXML
    private TableColumn<BillItem, Integer> colQty;
    @FXML
    private TableColumn<BillItem, Double> colPrice;
    @FXML
    private TableColumn<BillItem, Double> colTotal;
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
    private ListView<Medicine> listMedicineSuggestions;
    @FXML
    private TextField txtQty;
    @FXML
    private TextField txtBarcode;
    @FXML
    private Button btnHoldOrder;
    @FXML
    private Button btnRecallOrder;
    @FXML
    private Label lblHeldCount;
    @FXML
    private Button btnCheckout;
    @FXML
    private VBox careProtocolContainer;
    @FXML
    private Button btnGenerateCareProtocol;
    @FXML
    private Label lblLoyaltyPoints;
    @FXML
    private Button btnRedeemPoints;

    private final BillingService billingService = new BillingService();
    private final HeldOrderDAO heldOrderDAO = new HeldOrderDAO();
    private final MedicineDAO medicineDAO = new MedicineDAO();
    private final org.example.MediManage.service.LoyaltyService loyaltyService = new org.example.MediManage.service.LoyaltyService();
    private final Gson gson = new Gson();

    private ObservableList<BillItem> billList = FXCollections.observableArrayList();
    private ObservableList<Medicine> allMedicines = FXCollections.observableArrayList();
    private Customer selectedCustomer = null;
    private Medicine selectedMedicine = null;
    private boolean keyboardShortcutsRegistered = false;

    @FXML
    public void initialize() {
        allMedicines.addAll(billingService.loadActiveMedicines());

        colName.setCellValueFactory(data -> data.getValue().nameProperty());
        colQty.setCellValueFactory(data -> data.getValue().qtyProperty().asObject());
        colPrice.setCellValueFactory(data -> data.getValue().priceProperty().asObject());
        colTotal.setCellValueFactory(data -> data.getValue().totalProperty().asObject());
        billingTable.setItems(billList);

        setupMedicineSearch();
        setupBarcodeScanner();
        setupKeyBindings();
        setupGlobalShortcuts();
        applyCustomerNameStyle(false);
        refreshHeldCount();
    }

    // ═══════════════════════════════════════════════
    // MEDICINE SEARCH
    // ═══════════════════════════════════════════════

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
                    m.getGenericName().toLowerCase().contains(lower));
            if (filteredDocs.isEmpty()) {
                listMedicineSuggestions.setVisible(false);
            } else {
                listMedicineSuggestions.setVisible(true);
                listMedicineSuggestions.setItems(filteredDocs);
            }
        });
        listMedicineSuggestions.setOnMouseClicked(e -> {
            Medicine sel = listMedicineSuggestions.getSelectionModel().getSelectedItem();
            if (sel != null)
                selectMedicine(sel);
        });
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

    // ═══════════════════════════════════════════════
    // KEYBOARD SHORTCUTS
    // ═══════════════════════════════════════════════

    private void setupKeyBindings() {
        txtQty.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER)
                handleAdd();
        });
        txtSearchCustomer.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER)
                handleCustomerSearch();
        });
    }

    private void setupGlobalShortcuts() {
        Platform.runLater(() -> {
            if (keyboardShortcutsRegistered || txtSearchMedicine == null || txtSearchMedicine.getScene() == null)
                return;
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
        if (event.isControlDown() && event.getCode() == KeyCode.ENTER && !btnCheckout.isDisable()
                && !billList.isEmpty()) {
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
        if (lblCustomerName == null)
            return;
        lblCustomerName.getStyleClass().removeAll(CUSTOMER_NAME_SELECTED_CLASS, CUSTOMER_NAME_MUTED_CLASS);
        lblCustomerName.getStyleClass().add(selected ? CUSTOMER_NAME_SELECTED_CLASS : CUSTOMER_NAME_MUTED_CLASS);
    }

    private void updateLoyaltyDisplay() {
        if (lblLoyaltyPoints == null)
            return;
        if (selectedCustomer != null) {
            int points = loyaltyService.getPoints(selectedCustomer.getCustomerId());
            lblLoyaltyPoints.setText(String.valueOf(points));
            boolean canRedeem = points >= loyaltyService.getRedemptionThreshold();
            if (btnRedeemPoints != null) {
                btnRedeemPoints.setVisible(canRedeem);
                btnRedeemPoints.setManaged(canRedeem);
            }
        } else {
            lblLoyaltyPoints.setText("-");
            if (btnRedeemPoints != null) {
                btnRedeemPoints.setVisible(false);
                btnRedeemPoints.setManaged(false);
            }
        }
    }

    @FXML
    private void handleRedeemPoints() {
        if (selectedCustomer == null)
            return;
        double discountPercent = loyaltyService.redeemPoints(selectedCustomer.getCustomerId());
        if (discountPercent > 0) {
            // Apply discount to all items in the bill
            for (BillItem item : billList) {
                double discounted = item.getTotal() * (1.0 - discountPercent / 100.0);
                item.setTotal(Math.round(discounted * 100.0) / 100.0);
            }
            updateTotal();
            updateLoyaltyDisplay();
            showAlert(Alert.AlertType.INFORMATION, "Loyalty Redeemed",
                    String.format("%.0f%% discount applied! 100 points redeemed.", discountPercent));
        }
    }

    // ═══════════════════════════════════════════════
    // CUSTOMER MANAGEMENT
    // ═══════════════════════════════════════════════

    @FXML
    private void handleCustomerSearch() {
        String q = txtSearchCustomer.getText();
        if (q.isEmpty())
            return;
        List<Customer> res = billingService.searchCustomers(q);
        if (!res.isEmpty()) {
            selectedCustomer = res.get(0);
            String labelText = selectedCustomer.getName();
            if (selectedCustomer.getCurrentBalance() > 0) {
                labelText += String.format(" (Bal: \u20b9%.2f)", selectedCustomer.getCurrentBalance());
            }
            lblCustomerName.setText(labelText);
            if (lblCustomerPhone != null)
                lblCustomerPhone.setText(selectedCustomer.getPhone());
            applyCustomerNameStyle(true);
            updateLoyaltyDisplay();
        } else {
            lblCustomerName.setText("Not Found (Walk-in)");
            if (lblCustomerPhone != null)
                lblCustomerPhone.setText("-");
            applyCustomerNameStyle(false);
            selectedCustomer = null;
            updateLoyaltyDisplay();
        }
    }

    @FXML
    private void handleAddCustomer() {
        TextInputDialog phoneDialog = new TextInputDialog(txtSearchCustomer.getText());
        phoneDialog.setTitle("New Customer");
        phoneDialog.setHeaderText("Enter Customer Phone (+91...)");
        
        // Add text formatter to restrict to digits and '+', max 15 chars
        TextField phoneField = phoneDialog.getEditor();
        phoneField.setTextFormatter(new TextFormatter<>(change -> {
            if (change.getControlNewText().length() > 15) return null;
            if (!change.getControlNewText().matches("[0-9+]*")) return null;
            return change;
        }));
        
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
                        updateLoyaltyDisplay();
                    }
                } catch (Exception e) {
                    showAlert(Alert.AlertType.ERROR, "Error", "Could not add customer: " + e.getMessage());
                }
            }
        }
    }

    // ═══════════════════════════════════════════════
    // BILL ITEM MANAGEMENT
    // ═══════════════════════════════════════════════

    @FXML
    private void handleAdd() {
        if (selectedMedicine == null)
            return;
        int qty;
        try {
            qty = Integer.parseInt(txtQty.getText());
        } catch (NumberFormatException e) {
            return;
        }
        if (qty <= 0)
            return;

        BillingService.AddItemResult result = billingService.addMedicineToBill(billList, selectedMedicine, qty);
        if (result.status() == BillingService.AddItemStatus.OUT_OF_STOCK) {
            showAlert(Alert.AlertType.WARNING, "Stock Low", "Only " + result.availableStock() + " available.");
            return;
        }
        if (result.status() != BillingService.AddItemStatus.ADDED)
            return;
        if (result.requiresTableRefresh())
            billingTable.refresh();

        updateTotal();
        selectedMedicine = null;
        txtSearchMedicine.clear();
        txtQty.clear();
        txtSearchMedicine.requestFocus();
    }

    private void updateTotal() {
        double sum = billingService.calculateTotal(billList);
        lblTotal.setText(String.format("\u20b9 %.2f", sum));
    }

    // ═══════════════════════════════════════════════
    // CHECKOUT
    // ═══════════════════════════════════════════════

    @FXML
    private void handleCheckout() {
        if (billList.isEmpty())
            return;

        Customer checkoutCustomer = selectedCustomer;
        Integer cid = checkoutCustomer != null ? checkoutCustomer.getCustomerId() : null;

        double totalAmount = billingService.calculateTotal(billList);
        List<PaymentSplit> splits = showSplitPaymentDialog(totalAmount);
        if (splits == null || splits.isEmpty())
            return;

        String paymentMode = compositePaymentMode(splits);
        boolean hasCredit = splits.stream().anyMatch(s -> "Credit".equalsIgnoreCase(s.getPaymentMethod()));
        if (hasCredit && cid == null) {
            showAlert(Alert.AlertType.WARNING, "Credit Error", "Credit payment requires a selected customer!");
            return;
        }

        btnCheckout.setDisable(true);
        btnCheckout.setText(CHECKOUT_BUSY_LABEL);

        List<BillItem> checkoutItems = billingService.snapshotItems(billList);
        CompletableFuture<String> careProtocolFuture = billingService.generateCheckoutCareProtocol(checkoutItems);

        careProtocolFuture.thenAccept(careProtocol -> {
            Platform.runLater(() -> {
                try {
                    int userId = org.example.MediManage.util.UserSession.getInstance().getUser().getId();
                    BillingService.CheckoutResult checkoutResult = billingService.completeCheckout(
                            checkoutItems, checkoutCustomer, userId, paymentMode, careProtocol);

                    showPostCheckoutDialog(checkoutResult, checkoutCustomer, totalAmount, careProtocol);

                    billList.clear();
                    updateTotal();
                    selectedCustomer = null;
                    lblCustomerName.setText(WALK_IN_CUSTOMER_LABEL);
                    applyCustomerNameStyle(false);
                    if (lblCustomerPhone != null)
                        lblCustomerPhone.setText("-");
                    updateLoyaltyDisplay();

                    // Award loyalty points
                    if (cid != null) {
                        int awarded = loyaltyService.awardPoints(cid, totalAmount);
                        if (awarded > 0) {
                            System.out.println("🎁 Awarded " + awarded + " loyalty points to customer #" + cid);
                        }
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
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                showAlert(Alert.AlertType.ERROR, "AI Error",
                        "Failed to generate AI checkout context: " + ex.getMessage());
                btnCheckout.setDisable(false);
                btnCheckout.setText(CHECKOUT_READY_LABEL);
            });
            return null;
        });
    }

    private void showPostCheckoutDialog(BillingService.CheckoutResult result, Customer customer, double totalAmount, String careProtocol) {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Checkout Successful");
        dialog.setHeaderText("Bill #" + result.billId() + " Generated successfully!\nSaved to: " + result.pdfPath());
        
        ButtonType btnBoth = new ButtonType("📨 Send Both");
        ButtonType btnEmail = new ButtonType("📧 Email Invoice");
        ButtonType btnWhatsApp = new ButtonType("💬 WhatsApp Invoice");
        ButtonType btnClose = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.getButtonTypes().setAll(btnBoth, btnEmail, btnWhatsApp, btnClose);

        java.util.Optional<ButtonType> choice = dialog.showAndWait();
        if (choice.isPresent()) {
            if (choice.get() == btnBoth) {
                // To keep it simple, we just ask for email then ask for phone.
                handleSendEmail(result, customer, totalAmount, careProtocol);
                handleSendWhatsApp(result, customer, totalAmount, careProtocol);
            } else if (choice.get() == btnEmail) {
                handleSendEmail(result, customer, totalAmount, careProtocol);
            } else if (choice.get() == btnWhatsApp) {
                handleSendWhatsApp(result, customer, totalAmount, careProtocol);
            }
        }
    }

    private void handleSendEmail(BillingService.CheckoutResult result, Customer customer, double totalAmount, String careProtocol) {
        TextInputDialog emailDialog = new TextInputDialog(customer != null ? customer.getEmail() : "");
        emailDialog.setTitle("Send Email");
        emailDialog.setHeaderText("Enter Customer Email Address");
        java.util.Optional<String> emailResult = emailDialog.showAndWait();

        if (emailResult.isPresent() && !emailResult.get().trim().isEmpty()) {
            String toEmail = emailResult.get().trim();
            String name = customer != null ? customer.getName() : "Customer";
            
            org.example.MediManage.util.ToastNotification.info("Sending Email to " + toEmail + "...");
            
            EmailService.sendInvoiceEmail(toEmail, name, careProtocol, result.pdfPath(), result.billId(), totalAmount)
                .thenAccept(success -> Platform.runLater(() -> 
                    org.example.MediManage.util.ToastNotification.success("Email sent successfully!")))
                .exceptionally(ex -> {
                    Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Email Failed", ex.getMessage()));
                    return null;
                });
        }
    }

    private void handleSendWhatsApp(BillingService.CheckoutResult result, Customer customer, double totalAmount, String careProtocol) {
        TextInputDialog phoneDialog = new TextInputDialog(customer != null ? customer.getPhone() : "");
        phoneDialog.setTitle("Send WhatsApp");
        phoneDialog.setHeaderText("Enter Customer WhatsApp Number (+CCxxxxxxxxxx)");
        
        // Add text formatter to restrict to digits and '+', max 15 chars
        TextField phoneField = phoneDialog.getEditor();
        phoneField.setTextFormatter(new TextFormatter<>(change -> {
            if (change.getControlNewText().length() > 15) return null;
            if (change.getText().matches("[^0-9+]")) return null;
            return change;
        }));
        
        java.util.Optional<String> phoneResult = phoneDialog.showAndWait();

        if (phoneResult.isPresent() && !phoneResult.get().trim().isEmpty()) {
            String toPhone = phoneResult.get().trim();
            String name = customer != null ? customer.getName() : "Customer";
            
            org.example.MediManage.util.ToastNotification.info("Sending WhatsApp to " + toPhone + "...");
            
            WhatsAppService.sendInvoiceWhatsApp(toPhone, name, totalAmount, careProtocol, result.billId(), result.pdfPath())
                .thenAccept(success -> Platform.runLater(() -> 
                    org.example.MediManage.util.ToastNotification.success("WhatsApp message sent successfully!")))
                .exceptionally(ex -> {
                    Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "WhatsApp Failed", ex.getMessage()));
                    return null;
                });
        }
    }

    // ═══════════════════════════════════════════════
    // CARE PROTOCOL
    // ═══════════════════════════════════════════════

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
                "\ud83d\udd04 Generating AI Care Protocol...\nThis may take a few seconds.");
        loadingLabel.getStyleClass().add("care-loading-label");
        careProtocolContainer.getChildren().add(loadingLabel);

        String providerInfo = billingService.getCloudProviderInfo();
        List<BillItem> careItems = billingService.snapshotItems(billList);

        billingService.generateDetailedCareProtocol(careItems)
                .thenAccept(protocol -> Platform.runLater(() -> {
                    buildCareProtocolCards(protocol, providerInfo);
                    btnGenerateCareProtocol.setDisable(false);
                    btnGenerateCareProtocol.setText(CARE_PROTOCOL_READY_LABEL);
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        careProtocolContainer.getChildren().clear();
                        javafx.scene.control.Label errLabel = new javafx.scene.control.Label(
                                "\u274c Error: " + ex.getMessage()
                                        + "\n\n\ud83d\udca1 Tip: Configure your Cloud AI API key in Settings.");
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

        VBox headerCard = new VBox(3);
        headerCard.getStyleClass().add("care-header-card");
        javafx.scene.control.Label headerTitle = new javafx.scene.control.Label("\ud83c\udfe5 Patient Care Protocol");
        headerTitle.getStyleClass().add("care-header-title");
        javafx.scene.control.Label headerProv = new javafx.scene.control.Label("\u2601\ufe0f " + providerInfo);
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
                javafx.scene.control.Label medLabel = new javafx.scene.control.Label("\ud83d\udc8a " + trimmed);
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
        javafx.scene.control.Label footer = new javafx.scene.control.Label("\u2695\ufe0f Generated by MediManage AI");
        footer.getStyleClass().add("care-footer");
        careProtocolContainer.getChildren().add(footer);
    }

    private VBox createSectionCard(String sectionKey, String content, java.util.Map<String, String[]> colorMap) {
        String[] colors = colorMap.getOrDefault(sectionKey, new String[] { "#bfc9e6", "#0f1724", "#2d3555" });
        VBox card = new VBox(4);
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

    // ═══════════════════════════════════════════════
    // BARCODE SCANNING
    // ═══════════════════════════════════════════════

    private void setupBarcodeScanner() {
        if (txtBarcode == null)
            return;
        txtBarcode.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER)
                handleBarcodeScan();
        });
    }

    private void handleBarcodeScan() {
        if (txtBarcode == null)
            return;
        String code = txtBarcode.getText();
        if (code == null || code.isBlank())
            return;
        Medicine found = medicineDAO.findByBarcode(code.trim());
        if (found != null) {
            selectMedicine(found);
            txtBarcode.clear();
            if (txtQty.getText() == null || txtQty.getText().isBlank())
                txtQty.setText("1");
            txtQty.requestFocus();
        } else {
            showAlert(Alert.AlertType.WARNING, "Barcode Not Found",
                    "No medicine found with barcode: " + code
                            + "\n\nAssign barcodes via Inventory \u2192 right-click \u2192 Set Barcode.");
            txtBarcode.selectAll();
        }
    }

    // ═══════════════════════════════════════════════
    // HOLD / RECALL ORDERS
    // ═══════════════════════════════════════════════

    @FXML
    private void handleHoldOrder() {
        if (billList.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Empty Bill", "Add items before holding the order.");
            return;
        }
        TextInputDialog notesDialog = new TextInputDialog();
        notesDialog.setTitle("Hold Order");
        notesDialog.setHeaderText("Hold this order for later?");
        notesDialog.setContentText("Notes (optional):");
        java.util.Optional<String> notesResult = notesDialog.showAndWait();
        if (notesResult.isEmpty())
            return;

        try {
            List<Map<String, Object>> itemMaps = new ArrayList<>();
            for (BillItem item : billList) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("medicineId", item.getMedicineId());
                m.put("name", item.getName());
                m.put("qty", item.getQty());
                m.put("price", item.getPrice());
                m.put("total", item.getTotal());
                itemMaps.add(m);
            }
            String json = gson.toJson(itemMaps);
            double total = billingService.calculateTotal(billList);
            Integer cid = selectedCustomer != null ? selectedCustomer.getCustomerId() : null;
            int userId = org.example.MediManage.util.UserSession.getInstance().getUser().getId();
            int holdId = heldOrderDAO.holdOrder(cid, userId, json, total, notesResult.get());
            showAlert(Alert.AlertType.INFORMATION, "Order Held",
                    "Order #" + holdId + " held successfully (" + billList.size() + " items, \u20b9"
                            + String.format("%.2f", total) + ").");
            billList.clear();
            updateTotal();
            refreshHeldCount();
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Hold Failed", e.getMessage());
        }
    }

    @FXML
    private void handleRecallOrder() {
        try {
            List<HeldOrder> held = heldOrderDAO.getActiveHeldOrders();
            if (held.isEmpty()) {
                showAlert(Alert.AlertType.INFORMATION, "No Held Orders", "There are no held orders to recall.");
                return;
            }
            ChoiceDialog<HeldOrder> dialog = new ChoiceDialog<>(held.get(0), held);
            dialog.setTitle("Recall Order");
            dialog.setHeaderText("Select a held order to recall");
            dialog.setContentText("Order:");
            java.util.Optional<HeldOrder> result = dialog.showAndWait();
            if (result.isEmpty())
                return;

            HeldOrder selected = result.get();
            if (!billList.isEmpty()) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Current bill has " + billList.size() + " items. Recall will replace them. Continue?",
                        ButtonType.OK, ButtonType.CANCEL);
                confirm.setTitle("Replace Current Bill?");
                java.util.Optional<ButtonType> confirmResult = confirm.showAndWait();
                if (confirmResult.isEmpty() || confirmResult.get() != ButtonType.OK)
                    return;
            }

            List<Map<String, Object>> itemMaps = gson.fromJson(selected.getItemsJson(),
                    new TypeToken<List<Map<String, Object>>>() {
                    }.getType());
            billList.clear();
            for (Map<String, Object> m : itemMaps) {
                int medId = ((Number) m.get("medicineId")).intValue();
                String name = (String) m.get("name");
                int qty = ((Number) m.get("qty")).intValue();
                double price = ((Number) m.get("price")).doubleValue();
                billList.add(new BillItem(medId, name, null, qty, price, 0.0));
            }
            updateTotal();
            heldOrderDAO.recallOrder(selected.getHoldId());
            refreshHeldCount();
            showAlert(Alert.AlertType.INFORMATION, "Order Recalled",
                    "Order #" + selected.getHoldId() + " recalled (" + billList.size() + " items).");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Recall Failed", e.getMessage());
        }
    }

    private void refreshHeldCount() {
        if (lblHeldCount == null)
            return;
        try {
            int count = heldOrderDAO.countActiveHeldOrders();
            lblHeldCount.setText(count > 0 ? count + " held" : "");
        } catch (SQLException ignored) {
            lblHeldCount.setText("");
        }
    }

    // ═══════════════════════════════════════════════
    // SPLIT PAYMENT DIALOG
    // ═══════════════════════════════════════════════

    List<PaymentSplit> showSplitPaymentDialog(double totalAmount) {
        Dialog<List<PaymentSplit>> dialog = new Dialog<>();
        dialog.setTitle("Split Payment");
        dialog.setHeaderText(String.format("Total: \u20b9%.2f \u2014 Split across payment methods", totalAmount));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(500);

        VBox container = new VBox(10);
        container.setPadding(new Insets(10));

        ObservableList<PaymentSplit> splits = FXCollections.observableArrayList();
        splits.add(new PaymentSplit("Cash", totalAmount));

        ListView<PaymentSplit> listView = new ListView<>(splits);
        listView.setPrefHeight(150);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(PaymentSplit item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
            }
        });

        ComboBox<String> methodBox = new ComboBox<>(FXCollections.observableArrayList(
                "Cash", "UPI", "Card", "Credit", "Cheque", "Other"));
        methodBox.setValue("Cash");
        TextField amountField = new TextField();
        amountField.setPromptText("Amount");
        TextField refField = new TextField();
        refField.setPromptText("Ref# (optional)");

        Button addBtn = new Button("Add Split");
        addBtn.setOnAction(e -> {
            try {
                double amt = Double.parseDouble(amountField.getText());
                if (amt <= 0)
                    return;
                splits.add(new PaymentSplit(methodBox.getValue(), amt, refField.getText()));
                amountField.clear();
                refField.clear();
            } catch (NumberFormatException ignored) {
            }
        });

        Button removeBtn = new Button("Remove Selected");
        removeBtn.setOnAction(e -> {
            PaymentSplit sel = listView.getSelectionModel().getSelectedItem();
            if (sel != null)
                splits.remove(sel);
        });

        Label remainingLabel = new Label();
        Runnable updateRemaining = () -> {
            double paid = splits.stream().mapToDouble(PaymentSplit::getAmount).sum();
            double remaining = totalAmount - paid;
            remainingLabel.setText(String.format("Remaining: \u20b9%.2f", remaining));
            remainingLabel.setStyle(remaining > 0.01 ? "-fx-text-fill: #ff6b6b;" : "-fx-text-fill: #5fe6b3;");
        };
        splits.addListener((javafx.collections.ListChangeListener<PaymentSplit>) c -> updateRemaining.run());
        updateRemaining.run();

        HBox addRow = new HBox(8, methodBox, amountField, refField, addBtn, removeBtn);
        addRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        container.getChildren().addAll(listView, addRow, remainingLabel);
        dialog.getDialogPane().setContent(container);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                double paid = splits.stream().mapToDouble(PaymentSplit::getAmount).sum();
                if (Math.abs(paid - totalAmount) > 0.01) {
                    showAlert(Alert.AlertType.WARNING, "Amount Mismatch",
                            String.format("Total splits (\u20b9%.2f) must equal bill total (\u20b9%.2f)", paid,
                                    totalAmount));
                    return null;
                }
                return new ArrayList<>(splits);
            }
            return null;
        });

        java.util.Optional<List<PaymentSplit>> result = dialog.showAndWait();
        return result.orElse(null);
    }

    static String compositePaymentMode(List<PaymentSplit> splits) {
        if (splits == null || splits.isEmpty())
            return "Cash";
        if (splits.size() == 1)
            return splits.get(0).getPaymentMethod().toUpperCase();
        return splits.stream()
                .map(PaymentSplit::getPaymentMethod)
                .distinct()
                .reduce((a, b) -> a + "+" + b)
                .orElse("Cash");
    }

    // ═══════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }
}
