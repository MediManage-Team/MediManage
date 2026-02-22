package org.example.MediManage;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.example.MediManage.config.FeatureFlag;
import org.example.MediManage.config.FeatureFlags;
import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.model.SubscriptionDiscountOverride;
import org.example.MediManage.model.SubscriptionPlan;
import org.example.MediManage.model.SubscriptionPlanMedicineRule;
import org.example.MediManage.model.SubscriptionPlanStatus;
import org.example.MediManage.model.User;
import org.example.MediManage.security.Permission;
import org.example.MediManage.security.RbacPolicy;
import org.example.MediManage.service.SubscriptionApprovalService;
import org.example.MediManage.service.SubscriptionService;
import org.example.MediManage.util.UserSession;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class SubscriptionAdminController {
    @FXML
    private TabPane subscriptionTabPane;
    @FXML
    private Tab approvalsTab;

    @FXML
    private TableView<SubscriptionPlan> planTable;
    @FXML
    private TableColumn<SubscriptionPlan, Number> colPlanId;
    @FXML
    private TableColumn<SubscriptionPlan, String> colPlanCode;
    @FXML
    private TableColumn<SubscriptionPlan, String> colPlanName;
    @FXML
    private TableColumn<SubscriptionPlan, String> colPlanStatus;
    @FXML
    private TableColumn<SubscriptionPlan, Number> colPlanPrice;
    @FXML
    private TableColumn<SubscriptionPlan, Number> colPlanDefaultDiscount;
    @FXML
    private TableColumn<SubscriptionPlan, Number> colPlanMaxDiscount;

    @FXML
    private TextField txtPlanCode;
    @FXML
    private TextField txtPlanName;
    @FXML
    private TextArea txtPlanDescription;
    @FXML
    private TextField txtPlanPrice;
    @FXML
    private TextField txtPlanDurationDays;
    @FXML
    private TextField txtPlanGraceDays;
    @FXML
    private TextField txtPlanDefaultDiscount;
    @FXML
    private TextField txtPlanMaxDiscount;
    @FXML
    private TextField txtPlanMinimumMargin;
    @FXML
    private CheckBox chkPlanAutoRenew;
    @FXML
    private CheckBox chkPlanRequiresApproval;
    @FXML
    private Button btnSavePlan;
    @FXML
    private Button btnActivatePlan;
    @FXML
    private Button btnPausePlan;
    @FXML
    private Button btnRetirePlan;
    @FXML
    private Label lblSelectedPlanSummary;

    @FXML
    private VBox ruleEditorBox;
    @FXML
    private Label lblRulePlanContext;
    @FXML
    private TableView<SubscriptionPlanMedicineRule> ruleTable;
    @FXML
    private TableColumn<SubscriptionPlanMedicineRule, Number> colRuleId;
    @FXML
    private TableColumn<SubscriptionPlanMedicineRule, String> colRuleMedicine;
    @FXML
    private TableColumn<SubscriptionPlanMedicineRule, Boolean> colRuleInclude;
    @FXML
    private TableColumn<SubscriptionPlanMedicineRule, Number> colRuleDiscountPercent;
    @FXML
    private TableColumn<SubscriptionPlanMedicineRule, Number> colRuleMaxDiscountAmount;
    @FXML
    private TableColumn<SubscriptionPlanMedicineRule, Number> colRuleMinMarginPercent;
    @FXML
    private TableColumn<SubscriptionPlanMedicineRule, Boolean> colRuleActive;
    @FXML
    private ComboBox<Medicine> cmbRuleMedicine;
    @FXML
    private CheckBox chkRuleInclude;
    @FXML
    private CheckBox chkRuleActive;
    @FXML
    private TextField txtRuleDiscountPercent;
    @FXML
    private TextField txtRuleMaxDiscountAmount;
    @FXML
    private TextField txtRuleMinMarginPercent;
    @FXML
    private Button btnSaveRule;
    @FXML
    private Button btnDeleteRule;

    @FXML
    private TableView<SubscriptionDiscountOverride> overrideTable;
    @FXML
    private TableColumn<SubscriptionDiscountOverride, Number> colOverrideId;
    @FXML
    private TableColumn<SubscriptionDiscountOverride, Number> colOverrideCustomerId;
    @FXML
    private TableColumn<SubscriptionDiscountOverride, Number> colOverrideEnrollmentId;
    @FXML
    private TableColumn<SubscriptionDiscountOverride, Number> colOverrideRequestedDiscount;
    @FXML
    private TableColumn<SubscriptionDiscountOverride, String> colOverrideReason;
    @FXML
    private TableColumn<SubscriptionDiscountOverride, Number> colOverrideRequestedBy;
    @FXML
    private TableColumn<SubscriptionDiscountOverride, String> colOverrideCreatedAt;
    @FXML
    private Label lblOverrideSelection;
    @FXML
    private TextField txtOverrideApprovedPercent;
    @FXML
    private TextArea txtOverrideDecisionReason;
    @FXML
    private Button btnApproveOverride;
    @FXML
    private Button btnRejectOverride;

    private final SubscriptionService subscriptionService = new SubscriptionService();
    private final SubscriptionApprovalService approvalService = new SubscriptionApprovalService();
    private final MedicineDAO medicineDAO = new MedicineDAO();

    private final ObservableList<SubscriptionPlan> planRows = FXCollections.observableArrayList();
    private final ObservableList<SubscriptionPlanMedicineRule> ruleRows = FXCollections.observableArrayList();
    private final ObservableList<SubscriptionDiscountOverride> overrideRows = FXCollections.observableArrayList();
    private final ObservableList<Medicine> medicineRows = FXCollections.observableArrayList();

    private SubscriptionPlan selectedPlan;
    private SubscriptionPlanMedicineRule selectedRule;
    private SubscriptionDiscountOverride selectedOverride;
    private boolean approvalsEnabled;

    @FXML
    public void initialize() {
        RbacPolicy.requireCurrentUser(Permission.MANAGE_SUBSCRIPTION_POLICY);

        configurePlanTable();
        configureRuleTable();
        configureOverrideTable();
        configureMedicinePicker();
        configureFeatureFlags();

        loadMedicines();
        loadPlans();
        if (approvalsEnabled) {
            loadPendingOverrides();
        }

        clearPlanForm();
        clearRuleForm();
        clearOverrideForm();
    }

    private void configurePlanTable() {
        colPlanId.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().planId()));
        colPlanCode.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().planCode()));
        colPlanName.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().planName()));
        colPlanStatus.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().status().name()));
        colPlanPrice.setCellValueFactory(cd -> new SimpleDoubleProperty(cd.getValue().price()));
        colPlanDefaultDiscount.setCellValueFactory(cd -> new SimpleDoubleProperty(cd.getValue().defaultDiscountPercent()));
        colPlanMaxDiscount.setCellValueFactory(cd -> new SimpleDoubleProperty(cd.getValue().maxDiscountPercent()));
        planTable.setItems(planRows);
        planTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            selectedPlan = newValue;
            populatePlanForm(newValue);
            loadPlanRules();
            updatePlanActionState();
        });
    }

    private void configureRuleTable() {
        colRuleId.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().ruleId()));
        colRuleMedicine.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().medicineName()));
        colRuleInclude.setCellValueFactory(cd -> new SimpleBooleanProperty(cd.getValue().includeRule()));
        colRuleDiscountPercent.setCellValueFactory(cd -> new SimpleDoubleProperty(cd.getValue().discountPercent()));
        colRuleMaxDiscountAmount.setCellValueFactory(
                cd -> new SimpleDoubleProperty(cd.getValue().maxDiscountAmount() == null ? 0.0 : cd.getValue().maxDiscountAmount()));
        colRuleMinMarginPercent.setCellValueFactory(
                cd -> new SimpleDoubleProperty(cd.getValue().minMarginPercent() == null ? 0.0 : cd.getValue().minMarginPercent()));
        colRuleActive.setCellValueFactory(cd -> new SimpleBooleanProperty(cd.getValue().active()));

        ruleTable.setItems(ruleRows);
        ruleTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            selectedRule = newValue;
            populateRuleForm(newValue);
        });
    }

    private void configureOverrideTable() {
        colOverrideId.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().overrideId()));
        colOverrideCustomerId.setCellValueFactory(
                cd -> new SimpleIntegerProperty(cd.getValue().customerId() == null ? 0 : cd.getValue().customerId()));
        colOverrideEnrollmentId.setCellValueFactory(
                cd -> new SimpleIntegerProperty(cd.getValue().enrollmentId() == null ? 0 : cd.getValue().enrollmentId()));
        colOverrideRequestedDiscount
                .setCellValueFactory(cd -> new SimpleDoubleProperty(cd.getValue().requestedDiscountPercent()));
        colOverrideReason.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().reason()));
        colOverrideRequestedBy.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().requestedByUserId()));
        colOverrideCreatedAt.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().createdAt()));

        overrideTable.setItems(overrideRows);
        overrideTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            selectedOverride = newValue;
            populateOverrideForm(newValue);
        });
    }

    private void configureMedicinePicker() {
        cmbRuleMedicine.setItems(medicineRows);
        cmbRuleMedicine.setConverter(new StringConverter<>() {
            @Override
            public String toString(Medicine medicine) {
                if (medicine == null) {
                    return "";
                }
                return medicine.getName() + " (" + medicine.getCompany() + ")";
            }

            @Override
            public Medicine fromString(String string) {
                return null;
            }
        });
    }

    private void configureFeatureFlags() {
        User currentUser = UserSession.getInstance().getUser();
        boolean canApprove = currentUser != null
                && RbacPolicy.canAccess(currentUser.getRole(), Permission.APPROVE_SUBSCRIPTION_OVERRIDES);
        approvalsEnabled = FeatureFlags.isEnabled(FeatureFlag.SUBSCRIPTION_APPROVALS)
                && FeatureFlags.isEnabled(FeatureFlag.SUBSCRIPTION_DISCOUNT_OVERRIDES)
                && canApprove;

        if (!approvalsEnabled && approvalsTab != null && subscriptionTabPane != null) {
            subscriptionTabPane.getTabs().remove(approvalsTab);
        }
    }

    @FXML
    private void handleRefreshAll() {
        loadMedicines();
        loadPlans();
        if (approvalsEnabled) {
            loadPendingOverrides();
        }
    }

    @FXML
    private void handleRefreshPlans() {
        loadPlans();
    }

    @FXML
    private void handleSavePlan() {
        try {
            SubscriptionPlan payload = buildPlanPayload();
            if (selectedPlan == null) {
                int createdPlanId = subscriptionService.createPlan(payload);
                showAlert(Alert.AlertType.INFORMATION, "Plan Created", "Subscription plan created.");
                loadPlans();
                selectPlanById(createdPlanId);
            } else {
                subscriptionService.updatePlan(payload);
                showAlert(Alert.AlertType.INFORMATION, "Plan Updated", "Subscription plan updated.");
                loadPlans();
                selectPlanById(payload.planId());
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Plan Save Failed", e.getMessage());
        }
    }

    @FXML
    private void handleClearPlan() {
        planTable.getSelectionModel().clearSelection();
        selectedPlan = null;
        clearPlanForm();
        loadPlanRules();
        updatePlanActionState();
    }

    @FXML
    private void handleActivatePlan() {
        updatePlanStatus(SubscriptionPlanStatus.ACTIVE);
    }

    @FXML
    private void handlePausePlan() {
        updatePlanStatus(SubscriptionPlanStatus.PAUSED);
    }

    @FXML
    private void handleRetirePlan() {
        updatePlanStatus(SubscriptionPlanStatus.RETIRED);
    }

    @FXML
    private void handleRefreshRules() {
        loadPlanRules();
    }

    @FXML
    private void handleSaveRule() {
        if (selectedPlan == null) {
            showAlert(Alert.AlertType.WARNING, "No Plan Selected", "Select a subscription plan first.");
            return;
        }
        Medicine medicine = cmbRuleMedicine.getValue();
        if (medicine == null) {
            showAlert(Alert.AlertType.WARNING, "Validation Error", "Select a medicine for the rule.");
            return;
        }

        try {
            double discountPercent = parseRequiredDouble(txtRuleDiscountPercent, "Rule discount percent");
            Double maxDiscountAmount = parseOptionalDouble(txtRuleMaxDiscountAmount, "Rule max discount amount");
            Double minMarginPercent = parseOptionalDouble(txtRuleMinMarginPercent, "Rule minimum margin percent");

            SubscriptionPlanMedicineRule rule = new SubscriptionPlanMedicineRule(
                    selectedRule == null ? 0 : selectedRule.ruleId(),
                    selectedPlan.planId(),
                    medicine.getId(),
                    medicine.getName(),
                    chkRuleInclude.isSelected(),
                    discountPercent,
                    maxDiscountAmount,
                    minMarginPercent,
                    chkRuleActive.isSelected(),
                    selectedRule == null ? null : selectedRule.createdAt(),
                    null);

            String ruleAction = selectedRule == null ? "create" : "update";
            if (!confirmSensitiveRuleChange(ruleAction, medicine.getName())) {
                return;
            }

            subscriptionService.upsertPlanMedicineRule(rule);
            showAlert(Alert.AlertType.INFORMATION, "Rule Saved", "Plan medicine rule saved.");
            loadPlanRules();
            selectRuleByMedicineId(medicine.getId());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Rule Save Failed", e.getMessage());
        }
    }

    @FXML
    private void handleDeleteRule() {
        if (selectedRule == null) {
            showAlert(Alert.AlertType.WARNING, "No Rule Selected", "Select a medicine rule to delete.");
            return;
        }
        if (!confirmSensitiveRuleChange("delete", selectedRule.medicineName())) {
            return;
        }
        try {
            subscriptionService.deletePlanMedicineRule(selectedRule.ruleId());
            showAlert(Alert.AlertType.INFORMATION, "Rule Deleted", "Plan medicine rule deleted.");
            loadPlanRules();
            clearRuleForm();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Rule Delete Failed", e.getMessage());
        }
    }

    @FXML
    private void handleClearRule() {
        ruleTable.getSelectionModel().clearSelection();
        clearRuleForm();
    }

    @FXML
    private void handleRefreshOverrides() {
        if (!approvalsEnabled) {
            return;
        }
        loadPendingOverrides();
    }

    @FXML
    private void handleApproveOverride() {
        if (!approvalsEnabled) {
            return;
        }
        if (selectedOverride == null) {
            showAlert(Alert.AlertType.WARNING, "No Override Selected", "Select an override request first.");
            return;
        }
        try {
            double approvedPercent = txtOverrideApprovedPercent.getText() == null
                    || txtOverrideApprovedPercent.getText().trim().isEmpty()
                            ? selectedOverride.requestedDiscountPercent()
                            : parseRequiredDouble(txtOverrideApprovedPercent, "Approved discount percent");
            String reason = requireText(txtOverrideDecisionReason, "Decision reason");
            approvalService.approveManualOverride(selectedOverride.overrideId(), approvedPercent, reason);
            showAlert(Alert.AlertType.INFORMATION, "Override Approved", "Override request approved.");
            loadPendingOverrides();
            clearOverrideForm();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Approve Failed", e.getMessage());
        }
    }

    @FXML
    private void handleRejectOverride() {
        if (!approvalsEnabled) {
            return;
        }
        if (selectedOverride == null) {
            showAlert(Alert.AlertType.WARNING, "No Override Selected", "Select an override request first.");
            return;
        }
        try {
            String reason = requireText(txtOverrideDecisionReason, "Decision reason");
            approvalService.rejectManualOverride(selectedOverride.overrideId(), reason);
            showAlert(Alert.AlertType.INFORMATION, "Override Rejected", "Override request rejected.");
            loadPendingOverrides();
            clearOverrideForm();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Reject Failed", e.getMessage());
        }
    }

    @FXML
    private void handleClearOverrideDecision() {
        overrideTable.getSelectionModel().clearSelection();
        clearOverrideForm();
    }

    private void loadMedicines() {
        List<Medicine> medicines = medicineDAO.getAllMedicines();
        medicineRows.setAll(medicines);
    }

    private void loadPlans() {
        planRows.setAll(subscriptionService.getPlans());
        if (planRows.isEmpty()) {
            selectedPlan = null;
            clearPlanForm();
            loadPlanRules();
            updatePlanActionState();
            return;
        }
        if (selectedPlan != null) {
            selectPlanById(selectedPlan.planId());
            return;
        }
        planTable.getSelectionModel().selectFirst();
    }

    private void loadPlanRules() {
        if (selectedPlan == null) {
            ruleRows.clear();
            selectedRule = null;
            lblRulePlanContext.setText("Select a plan to configure medicine rules.");
            ruleEditorBox.setDisable(true);
            btnDeleteRule.setDisable(true);
            return;
        }
        ruleRows.setAll(subscriptionService.getPlanMedicineRules(selectedPlan.planId()));
        selectedRule = null;
        ruleTable.getSelectionModel().clearSelection();
        lblRulePlanContext.setText("Plan: " + selectedPlan.planName() + " (" + selectedPlan.planCode() + ")");
        ruleEditorBox.setDisable(false);
        btnDeleteRule.setDisable(true);
    }

    private void loadPendingOverrides() {
        try {
            overrideRows.setAll(approvalService.getPendingOverrides());
            if (overrideRows.isEmpty()) {
                lblOverrideSelection.setText("No pending overrides.");
            }
        } catch (SecurityException e) {
            showAlert(Alert.AlertType.ERROR, "Access Denied", e.getMessage());
        }
    }

    private void populatePlanForm(SubscriptionPlan plan) {
        if (plan == null) {
            clearPlanForm();
            return;
        }
        txtPlanCode.setText(plan.planCode());
        txtPlanName.setText(plan.planName());
        txtPlanDescription.setText(plan.description() == null ? "" : plan.description());
        txtPlanPrice.setText(formatNumber(plan.price()));
        txtPlanDurationDays.setText(String.valueOf(plan.durationDays()));
        txtPlanGraceDays.setText(String.valueOf(plan.graceDays()));
        txtPlanDefaultDiscount.setText(formatNumber(plan.defaultDiscountPercent()));
        txtPlanMaxDiscount.setText(formatNumber(plan.maxDiscountPercent()));
        txtPlanMinimumMargin.setText(formatNumber(plan.minimumMarginPercent()));
        chkPlanAutoRenew.setSelected(plan.autoRenew());
        chkPlanRequiresApproval.setSelected(plan.requiresApproval());
        btnSavePlan.setText("Update Plan");
        lblSelectedPlanSummary.setText("Selected: " + plan.planName() + " [" + plan.status().name() + "]");
    }

    private void clearPlanForm() {
        txtPlanCode.clear();
        txtPlanName.clear();
        txtPlanDescription.clear();
        txtPlanPrice.clear();
        txtPlanDurationDays.clear();
        txtPlanGraceDays.clear();
        txtPlanDefaultDiscount.clear();
        txtPlanMaxDiscount.clear();
        txtPlanMinimumMargin.clear();
        chkPlanAutoRenew.setSelected(false);
        chkPlanRequiresApproval.setSelected(true);
        lblSelectedPlanSummary.setText("Selected: none");
        btnSavePlan.setText("Create Plan");
    }

    private void populateRuleForm(SubscriptionPlanMedicineRule rule) {
        if (rule == null) {
            clearRuleForm();
            return;
        }
        findMedicine(rule.medicineId()).ifPresent(cmbRuleMedicine::setValue);
        chkRuleInclude.setSelected(rule.includeRule());
        chkRuleActive.setSelected(rule.active());
        txtRuleDiscountPercent.setText(formatNumber(rule.discountPercent()));
        txtRuleMaxDiscountAmount.setText(rule.maxDiscountAmount() == null ? "" : formatNumber(rule.maxDiscountAmount()));
        txtRuleMinMarginPercent.setText(rule.minMarginPercent() == null ? "" : formatNumber(rule.minMarginPercent()));
        btnDeleteRule.setDisable(false);
    }

    private void clearRuleForm() {
        selectedRule = null;
        cmbRuleMedicine.setValue(null);
        chkRuleInclude.setSelected(true);
        chkRuleActive.setSelected(true);
        txtRuleDiscountPercent.clear();
        txtRuleMaxDiscountAmount.clear();
        txtRuleMinMarginPercent.clear();
        btnDeleteRule.setDisable(true);
    }

    private void populateOverrideForm(SubscriptionDiscountOverride override) {
        if (override == null) {
            clearOverrideForm();
            return;
        }
        lblOverrideSelection.setText("Selected override #" + override.overrideId()
                + " | Requested: " + formatNumber(override.requestedDiscountPercent()) + "%");
        txtOverrideApprovedPercent.setText(formatNumber(override.requestedDiscountPercent()));
        btnApproveOverride.setDisable(false);
        btnRejectOverride.setDisable(false);
    }

    private void clearOverrideForm() {
        selectedOverride = null;
        txtOverrideApprovedPercent.clear();
        txtOverrideDecisionReason.clear();
        btnApproveOverride.setDisable(true);
        btnRejectOverride.setDisable(true);
        lblOverrideSelection.setText("Select a pending override to review.");
    }

    private void updatePlanStatus(SubscriptionPlanStatus targetStatus) {
        if (selectedPlan == null) {
            showAlert(Alert.AlertType.WARNING, "No Plan Selected", "Select a plan first.");
            return;
        }
        try {
            switch (targetStatus) {
                case ACTIVE:
                    subscriptionService.activatePlan(selectedPlan.planId());
                    break;
                case PAUSED:
                    subscriptionService.pausePlan(selectedPlan.planId());
                    break;
                case RETIRED:
                    subscriptionService.retirePlan(selectedPlan.planId());
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported status: " + targetStatus);
            }
            loadPlans();
            selectPlanById(selectedPlan.planId());
        } catch (SQLException | RuntimeException e) {
            showAlert(Alert.AlertType.ERROR, "Status Update Failed", e.getMessage());
        }
    }

    private void updatePlanActionState() {
        boolean hasPlan = selectedPlan != null;
        btnActivatePlan.setDisable(!hasPlan || selectedPlan.status() == SubscriptionPlanStatus.ACTIVE);
        btnPausePlan.setDisable(!hasPlan || selectedPlan.status() != SubscriptionPlanStatus.ACTIVE);
        btnRetirePlan.setDisable(!hasPlan || selectedPlan.status() == SubscriptionPlanStatus.RETIRED);
    }

    private SubscriptionPlan buildPlanPayload() {
        String code = requireText(txtPlanCode, "Plan code");
        String name = requireText(txtPlanName, "Plan name");
        String description = txtPlanDescription.getText() == null ? "" : txtPlanDescription.getText().trim();
        double price = parseRequiredDouble(txtPlanPrice, "Plan price");
        int durationDays = parseRequiredInt(txtPlanDurationDays, "Duration days");
        int graceDays = parseRequiredInt(txtPlanGraceDays, "Grace days");
        double defaultDiscountPercent = parseRequiredDouble(txtPlanDefaultDiscount, "Default discount percent");
        double maxDiscountPercent = parseRequiredDouble(txtPlanMaxDiscount, "Max discount percent");
        double minimumMarginPercent = parseRequiredDouble(txtPlanMinimumMargin, "Minimum margin percent");

        if (selectedPlan == null) {
            return new SubscriptionPlan(
                    0,
                    code,
                    name,
                    description,
                    price,
                    durationDays,
                    graceDays,
                    defaultDiscountPercent,
                    maxDiscountPercent,
                    minimumMarginPercent,
                    SubscriptionPlanStatus.DRAFT,
                    chkPlanAutoRenew.isSelected(),
                    chkPlanRequiresApproval.isSelected(),
                    null,
                    null,
                    null,
                    null);
        }

        return new SubscriptionPlan(
                selectedPlan.planId(),
                code,
                name,
                description,
                price,
                durationDays,
                graceDays,
                defaultDiscountPercent,
                maxDiscountPercent,
                minimumMarginPercent,
                selectedPlan.status(),
                chkPlanAutoRenew.isSelected(),
                chkPlanRequiresApproval.isSelected(),
                selectedPlan.createdByUserId(),
                selectedPlan.updatedByUserId(),
                selectedPlan.createdAt(),
                selectedPlan.updatedAt());
    }

    private void selectPlanById(int planId) {
        for (SubscriptionPlan row : planRows) {
            if (row.planId() == planId) {
                planTable.getSelectionModel().select(row);
                planTable.scrollTo(row);
                return;
            }
        }
    }

    private void selectRuleByMedicineId(int medicineId) {
        for (SubscriptionPlanMedicineRule row : ruleRows) {
            if (row.medicineId() == medicineId) {
                ruleTable.getSelectionModel().select(row);
                ruleTable.scrollTo(row);
                return;
            }
        }
    }

    private Optional<Medicine> findMedicine(int medicineId) {
        return medicineRows.stream().filter(med -> med.getId() == medicineId).findFirst();
    }

    private String requireText(TextInputControl input, String label) {
        if (input == null || input.getText() == null || input.getText().trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return input.getText().trim();
    }

    private double parseRequiredDouble(TextField field, String label) {
        String value = requireText(field, label);
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be a valid number.");
        }
    }

    private Double parseOptionalDouble(TextField field, String label) {
        if (field == null || field.getText() == null || field.getText().trim().isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(field.getText().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be a valid number.");
        }
    }

    private int parseRequiredInt(TextField field, String label) {
        String value = requireText(field, label);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be a valid integer.");
        }
    }

    private String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.00001) {
            return String.format(Locale.US, "%.0f", value);
        }
        return String.format(Locale.US, "%.2f", value);
    }

    private boolean confirmSensitiveRuleChange(String action, String ruleContext) {
        String actionLabel = action == null ? "update" : action.trim().toLowerCase(Locale.US);
        Alert firstStep = new Alert(
                Alert.AlertType.CONFIRMATION,
                "You are about to " + actionLabel + " a subscription medicine rule.\nContext: " + ruleContext
                        + "\n\nProceed to second confirmation?",
                ButtonType.OK,
                ButtonType.CANCEL);
        firstStep.setTitle("Confirm Rule Change");
        firstStep.setHeaderText("Step 1 of 2");
        Optional<ButtonType> firstDecision = firstStep.showAndWait();
        if (firstDecision.isEmpty() || firstDecision.get() != ButtonType.OK) {
            return false;
        }

        String confirmationCode = "RULE-" + (selectedPlan == null ? "X" : selectedPlan.planId());
        TextInputDialog secondStep = new TextInputDialog();
        secondStep.setTitle("Second Confirmation");
        secondStep.setHeaderText("Step 2 of 2");
        secondStep.setContentText("Type " + confirmationCode + " to confirm:");
        Optional<String> entered = secondStep.showAndWait();
        if (entered.isEmpty() || !confirmationCode.equalsIgnoreCase(entered.get().trim())) {
            showAlert(Alert.AlertType.WARNING, "Confirmation Failed", "Rule change was not confirmed.");
            return false;
        }
        return true;
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }
}
