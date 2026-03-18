package org.example.MediManage.controller;

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
import org.example.MediManage.model.HeldOrder;
import org.example.MediManage.model.PaymentSplit;
import org.example.MediManage.dao.HeldOrderDAO;
import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.service.BillingService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

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
    private Button btnGenerateCareProtocol;
    @FXML
    private Label lblLoyaltyPoints;
    @FXML
    private Button btnRedeemPoints;

    private final BillingService billingService = new BillingService();
    private final HeldOrderDAO heldOrderDAO = new HeldOrderDAO();
    private final MedicineDAO medicineDAO = new MedicineDAO();
    private final org.example.MediManage.service.LoyaltyService loyaltyService = new org.example.MediManage.service.LoyaltyService();
    private final BillingCheckoutSupport checkoutSupport = new BillingCheckoutSupport(this::showAlert);
    private final Gson gson = new Gson();

    private ObservableList<BillItem> billList = FXCollections.observableArrayList();
    private ObservableList<Medicine> allMedicines = FXCollections.observableArrayList();
    private Customer selectedCustomer = null;
    private Medicine selectedMedicine = null;
    private boolean keyboardShortcutsRegistered = false;
    private double pendingLoyaltyDiscountPercent = 0.0;

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
            boolean canRedeem = points >= loyaltyService.getRedemptionThreshold() && pendingLoyaltyDiscountPercent <= 0.0;
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
        if (billList.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Empty Bill", "Add items before applying loyalty redemption.");
            return;
        }
        if (!loyaltyService.canRedeem(selectedCustomer.getCustomerId()))
            return;

        pendingLoyaltyDiscountPercent = loyaltyService.getRedemptionDiscountPercent();
        updateTotal();
        updateLoyaltyDisplay();
        showAlert(Alert.AlertType.INFORMATION, "Loyalty Discount Ready",
                String.format("%.0f%% discount will be applied when checkout completes.", pendingLoyaltyDiscountPercent));
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
            Customer newCustomer = res.get(0);
            if (!isSameCustomer(selectedCustomer, newCustomer)) {
                clearPendingLoyaltyDiscount();
            }
            selectedCustomer = newCustomer;
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
            clearPendingLoyaltyDiscount();
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
                    Customer previousCustomer = selectedCustomer;
                    selectedCustomer = billingService.addCustomerAndFind(name, phone);
                    if (selectedCustomer != null) {
                        if (!isSameCustomer(previousCustomer, selectedCustomer)) {
                            clearPendingLoyaltyDiscount();
                        }
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
        double sum = billingService.calculateTotal(billList, pendingLoyaltyDiscountPercent);
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

        double totalAmount = billingService.calculateTotal(billList, pendingLoyaltyDiscountPercent);
        List<PaymentSplit> splits = checkoutSupport.showSplitPaymentDialog(totalAmount);
        if (splits == null || splits.isEmpty())
            return;

        String paymentMode = BillingCheckoutSupport.compositePaymentMode(splits);
        boolean hasCredit = splits.stream().anyMatch(s -> "Credit".equalsIgnoreCase(s.getPaymentMethod()));
        if (hasCredit && cid == null) {
            showAlert(Alert.AlertType.WARNING, "Credit Error", "Credit payment requires a selected customer!");
            return;
        }

        btnCheckout.setDisable(true);
        btnCheckout.setText(CHECKOUT_BUSY_LABEL);

        List<BillItem> checkoutItems = billingService.snapshotItems(billList, pendingLoyaltyDiscountPercent);
        boolean redeemLoyalty = pendingLoyaltyDiscountPercent > 0.0;
        CompletableFuture<String> careProtocolFuture = billingService.generateCheckoutCareProtocol(checkoutItems);

        careProtocolFuture.thenAccept(careProtocol -> {
            Platform.runLater(() -> {
                try {
                    int userId = org.example.MediManage.util.UserSession.getInstance().getUser().getId();
                    BillingService.CheckoutResult checkoutResult = billingService.completeCheckout(
                            checkoutItems, checkoutCustomer, userId, splits, paymentMode, careProtocol,
                            redeemLoyalty);

                    checkoutSupport.showPostCheckoutDialog(checkoutResult, checkoutCustomer, totalAmount, careProtocol);

                    billList.clear();
                    clearPendingLoyaltyDiscount();
                    updateTotal();
                    selectedCustomer = null;
                    lblCustomerName.setText(WALK_IN_CUSTOMER_LABEL);
                    applyCustomerNameStyle(false);
                    if (lblCustomerPhone != null)
                        lblCustomerPhone.setText("-");
                    updateLoyaltyDisplay();
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

    // ═══════════════════════════════════════════════
    // CARE PROTOCOL (POPUP)
    // ═══════════════════════════════════════════════

    @FXML
    private void handleGenerateCareProtocol() {
        if (billList.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Empty Bill", "Add items to the bill first.");
            return;
        }
        btnGenerateCareProtocol.setDisable(true);
        btnGenerateCareProtocol.setText(CARE_PROTOCOL_BUSY_LABEL);

        var ctx = org.example.MediManage.util.AIResultDialog.showLoadingPopup(
                "Patient Care Protocol", "\ud83c\udfe5",
                "\ud83d\udd04 Generating AI Care Protocol...\nThis may take a few seconds.");

        String providerInfo = billingService.getCloudProviderInfo();
        List<BillItem> careItems = billingService.snapshotItems(billList);

        billingService.generateDetailedCareProtocol(careItems)
                .thenAccept(protocol -> Platform.runLater(() -> {
                    ctx.setResult("\u2601\ufe0f " + providerInfo + "\n\n" + protocol
                            + "\n\n\u2695\ufe0f Generated by MediManage AI");
                    btnGenerateCareProtocol.setDisable(false);
                    btnGenerateCareProtocol.setText(CARE_PROTOCOL_READY_LABEL);
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        ctx.setResult("\u274c Error: " + ex.getMessage()
                                + "\n\n\ud83d\udca1 Tip: Configure your Cloud AI API key in Settings.");
                        btnGenerateCareProtocol.setDisable(false);
                        btnGenerateCareProtocol.setText(CARE_PROTOCOL_READY_LABEL);
                    });
                    return null;
                });
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
            clearPendingLoyaltyDiscount();
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
            clearPendingLoyaltyDiscount();
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

    private void clearPendingLoyaltyDiscount() {
        if (pendingLoyaltyDiscountPercent > 0.0) {
            pendingLoyaltyDiscountPercent = 0.0;
            updateTotal();
        }
    }

    private boolean isSameCustomer(Customer left, Customer right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.getCustomerId() == right.getCustomerId();
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
