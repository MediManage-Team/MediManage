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
    @FXML
    private javafx.scene.layout.VBox careProtocolContainer;
    @FXML
    private Button btnGenerateCareProtocol;

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

    @FXML
    private void handleGenerateCareProtocol() {
        if (billList.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Empty Bill", "Add items to the bill first.");
            return;
        }

        btnGenerateCareProtocol.setDisable(true);
        btnGenerateCareProtocol.setText("⏳ Generating...");
        careProtocolContainer.getChildren().clear();
        javafx.scene.control.Label loadingLabel = new javafx.scene.control.Label(
                "🔄 Generating AI Care Protocol...\nThis may take a few seconds.");
        loadingLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #666; -fx-padding: 20;");
        careProtocolContainer.getChildren().add(loadingLabel);

        StringBuilder medicineList = new StringBuilder();
        billList.forEach(item -> medicineList.append("- ").append(item.getName()).append("\n"));

        String prompt = "I am a Pharmacist. Create a 'Patient Care Protocol' for the following medicines:\n" +
                medicineList.toString() + "\n" +
                "For EACH medicine, provide these sections with EXACT section names as headers:\n" +
                "Substitutes\nMechanism\nUsage Guide\nDietary Advice\nSide Effects\nSafety Check\nStop Protocol\n\n" +
                "Also include a 'Combinational Safety' section for Drug-Drug Interactions.\n" +
                "Format each section as: 'SectionName: content on same line'. " +
                "Start each medicine with its full name on its own line. " +
                "Do NOT use markdown formatting like ** or #.";

        org.example.MediManage.service.ai.AIOrchestrator aiOrchestrator = org.example.MediManage.service.ai.AIServiceProvider
                .get().getOrchestrator();

        org.example.MediManage.service.ai.CloudAIService cloud = org.example.MediManage.service.ai.AIServiceProvider
                .get().getCloudService();
        String providerInfo = cloud.getProviderName() + " — " + cloud.getActiveModel();

        aiOrchestrator.cloudQuery(prompt)
                .thenAccept(protocol -> {
                    javafx.application.Platform.runLater(() -> {
                        buildCareProtocolCards(protocol, providerInfo);
                        btnGenerateCareProtocol.setDisable(false);
                        btnGenerateCareProtocol.setText("✨ Generate Care Protocol");
                    });
                })
                .exceptionally(ex -> {
                    javafx.application.Platform.runLater(() -> {
                        careProtocolContainer.getChildren().clear();
                        javafx.scene.control.Label errLabel = new javafx.scene.control.Label(
                                "❌ Error: " + ex.getMessage()
                                        + "\n\n💡 Tip: Configure your Cloud AI API key in Settings.");
                        errLabel.setWrapText(true);
                        errLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #D32F2F; -fx-padding: 15;");
                        careProtocolContainer.getChildren().add(errLabel);
                        btnGenerateCareProtocol.setDisable(false);
                        btnGenerateCareProtocol.setText("✨ Generate Care Protocol");
                    });
                    return null;
                });
    }

    private void buildCareProtocolCards(String raw, String providerInfo) {
        careProtocolContainer.getChildren().clear();
        String text = raw.replaceAll("\\*\\*(.+?)\\*\\*", "$1").replaceAll("(?m)^#{1,3}\\s*", "").trim();

        // Provider header
        javafx.scene.layout.VBox headerCard = new javafx.scene.layout.VBox(3);
        headerCard.setStyle(
                "-fx-background-color: #EDE7F6; -fx-padding: 12; -fx-background-radius: 6; -fx-border-color: #9c27b0; -fx-border-radius: 6; -fx-border-width: 0 0 0 4;");
        javafx.scene.control.Label headerTitle = new javafx.scene.control.Label("🏥 Patient Care Protocol");
        headerTitle.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #4A148C;");
        javafx.scene.control.Label headerProv = new javafx.scene.control.Label("☁️ " + providerInfo);
        headerProv.setStyle("-fx-font-size: 11px; -fx-text-fill: #7B1FA2;");
        headerCard.getChildren().addAll(headerTitle, headerProv);
        careProtocolContainer.getChildren().add(headerCard);

        java.util.Map<String, String[]> sc = new java.util.LinkedHashMap<>();
        sc.put("substitutes", new String[] { "#2E7D32", "#E8F5E9", "#4CAF50" });
        sc.put("mechanism", new String[] { "#00695C", "#E0F2F1", "#009688" });
        sc.put("usage guide", new String[] { "#BF360C", "#E8F5E9", "#4CAF50" });
        sc.put("dietary advice", new String[] { "#E65100", "#FFF3E0", "#FF9800" });
        sc.put("side effects", new String[] { "#B71C1C", "#FFFDE7", "#FFC107" });
        sc.put("safety check", new String[] { "#1B5E20", "#FCE4EC", "#E91E63" });
        sc.put("stop protocol", new String[] { "#C62828", "#FFEBEE", "#F44336" });
        sc.put("special precautions", new String[] { "#4A148C", "#F3E5F5", "#9C27B0" });
        sc.put("monitoring", new String[] { "#1A237E", "#E8EAF6", "#3F51B5" });
        sc.put("combinational safety", new String[] { "#B71C1C", "#FBE9E7", "#FF5722" });
        sc.put("drug-drug interaction", new String[] { "#B71C1C", "#FBE9E7", "#FF5722" });

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
                medLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 10 0 2 0;");
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
        footer.setStyle("-fx-font-size: 10px; -fx-text-fill: #999; -fx-padding: 8 0 0 0;");
        careProtocolContainer.getChildren().add(footer);
    }

    private javafx.scene.layout.VBox createSectionCard(String sectionKey, String content,
            java.util.Map<String, String[]> colorMap) {
        String[] colors = colorMap.getOrDefault(sectionKey, new String[] { "#333", "#F5F5F5", "#9E9E9E" });
        javafx.scene.layout.VBox card = new javafx.scene.layout.VBox(4);
        card.setStyle("-fx-background-color: " + colors[1]
                + "; -fx-padding: 10 12; -fx-background-radius: 5; -fx-border-color: " + colors[2]
                + "; -fx-border-radius: 5; -fx-border-width: 0 0 0 4;");
        String displayName = sectionKey.substring(0, 1).toUpperCase() + sectionKey.substring(1);
        javafx.scene.control.Label titleLabel = new javafx.scene.control.Label(displayName);
        titleLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: " + colors[0] + ";");
        javafx.scene.control.Label bodyLabel = new javafx.scene.control.Label(content);
        bodyLabel.setWrapText(true);
        bodyLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #333;");
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
