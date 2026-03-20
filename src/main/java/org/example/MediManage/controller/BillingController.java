package org.example.MediManage.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.example.MediManage.model.BillItem;
import org.example.MediManage.model.Customer;
import org.example.MediManage.model.HeldOrder;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.model.PaymentSplit;
import org.example.MediManage.model.PrescriptionDirection;
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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class BillingController {
    private static final String CUSTOMER_NAME_SELECTED_CLASS = "customer-name-selected";
    private static final String CUSTOMER_NAME_MUTED_CLASS = "customer-name-muted";
    private static final String WALK_IN_CUSTOMER_LABEL = "Walk-in Customer";
    private static final String CHECKOUT_READY_LABEL = "CHECKOUT (PRINT)";
    private static final String CHECKOUT_BUSY_LABEL = "Generating Care Protocol...";
    private static final String CARE_PROTOCOL_READY_LABEL = "Generate Care Protocol";
    private static final String CARE_PROTOCOL_BUSY_LABEL = "Generating...";

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
    private ProgressIndicator medicineSearchSpinner;
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
    @FXML
    private Button btnEditPrescriptionSchedule;
    @FXML
    private Label lblPrescriptionSummary;

    private final BillingService billingService = new BillingService();
    private final HeldOrderDAO heldOrderDAO = new HeldOrderDAO();
    private final MedicineDAO medicineDAO = new MedicineDAO();
    private final org.example.MediManage.service.LoyaltyService loyaltyService = new org.example.MediManage.service.LoyaltyService();
    private final BillingCheckoutSupport checkoutSupport = new BillingCheckoutSupport(this::showAlert);
    private final Gson gson = new Gson();

    private ObservableList<BillItem> billList = FXCollections.observableArrayList();
    private final ObservableList<Medicine> medicineSuggestions = FXCollections.observableArrayList();
    private Customer selectedCustomer = null;
    private Medicine selectedMedicine = null;
    private boolean keyboardShortcutsRegistered = false;
    private double pendingLoyaltyDiscountPercent = 0.0;
    private ScheduledFuture<?> pendingMedicineSearch;
    private int medicineSearchGeneration = 0;
    private String prescriptionHighlights = "";

    private static final int MIN_MEDICINE_SEARCH_CHARS = 2;
    private static final long MEDICINE_SEARCH_DEBOUNCE_MS = 180L;

    @FXML
    public void initialize() {
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
        updatePrescriptionSummary();
    }

    // ═══════════════════════════════════════════════
    // MEDICINE SEARCH
    // ═══════════════════════════════════════════════

    private void setupMedicineSearch() {
        listMedicineSuggestions.setItems(medicineSuggestions);
        listMedicineSuggestions.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Medicine item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getName() + " (" + item.getCompany() + ")");
                }
            }
        });

        txtSearchMedicine.textProperty().addListener((obs, oldVal, newVal) -> {
            if (selectedMedicine != null && selectedMedicine.getName().equals(newVal)) {
                setMedicineSearchLoading(false);
                return;
            }
            if (selectedMedicine != null && !selectedMedicine.getName().equals(newVal)) {
                selectedMedicine = null;
            }
            scheduleMedicineSearch(newVal);
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
                if (!medicineSuggestions.isEmpty()) {
                    selectMedicine(medicineSuggestions.get(0));
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

        setMedicineSearchLoading(false);
    }

    private void scheduleMedicineSearch(String query) {
        if (pendingMedicineSearch != null) {
            pendingMedicineSearch.cancel(false);
        }

        medicineSearchGeneration++;
        String trimmedQuery = query == null ? "" : query.trim();
        if (trimmedQuery.length() < MIN_MEDICINE_SEARCH_CHARS) {
            medicineSuggestions.clear();
            listMedicineSuggestions.setVisible(false);
            setMedicineSearchLoading(false);
            return;
        }

        setMedicineSearchLoading(true);
        final int generation = medicineSearchGeneration;
        pendingMedicineSearch = org.example.MediManage.util.AppExecutors.schedule(() -> {
            List<Medicine> results = medicineDAO.searchMedicines(trimmedQuery, 0, 15);
            Platform.runLater(() -> {
                if (generation != medicineSearchGeneration) {
                    return;
                }
                medicineSuggestions.setAll(results);
                listMedicineSuggestions.setVisible(!results.isEmpty());
                if (!results.isEmpty()) {
                    listMedicineSuggestions.setPrefHeight(Math.min(180, results.size() * 28));
                }
                setMedicineSearchLoading(false);
            });
        }, MEDICINE_SEARCH_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    private void setMedicineSearchLoading(boolean loading) {
        if (medicineSearchSpinner == null) {
            return;
        }
        medicineSearchSpinner.setVisible(loading);
        medicineSearchSpinner.setManaged(loading);
    }

    private void selectMedicine(Medicine m) {
        if (pendingMedicineSearch != null) {
            pendingMedicineSearch.cancel(false);
        }
        medicineSearchGeneration++;
        selectedMedicine = m;
        txtSearchMedicine.setText(m.getName());
        medicineSuggestions.clear();
        listMedicineSuggestions.setVisible(false);
        setMedicineSearchLoading(false);
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
        medicineSuggestions.clear();
        listMedicineSuggestions.setVisible(false);
        setMedicineSearchLoading(false);
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
                    labelText += String.format(" (Bal: Rs. %.2f)", selectedCustomer.getCurrentBalance());
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
        updatePrescriptionSummary();
        selectedMedicine = null;
        txtSearchMedicine.clear();
        txtQty.clear();
        txtSearchMedicine.requestFocus();
    }

    private void updateTotal() {
        double sum = billingService.calculateTotal(billList, pendingLoyaltyDiscountPercent);
        lblTotal.setText(String.format("Rs. %.2f", sum));
    }

    @FXML
    private void handleEditPrescriptionSchedule() {
        if (billList.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Empty Bill", "Add medicines before opening the prescription schedule.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Verified Prescription Schedule");
        dialog.setHeaderText("Review bill highlights and fill the verified schedule for each medicine.");
        dialog.setResizable(true);
        dialog.getDialogPane().setPrefWidth(1260);
        dialog.getDialogPane().setPrefHeight(860);

        ButtonType saveButtonType = new ButtonType("Save Schedule", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        Map<BillItem, PrescriptionDirection> workingDirections = new LinkedHashMap<>();
        for (BillItem item : billList) {
            workingDirections.put(item, item.getPrescriptionDirection().copy());
        }

        TextArea highlightsArea = new TextArea(prescriptionHighlights);
        highlightsArea.setPromptText("One highlight per line. Example:\nAfter food\nComplete full antibiotic course");
        highlightsArea.setPrefRowCount(6);
        highlightsArea.setPrefHeight(160);
        highlightsArea.setWrapText(true);

        Label configuredCountLabel = createPrescriptionDialogBadge("");
        Label highlightCountLabel = createMutedDialogLabel("");

        ListView<BillItem> medicineList = new ListView<>(FXCollections.observableArrayList(billList));
        medicineList.setPrefWidth(340);
        medicineList.setMinWidth(300);
        medicineList.setPrefHeight(640);
        medicineList.setPlaceholder(createMutedDialogLabel("No medicines in this bill yet."));
        medicineList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(BillItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                PrescriptionDirection direction = workingDirections.get(item);
                boolean configured = direction != null && !direction.isEmpty();
                String summary = direction == null ? "" : direction.buildSlotSummary();
                if (summary.isBlank()) {
                    summary = direction == null ? "" : direction.buildSummary();
                }

                Label nameLabel = new Label(item.getName());
                nameLabel.setWrapText(true);
                nameLabel.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 14px; -fx-font-weight: 700;");

                Label statusLabel = createPrescriptionDialogBadge(configured ? "Configured" : "Pending");
                statusLabel.setStyle((configured
                        ? "-fx-background-color: rgba(16,185,129,0.18); -fx-border-color: rgba(16,185,129,0.45);"
                        : "-fx-background-color: rgba(245,158,11,0.15); -fx-border-color: rgba(245,158,11,0.38);")
                        + " -fx-background-radius: 999; -fx-border-radius: 999;"
                        + " -fx-padding: 4 10; -fx-text-fill: #e5e7eb; -fx-font-size: 11px; -fx-font-weight: 700;");

                Label summaryLabel = new Label(summary.isBlank()
                        ? "No verified timings added yet."
                        : summary);
                summaryLabel.setWrapText(true);
                summaryLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                HBox topRow = new HBox(10, nameLabel, spacer, statusLabel);
                topRow.setAlignment(Pos.CENTER_LEFT);

                VBox content = new VBox(6, topRow, summaryLabel);
                content.setPadding(new Insets(8, 10, 8, 10));

                setText(null);
                setGraphic(content);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });

        TextField morningField = new TextField();
        TextField afternoonField = new TextField();
        TextField eveningField = new TextField();
        TextField nightField = new TextField();
        TextField exactTimeField = new TextField();
        ComboBox<String> mealRelationBox = new ComboBox<>(FXCollections.observableArrayList(
                "",
                "Before meal",
                "After meal",
                "With meal",
                "Empty stomach",
                "Any time"));
        mealRelationBox.setPromptText("Meal relation");
        TextField durationField = new TextField();
        TextArea noteArea = new TextArea();
        noteArea.setWrapText(true);
        noteArea.setPrefRowCount(6);
        noteArea.setPrefHeight(180);

        morningField.setPromptText("1 tab / 5 ml");
        afternoonField.setPromptText("1 tab / 5 ml");
        eveningField.setPromptText("1 tab / 5 ml");
        nightField.setPromptText("1 tab / 5 ml");
        exactTimeField.setPromptText("8:00 AM / bedtime");
        durationField.setPromptText("5 days / 2 weeks");
        noteArea.setPromptText("Short counselling note for this medicine");
        morningField.setMaxWidth(Double.MAX_VALUE);
        afternoonField.setMaxWidth(Double.MAX_VALUE);
        eveningField.setMaxWidth(Double.MAX_VALUE);
        nightField.setMaxWidth(Double.MAX_VALUE);
        exactTimeField.setMaxWidth(Double.MAX_VALUE);
        durationField.setMaxWidth(Double.MAX_VALUE);
        mealRelationBox.setMaxWidth(Double.MAX_VALUE);

        Label selectedMedicineLabel = new Label("Select a medicine");
        selectedMedicineLabel.setWrapText(true);
        selectedMedicineLabel.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 18px; -fx-font-weight: 700;");

        Label selectedMedicineSummaryLabel = createMutedDialogLabel(
                "Choose a medicine from the left to enter dose timing, meal relation, duration, and notes.");

        final BillItem[] selectedItemHolder = new BillItem[1];
        final boolean[] editorLoading = new boolean[1];

        Runnable clearEditor = () -> {
            morningField.clear();
            afternoonField.clear();
            eveningField.clear();
            nightField.clear();
            exactTimeField.clear();
            mealRelationBox.getSelectionModel().clearSelection();
            durationField.clear();
            noteArea.clear();
        };

        Runnable updateSidebarSummary = () -> {
            long configuredCount = workingDirections.values().stream()
                    .filter(direction -> direction != null && !direction.isEmpty())
                    .count();
            int highlightCount = countHighlightLines(highlightsArea.getText());
            configuredCountLabel.setText(configuredCount + " / " + billList.size() + " medicines configured");
            highlightCountLabel.setText(highlightCount == 0
                    ? "No bill highlights yet."
                    : highlightCount + " bill highlight" + (highlightCount == 1 ? "" : "s") + " ready for the invoice.");
        };

        Runnable updateSelectionSummary = () -> {
            BillItem selected = selectedItemHolder[0];
            if (selected == null) {
                selectedMedicineLabel.setText("Select a medicine");
                selectedMedicineSummaryLabel.setText(
                        "Choose a medicine from the left to enter dose timing, meal relation, duration, and notes.");
                return;
            }
            PrescriptionDirection direction = workingDirections.getOrDefault(selected, new PrescriptionDirection());
            selectedMedicineLabel.setText(selected.getName());
            if (direction.isEmpty()) {
                selectedMedicineSummaryLabel.setText("No verified schedule saved yet for this medicine.");
                return;
            }
            String summary = direction.buildSummary();
            selectedMedicineSummaryLabel.setText(summary.isBlank()
                    ? "Schedule details are saved for this medicine."
                    : summary);
        };

        Runnable saveEditorToSelection = () -> {
            if (editorLoading[0]) {
                return;
            }
            BillItem selected = selectedItemHolder[0];
            if (selected == null) {
                return;
            }
            PrescriptionDirection direction = new PrescriptionDirection();
            direction.setMorningDose(morningField.getText());
            direction.setAfternoonDose(afternoonField.getText());
            direction.setEveningDose(eveningField.getText());
            direction.setNightDose(nightField.getText());
            direction.setExactTime(exactTimeField.getText());
            direction.setMealRelation(mealRelationBox.getValue());
            direction.setDuration(durationField.getText());
            direction.setShortNote(noteArea.getText());
            workingDirections.put(selected, direction);
            medicineList.refresh();
            updateSelectionSummary.run();
            updateSidebarSummary.run();
        };

        Runnable loadSelectionIntoEditor = () -> {
            BillItem selected = selectedItemHolder[0];
            editorLoading[0] = true;
            if (selected == null) {
                clearEditor.run();
                editorLoading[0] = false;
                updateSelectionSummary.run();
                return;
            }
            PrescriptionDirection direction = workingDirections.getOrDefault(selected, new PrescriptionDirection());
            morningField.setText(direction.getMorningDose());
            afternoonField.setText(direction.getAfternoonDose());
            eveningField.setText(direction.getEveningDose());
            nightField.setText(direction.getNightDose());
            exactTimeField.setText(direction.getExactTime());
            mealRelationBox.setValue(direction.getMealRelation().isBlank() ? null : direction.getMealRelation());
            durationField.setText(direction.getDuration());
            noteArea.setText(direction.getShortNote());
            editorLoading[0] = false;
            updateSelectionSummary.run();
        };

        morningField.textProperty().addListener((obs, oldValue, newValue) -> saveEditorToSelection.run());
        afternoonField.textProperty().addListener((obs, oldValue, newValue) -> saveEditorToSelection.run());
        eveningField.textProperty().addListener((obs, oldValue, newValue) -> saveEditorToSelection.run());
        nightField.textProperty().addListener((obs, oldValue, newValue) -> saveEditorToSelection.run());
        exactTimeField.textProperty().addListener((obs, oldValue, newValue) -> saveEditorToSelection.run());
        durationField.textProperty().addListener((obs, oldValue, newValue) -> saveEditorToSelection.run());
        noteArea.textProperty().addListener((obs, oldValue, newValue) -> saveEditorToSelection.run());
        mealRelationBox.valueProperty().addListener((obs, oldValue, newValue) -> saveEditorToSelection.run());
        highlightsArea.textProperty().addListener((obs, oldValue, newValue) -> updateSidebarSummary.run());

        medicineList.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (oldSelection != null) {
                saveEditorToSelection.run();
            }
            selectedItemHolder[0] = newSelection;
            loadSelectionIntoEditor.run();
        });

        Button preset101 = new Button("1-0-1");
        preset101.setOnAction(e -> {
            morningField.setText("1 tab");
            afternoonField.clear();
            eveningField.setText("1 tab");
            nightField.clear();
        });

        Button preset111 = new Button("1-1-1");
        preset111.setOnAction(e -> {
            morningField.setText("1 tab");
            afternoonField.setText("1 tab");
            eveningField.setText("1 tab");
            nightField.clear();
        });

        Button preset001 = new Button("0-0-1");
        preset001.setOnAction(e -> {
            morningField.clear();
            afternoonField.clear();
            eveningField.clear();
            nightField.setText("1 tab");
        });

        Button clearCurrent = new Button("Clear Current");
        clearCurrent.setOnAction(e -> {
            clearEditor.run();
            saveEditorToSelection.run();
        });

        HBox presetsRow = new HBox(8, preset101, preset111, preset001, clearCurrent);
        presetsRow.setAlignment(Pos.CENTER_RIGHT);

        GridPane editorGrid = new GridPane();
        editorGrid.setHgap(18);
        editorGrid.setVgap(18);
        editorGrid.setMaxWidth(Double.MAX_VALUE);
        ColumnConstraints firstEditorColumn = new ColumnConstraints();
        firstEditorColumn.setFillWidth(true);
        firstEditorColumn.setHgrow(Priority.ALWAYS);
        ColumnConstraints secondEditorColumn = new ColumnConstraints();
        secondEditorColumn.setFillWidth(true);
        secondEditorColumn.setHgrow(Priority.ALWAYS);
        editorGrid.getColumnConstraints().addAll(firstEditorColumn, secondEditorColumn);
        editorGrid.add(createPrescriptionFieldGroup("Morning", morningField, "Dose to be taken in the morning slot."), 0, 0);
        editorGrid.add(createPrescriptionFieldGroup("Afternoon", afternoonField, "Dose to be taken in the afternoon slot."), 1, 0);
        editorGrid.add(createPrescriptionFieldGroup("Evening", eveningField, "Dose to be taken in the evening slot."), 0, 1);
        editorGrid.add(createPrescriptionFieldGroup("Night", nightField, "Dose to be taken at night or bedtime."), 1, 1);
        editorGrid.add(createPrescriptionFieldGroup("Exact Time", exactTimeField, "Optional exact time such as 8:00 AM or bedtime."), 0, 2);
        editorGrid.add(createPrescriptionFieldGroup("Meal Relation", mealRelationBox, "State whether it should be taken before, after, or with food."), 1, 2);
        editorGrid.add(createPrescriptionFieldGroup("Duration", durationField, "Optional length such as 5 days or 2 weeks."), 0, 3);
        editorGrid.add(createPrescriptionFieldGroup("Short Note", noteArea, "Optional counselling note printed for this medicine."), 1, 3);
        GridPane.setHgrow(editorGrid.getChildren().get(0), Priority.ALWAYS);
        GridPane.setHgrow(editorGrid.getChildren().get(1), Priority.ALWAYS);
        GridPane.setHgrow(editorGrid.getChildren().get(2), Priority.ALWAYS);
        GridPane.setHgrow(editorGrid.getChildren().get(3), Priority.ALWAYS);
        GridPane.setHgrow(editorGrid.getChildren().get(4), Priority.ALWAYS);
        GridPane.setHgrow(editorGrid.getChildren().get(5), Priority.ALWAYS);
        GridPane.setHgrow(editorGrid.getChildren().get(6), Priority.ALWAYS);
        GridPane.setHgrow(editorGrid.getChildren().get(7), Priority.ALWAYS);

        HBox medicineHeader = new HBox(12);
        Label medicineListTitle = new Label("Medicines in This Bill");
        medicineListTitle.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 18px; -fx-font-weight: 700;");
        Label medicineListHint = createMutedDialogLabel("Select one medicine at a time. The list shows which schedules are still pending.");
        VBox medicineHeaderText = new VBox(4, medicineListTitle, medicineListHint);
        Region medicineHeaderSpacer = new Region();
        HBox.setHgrow(medicineHeaderSpacer, Priority.ALWAYS);
        medicineHeader.getChildren().addAll(medicineHeaderText, medicineHeaderSpacer, configuredCountLabel);
        medicineHeader.setAlignment(Pos.TOP_LEFT);

        VBox medicinePane = createPrescriptionSection(
                medicineHeader,
                medicineList,
                highlightCountLabel);
        VBox.setVgrow(medicineList, Priority.ALWAYS);

        HBox scheduleHeader = new HBox(16);
        VBox selectedMedicineBox = new VBox(6, selectedMedicineLabel, selectedMedicineSummaryLabel);
        Region scheduleHeaderSpacer = new Region();
        HBox.setHgrow(scheduleHeaderSpacer, Priority.ALWAYS);
        HBox.setHgrow(selectedMedicineBox, Priority.ALWAYS);
        scheduleHeader.getChildren().addAll(selectedMedicineBox, scheduleHeaderSpacer, presetsRow);
        scheduleHeader.setAlignment(Pos.CENTER_LEFT);

        VBox highlightPane = createPrescriptionSection(
                createPrescriptionDialogTitle("Bill Highlights"),
                createMutedDialogLabel("Add one verified highlight per line. These appear above the schedule table on the invoice."),
                highlightsArea);

        VBox editorPane = createPrescriptionSection(
                createPrescriptionDialogTitle("Per-Medicine Schedule"),
                createMutedDialogLabel("Use the selected medicine card below to fill its verified timing, meal relation, duration, and note."),
                scheduleHeader,
                editorGrid);

        VBox rightColumn = new VBox(18, highlightPane, editorPane);
        rightColumn.setFillWidth(true);

        ScrollPane rightScroll = new ScrollPane(rightColumn);
        rightScroll.setFitToWidth(true);
        rightScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rightScroll.setStyle("-fx-background-color: transparent;");

        SplitPane splitPane = new SplitPane(medicinePane, rightScroll);
        splitPane.setDividerPositions(0.31);

        VBox root = new VBox(splitPane);
        root.setPadding(new Insets(14));
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        dialog.getDialogPane().setContent(root);

        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        if (saveButton != null) {
            saveButton.setDefaultButton(true);
        }

        medicineList.getSelectionModel().selectFirst();
        updateSidebarSummary.run();

        dialog.setResultConverter(buttonType -> {
            if (buttonType == saveButtonType) {
                saveEditorToSelection.run();
                return saveButtonType;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            if (result == saveButtonType) {
                for (BillItem item : billList) {
                    item.setPrescriptionDirection(workingDirections.get(item));
                }
                prescriptionHighlights = normalizeMultilineText(highlightsArea.getText());
                billingTable.refresh();
                updatePrescriptionSummary();
            }
        });
    }

    private VBox createPrescriptionSection(Node... children) {
        VBox box = new VBox(12, children);
        box.setPadding(new Insets(18));
        box.setStyle(
                "-fx-background-color: rgba(15,23,42,0.68);"
                        + " -fx-background-radius: 16;"
                        + " -fx-border-color: rgba(96,165,250,0.18);"
                        + " -fx-border-radius: 16;");
        return box;
    }

    private Label createPrescriptionDialogTitle(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill: #f8fafc; -fx-font-size: 18px; -fx-font-weight: 700;");
        return label;
    }

    private Label createMutedDialogLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
        return label;
    }

    private Label createPrescriptionDialogBadge(String text) {
        Label label = new Label(text);
        label.setStyle(
                "-fx-background-color: rgba(59,130,246,0.18);"
                        + " -fx-border-color: rgba(96,165,250,0.38);"
                        + " -fx-background-radius: 999;"
                        + " -fx-border-radius: 999;"
                        + " -fx-padding: 5 12;"
                        + " -fx-text-fill: #dbeafe;"
                        + " -fx-font-size: 11px;"
                        + " -fx-font-weight: 700;");
        return label;
    }

    private VBox createPrescriptionFieldGroup(String labelText, Control input, String helperText) {
        Label titleLabel = new Label(labelText);
        titleLabel.setStyle("-fx-text-fill: #e5e7eb; -fx-font-size: 13px; -fx-font-weight: 700;");

        VBox fieldGroup = new VBox(6, titleLabel, input);
        if (helperText != null && !helperText.isBlank()) {
            fieldGroup.getChildren().add(createMutedDialogLabel(helperText));
        }
        fieldGroup.setFillWidth(true);
        input.setMaxWidth(Double.MAX_VALUE);
        if (input instanceof TextArea) {
            VBox.setVgrow(input, Priority.ALWAYS);
        }
        return fieldGroup;
    }

    private void updatePrescriptionSummary() {
        if (lblPrescriptionSummary == null) {
            return;
        }
        int itemCount = billList.size();
        if (itemCount == 0) {
            lblPrescriptionSummary.setText("No medicines added yet.");
            return;
        }
        long configuredCount = billList.stream()
                .filter(BillItem::hasPrescriptionDirection)
                .count();
        int highlightCount = countHighlightLines(prescriptionHighlights);
        String highlightsPart = highlightCount == 0
                ? "No bill highlights yet"
                : highlightCount + " highlight" + (highlightCount == 1 ? "" : "s");
        lblPrescriptionSummary.setText(configuredCount + " of " + itemCount
                + " medicines configured. " + highlightsPart + ".");
    }

    private int countHighlightLines(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return (int) value.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .count();
    }

    private String normalizeMultilineText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r\n", "\n").trim();
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
                            prescriptionHighlights,
                            redeemLoyalty);

                    checkoutSupport.showPostCheckoutDialog(checkoutResult, checkoutCustomer, totalAmount, careProtocol);

                    billList.clear();
                    prescriptionHighlights = "";
                    clearPendingLoyaltyDiscount();
                    updateTotal();
                    updatePrescriptionSummary();
                    selectedCustomer = null;
                    lblCustomerName.setText(WALK_IN_CUSTOMER_LABEL);
                    applyCustomerNameStyle(false);
                    if (lblCustomerPhone != null)
                        lblCustomerPhone.setText("-");
                    updateLoyaltyDisplay();
                    txtSearchCustomer.clear();
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
                "Patient Care Protocol", "",
                "Generating AI Care Protocol...\nThis may take a few seconds.");

        String providerInfo = billingService.getCloudProviderInfo();
        List<BillItem> careItems = billingService.snapshotItems(billList);

        billingService.generateDetailedCareProtocol(careItems)
                .thenAccept(protocol -> Platform.runLater(() -> {
                    ctx.setResult("Patient Care Protocol\n\n"
                            + "Provider: " + providerInfo + "\n\n"
                            + protocol
                            + "\n\nSource: MediManage AI");
                    btnGenerateCareProtocol.setDisable(false);
                    btnGenerateCareProtocol.setText(CARE_PROTOCOL_READY_LABEL);
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        ctx.setResult("Patient Care Protocol\n\n"
                                + "Status: Unable to generate care protocol\n\n"
                                + "Error: " + ex.getMessage()
                                + "\n\nTip: Configure your Cloud AI API key in Settings.");
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
                            + "\n\nAssign barcodes via Inventory -> right-click any row -> Edit Barcode.");
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
                if (item.hasPrescriptionDirection()) {
                    m.put("prescriptionDirection", item.getPrescriptionDirection());
                }
                itemMaps.add(m);
            }
            Map<String, Object> holdPayload = new LinkedHashMap<>();
            holdPayload.put("items", itemMaps);
            holdPayload.put("prescriptionHighlights", prescriptionHighlights);
            String json = gson.toJson(holdPayload);
            double total = billingService.calculateTotal(billList);
            Integer cid = selectedCustomer != null ? selectedCustomer.getCustomerId() : null;
            int userId = org.example.MediManage.util.UserSession.getInstance().getUser().getId();
            int holdId = heldOrderDAO.holdOrder(cid, userId, json, total, notesResult.get());
            showAlert(Alert.AlertType.INFORMATION, "Order Held",
                    "Order #" + holdId + " held successfully (" + billList.size() + " items, Rs. "
                            + String.format("%.2f", total) + ").");
            billList.clear();
            prescriptionHighlights = "";
            clearPendingLoyaltyDiscount();
            updateTotal();
            updatePrescriptionSummary();
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

            List<Map<String, Object>> itemMaps;
            String recalledHighlights = "";
            Object payload = gson.fromJson(selected.getItemsJson(), Object.class);
            if (payload instanceof Map<?, ?> payloadMap && payloadMap.get("items") instanceof List<?> rawItems) {
                itemMaps = gson.fromJson(gson.toJson(rawItems),
                        new TypeToken<List<Map<String, Object>>>() {
                        }.getType());
                Object rawHighlights = payloadMap.get("prescriptionHighlights");
                if (rawHighlights != null) {
                    recalledHighlights = normalizeMultilineText(rawHighlights.toString());
                }
            } else {
                itemMaps = gson.fromJson(selected.getItemsJson(),
                        new TypeToken<List<Map<String, Object>>>() {
                        }.getType());
            }
            billList.clear();
            clearPendingLoyaltyDiscount();
            for (Map<String, Object> m : itemMaps) {
                int medId = ((Number) m.get("medicineId")).intValue();
                String name = (String) m.get("name");
                int qty = ((Number) m.get("qty")).intValue();
                double price = ((Number) m.get("price")).doubleValue();
                BillItem item = new BillItem(medId, name, null, qty, price, 0.0);
                Object rawDirection = m.get("prescriptionDirection");
                if (rawDirection != null) {
                    PrescriptionDirection direction = gson.fromJson(gson.toJson(rawDirection), PrescriptionDirection.class);
                    item.setPrescriptionDirection(direction);
                }
                billList.add(item);
            }
            prescriptionHighlights = recalledHighlights;
            updateTotal();
            updatePrescriptionSummary();
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
