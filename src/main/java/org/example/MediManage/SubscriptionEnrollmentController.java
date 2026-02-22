package org.example.MediManage;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.example.MediManage.model.Customer;
import org.example.MediManage.model.CustomerSubscription;
import org.example.MediManage.model.SubscriptionPlan;
import org.example.MediManage.model.SubscriptionPlanStatus;
import org.example.MediManage.security.Permission;
import org.example.MediManage.security.RbacPolicy;
import org.example.MediManage.service.CustomerService;
import org.example.MediManage.service.SubscriptionService;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SubscriptionEnrollmentController {
    @FXML
    private TextField txtCustomerSearch;
    @FXML
    private TableView<Customer> customerTable;
    @FXML
    private TableColumn<Customer, Number> colCustomerId;
    @FXML
    private TableColumn<Customer, String> colCustomerName;
    @FXML
    private TableColumn<Customer, String> colCustomerPhone;

    @FXML
    private TableView<SubscriptionPlan> enrollmentPlanTable;
    @FXML
    private TableColumn<SubscriptionPlan, Number> colEnrollmentPlanId;
    @FXML
    private TableColumn<SubscriptionPlan, String> colEnrollmentPlanCode;
    @FXML
    private TableColumn<SubscriptionPlan, String> colEnrollmentPlanName;
    @FXML
    private TableColumn<SubscriptionPlan, String> colEnrollmentPlanStatus;
    @FXML
    private TableColumn<SubscriptionPlan, String> colEnrollmentPlanPrice;
    @FXML
    private TableColumn<SubscriptionPlan, String> colEnrollmentPlanDiscount;

    @FXML
    private DatePicker dpEnrollmentStartDate;
    @FXML
    private TextField txtEnrollmentChannel;
    @FXML
    private TextField txtApprovalReference;
    @FXML
    private Label lblEnrollmentContext;

    @FXML
    private TableView<CustomerSubscription> enrollmentTable;
    @FXML
    private TableColumn<CustomerSubscription, Number> colEnrollmentId;
    @FXML
    private TableColumn<CustomerSubscription, String> colEnrollmentPlan;
    @FXML
    private TableColumn<CustomerSubscription, String> colEnrollmentStatus;
    @FXML
    private TableColumn<CustomerSubscription, String> colEnrollmentStart;
    @FXML
    private TableColumn<CustomerSubscription, String> colEnrollmentEnd;
    @FXML
    private TableColumn<CustomerSubscription, String> colEnrollmentGraceEnd;
    @FXML
    private TableColumn<CustomerSubscription, String> colEnrollmentChannel;

    @FXML
    private ComboBox<SubscriptionPlan> cmbTargetPlan;
    @FXML
    private TextArea txtEnrollmentActionReason;

    private final CustomerService customerService = new CustomerService();
    private final SubscriptionService subscriptionService = new SubscriptionService();

    private final ObservableList<Customer> customerRows = FXCollections.observableArrayList();
    private final ObservableList<SubscriptionPlan> planRows = FXCollections.observableArrayList();
    private final ObservableList<CustomerSubscription> enrollmentRows = FXCollections.observableArrayList();

    private FilteredList<Customer> filteredCustomers;
    private final Map<Integer, SubscriptionPlan> plansById = new LinkedHashMap<>();

    private Customer selectedCustomer;
    private SubscriptionPlan selectedEnrollmentPlan;
    private CustomerSubscription selectedEnrollment;

    @FXML
    public void initialize() {
        RbacPolicy.requireCurrentUser(Permission.MANAGE_SUBSCRIPTION_ENROLLMENTS);
        configureCustomerTable();
        configurePlanTable();
        configureEnrollmentTable();

        dpEnrollmentStartDate.setValue(LocalDate.now());
        txtEnrollmentChannel.setText("POS");

        loadPlans();
        loadCustomers();
        clearSelectionContext();
    }

    @FXML
    private void handleRefreshData() {
        loadPlans();
        loadCustomers();
        if (selectedCustomer != null) {
            loadEnrollments(selectedCustomer.getCustomerId());
        } else {
            enrollmentRows.clear();
        }
    }

    @FXML
    private void handleEnrollCustomer() {
        if (selectedCustomer == null) {
            showAlert(Alert.AlertType.WARNING, "No Customer", "Select a customer first.");
            return;
        }
        if (selectedEnrollmentPlan == null) {
            showAlert(Alert.AlertType.WARNING, "No Plan", "Select an enrollment plan.");
            return;
        }

        try {
            String channel = txtEnrollmentChannel.getText() == null || txtEnrollmentChannel.getText().trim().isEmpty()
                    ? "POS"
                    : txtEnrollmentChannel.getText().trim();
            String approvalReference = normalizeOptionalText(txtApprovalReference.getText());

            subscriptionService.enrollCustomer(
                    selectedCustomer.getCustomerId(),
                    selectedEnrollmentPlan.planId(),
                    dpEnrollmentStartDate.getValue(),
                    channel,
                    null,
                    approvalReference);
            showAlert(Alert.AlertType.INFORMATION, "Enrollment Created", "Customer enrollment created.");
            loadEnrollments(selectedCustomer.getCustomerId());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Enroll Failed", e.getMessage());
        }
    }

    @FXML
    private void handleRenewEnrollment() {
        if (selectedEnrollment == null) {
            showAlert(Alert.AlertType.WARNING, "No Enrollment", "Select an enrollment first.");
            return;
        }
        try {
            subscriptionService.renewEnrollment(
                    selectedEnrollment.enrollmentId(),
                    null,
                    normalizeOptionalText(txtApprovalReference.getText()));
            showAlert(Alert.AlertType.INFORMATION, "Enrollment Renewed", "Enrollment renewed successfully.");
            reloadSelectedCustomerEnrollments();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Renew Failed", e.getMessage());
        }
    }

    @FXML
    private void handleFreezeEnrollment() {
        if (selectedEnrollment == null) {
            showAlert(Alert.AlertType.WARNING, "No Enrollment", "Select an enrollment first.");
            return;
        }
        try {
            String reason = requireReason("Freeze reason is required.");
            subscriptionService.freezeEnrollment(
                    selectedEnrollment.enrollmentId(),
                    reason,
                    null,
                    normalizeOptionalText(txtApprovalReference.getText()));
            showAlert(Alert.AlertType.INFORMATION, "Enrollment Frozen", "Enrollment frozen.");
            reloadSelectedCustomerEnrollments();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Freeze Failed", e.getMessage());
        }
    }

    @FXML
    private void handleUnfreezeEnrollment() {
        if (selectedEnrollment == null) {
            showAlert(Alert.AlertType.WARNING, "No Enrollment", "Select an enrollment first.");
            return;
        }
        try {
            subscriptionService.unfreezeEnrollment(
                    selectedEnrollment.enrollmentId(),
                    null,
                    normalizeOptionalText(txtApprovalReference.getText()));
            showAlert(Alert.AlertType.INFORMATION, "Enrollment Unfrozen", "Enrollment reactivated.");
            reloadSelectedCustomerEnrollments();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Unfreeze Failed", e.getMessage());
        }
    }

    @FXML
    private void handleCancelEnrollment() {
        if (selectedEnrollment == null) {
            showAlert(Alert.AlertType.WARNING, "No Enrollment", "Select an enrollment first.");
            return;
        }
        try {
            String reason = requireReason("Cancellation reason is required.");
            subscriptionService.cancelEnrollment(
                    selectedEnrollment.enrollmentId(),
                    reason,
                    null,
                    normalizeOptionalText(txtApprovalReference.getText()));
            showAlert(Alert.AlertType.INFORMATION, "Enrollment Cancelled", "Enrollment cancelled.");
            reloadSelectedCustomerEnrollments();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Cancel Failed", e.getMessage());
        }
    }

    @FXML
    private void handleChangeEnrollmentPlan() {
        if (selectedEnrollment == null) {
            showAlert(Alert.AlertType.WARNING, "No Enrollment", "Select an enrollment first.");
            return;
        }
        SubscriptionPlan targetPlan = cmbTargetPlan.getValue();
        if (targetPlan == null) {
            showAlert(Alert.AlertType.WARNING, "No Target Plan", "Select a target plan.");
            return;
        }

        try {
            String note = normalizeOptionalText(txtEnrollmentActionReason.getText());
            subscriptionService.changeEnrollmentPlan(
                    selectedEnrollment.enrollmentId(),
                    targetPlan.planId(),
                    note == null ? "Enrollment plan changed via workflow screen." : note,
                    null,
                    normalizeOptionalText(txtApprovalReference.getText()));
            showAlert(Alert.AlertType.INFORMATION, "Plan Changed", "Enrollment plan changed.");
            reloadSelectedCustomerEnrollments();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Plan Change Failed", e.getMessage());
        }
    }

    @FXML
    private void handleClearActionNote() {
        txtEnrollmentActionReason.clear();
    }

    private void configureCustomerTable() {
        colCustomerId.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().getCustomerId()));
        colCustomerName.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getName()));
        colCustomerPhone.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getPhoneNumber()));

        filteredCustomers = new FilteredList<>(customerRows, c -> true);
        customerTable.setItems(filteredCustomers);
        txtCustomerSearch.textProperty().addListener((obs, oldValue, newValue) -> {
            filteredCustomers.setPredicate(customer -> customerService.matchesSearch(customer, newValue));
        });
        customerTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            selectedCustomer = newValue;
            clearEnrollmentSelection();
            if (newValue == null) {
                enrollmentRows.clear();
                clearSelectionContext();
                return;
            }
            updateSelectionContext(newValue);
            loadEnrollments(newValue.getCustomerId());
        });
    }

    private void configurePlanTable() {
        colEnrollmentPlanId.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().planId()));
        colEnrollmentPlanCode.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().planCode()));
        colEnrollmentPlanName.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().planName()));
        colEnrollmentPlanStatus.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().status().name()));
        colEnrollmentPlanPrice.setCellValueFactory(cd -> new SimpleStringProperty(formatAmount(cd.getValue().price())));
        colEnrollmentPlanDiscount.setCellValueFactory(
                cd -> new SimpleStringProperty(formatPercent(cd.getValue().defaultDiscountPercent())));
        enrollmentPlanTable.setItems(planRows);
        enrollmentPlanTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            selectedEnrollmentPlan = newValue;
        });
    }

    private void configureEnrollmentTable() {
        colEnrollmentId.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().enrollmentId()));
        colEnrollmentPlan.setCellValueFactory(cd -> new SimpleStringProperty(planName(cd.getValue().planId())));
        colEnrollmentStatus.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().status().name()));
        colEnrollmentStart.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().startDate()));
        colEnrollmentEnd.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().endDate()));
        colEnrollmentGraceEnd
                .setCellValueFactory(cd -> new SimpleStringProperty(emptyIfNull(cd.getValue().graceEndDate())));
        colEnrollmentChannel
                .setCellValueFactory(cd -> new SimpleStringProperty(emptyIfNull(cd.getValue().enrollmentChannel())));
        enrollmentTable.setItems(enrollmentRows);
        enrollmentTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            selectedEnrollment = newValue;
            if (newValue != null) {
                Optional<SubscriptionPlan> plan = Optional.ofNullable(plansById.get(newValue.planId()));
                cmbTargetPlan.setValue(plan.orElse(null));
            }
        });
    }

    private void loadCustomers() {
        List<Customer> customers = customerService.getAllCustomers();
        customerRows.setAll(customers);
        if (selectedCustomer != null) {
            selectCustomerById(selectedCustomer.getCustomerId());
        }
    }

    private void loadPlans() {
        List<SubscriptionPlan> plans = subscriptionService.getPlans();
        plansById.clear();
        for (SubscriptionPlan plan : plans) {
            plansById.put(plan.planId(), plan);
        }
        planRows.setAll(plans.stream()
                .filter(plan -> plan.status() == SubscriptionPlanStatus.ACTIVE
                        || plan.status() == SubscriptionPlanStatus.DRAFT)
                .toList());
        cmbTargetPlan.setItems(FXCollections.observableArrayList(planRows));
        cmbTargetPlan.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(SubscriptionPlan item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.planName() + " (" + item.planCode() + ")");
            }
        });
        cmbTargetPlan.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(SubscriptionPlan item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.planName() + " (" + item.planCode() + ")");
            }
        });
        enrollmentTable.refresh();
    }

    private void loadEnrollments(int customerId) {
        List<CustomerSubscription> enrollments = subscriptionService.getCustomerEnrollments(customerId);
        enrollmentRows.setAll(enrollments);
        if (selectedEnrollment != null) {
            selectEnrollmentById(selectedEnrollment.enrollmentId());
        }
    }

    private void reloadSelectedCustomerEnrollments() {
        if (selectedCustomer != null) {
            loadEnrollments(selectedCustomer.getCustomerId());
        }
    }

    private void selectCustomerById(int customerId) {
        for (Customer customer : customerRows) {
            if (customer.getCustomerId() == customerId) {
                customerTable.getSelectionModel().select(customer);
                customerTable.scrollTo(customer);
                return;
            }
        }
    }

    private void selectEnrollmentById(int enrollmentId) {
        for (CustomerSubscription enrollment : enrollmentRows) {
            if (enrollment.enrollmentId() == enrollmentId) {
                enrollmentTable.getSelectionModel().select(enrollment);
                enrollmentTable.scrollTo(enrollment);
                return;
            }
        }
    }

    private void clearEnrollmentSelection() {
        selectedEnrollment = null;
        enrollmentTable.getSelectionModel().clearSelection();
    }

    private void clearSelectionContext() {
        lblEnrollmentContext.setText("Select a customer to view and manage subscriptions.");
    }

    private void updateSelectionContext(Customer customer) {
        lblEnrollmentContext.setText("Customer: " + customer.getName() + " (ID " + customer.getCustomerId() + ")");
    }

    private String planName(int planId) {
        SubscriptionPlan plan = plansById.get(planId);
        if (plan == null) {
            return "Plan #" + planId;
        }
        return plan.planName() + " (" + plan.planCode() + ")";
    }

    private String requireReason(String message) {
        if (txtEnrollmentActionReason.getText() == null || txtEnrollmentActionReason.getText().trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return txtEnrollmentActionReason.getText().trim();
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private String formatAmount(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    private String formatPercent(double value) {
        return String.format(java.util.Locale.US, "%.2f%%", value);
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }
}
