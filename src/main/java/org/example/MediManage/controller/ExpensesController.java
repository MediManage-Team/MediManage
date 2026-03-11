package org.example.MediManage.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.example.MediManage.dao.ExpenseDAO;
import org.example.MediManage.model.Expense;
import org.example.MediManage.service.DashboardKpiService;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javafx.application.Platform;
import org.example.MediManage.util.AppExecutors;

public class ExpensesController {

    @FXML private Label lblMonthlyTotal;

    @FXML private DatePicker datePicker;
    @FXML private ComboBox<String> comboCategory;
    @FXML private TextField txtAmount;
    @FXML private TextArea txtDescription;

    @FXML private TableView<Expense> expenseTable;
    @FXML private TableColumn<Expense, Number> colId;
    @FXML private TableColumn<Expense, String> colDate;
    @FXML private TableColumn<Expense, String> colCategory;
    @FXML private TableColumn<Expense, Number> colAmount;
    @FXML private TableColumn<Expense, String> colDesc;

    private final ExpenseDAO expenseDAO = new ExpenseDAO();
    private final ObservableList<Expense> expensesList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTable();
        
        datePicker.setValue(LocalDate.now());
        
        // Common expense categories
        comboCategory.setItems(FXCollections.observableArrayList(
            "Rent", "Salary", "Electricity", "Water", "Office Supplies", "Marketing", "Maintenance", "Other"
        ));

        loadExpenses();
    }

    private void setupTable() {
        colId.setCellValueFactory(d -> d.getValue().idProperty());
        colDate.setCellValueFactory(d -> d.getValue().dateProperty());
        colCategory.setCellValueFactory(d -> d.getValue().categoryProperty());
        colAmount.setCellValueFactory(d -> d.getValue().amountProperty());
        colDesc.setCellValueFactory(d -> d.getValue().descriptionProperty());

        expenseTable.setItems(expensesList);
    }

    @FXML
    private void loadExpenses() {
        AppExecutors.runBackground(() -> {
            try {
                List<Expense> allExpenses = expenseDAO.getAllExpenses();
                Platform.runLater(() -> {
                    expensesList.setAll(allExpenses);
                    calculateMonthlyTotal(allExpenses);
                });
            } catch (SQLException e) {
                e.printStackTrace();
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Error", "Failed to load expenses."));
            }
        });
    }

    private void calculateMonthlyTotal(List<Expense> allExpenses) {
        String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        
        double total = allExpenses.stream()
                .filter(e -> e.getDate() != null && e.getDate().startsWith(currentMonth))
                .mapToDouble(Expense::getAmount)
                .sum();
                
        lblMonthlyTotal.setText(String.format(Locale.getDefault(), "₹ %,.2f", total));
    }

    @FXML
    private void handleAddExpense() {
        LocalDate date = datePicker.getValue();
        String category = comboCategory.getValue() != null ? comboCategory.getValue() : comboCategory.getEditor().getText();
        String amountStr = txtAmount.getText();
        String desc = txtDescription.getText();

        if (date == null || category.trim().isEmpty() || amountStr.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Validation", "Please fill in all required fields (Date, Category, Amount).");
            return;
        }

        try {
            double amount = Double.parseDouble(amountStr);
            if (amount <= 0) {
                showAlert(Alert.AlertType.WARNING, "Validation", "Amount must be greater than zero.");
                return;
            }

            expenseDAO.addExpense(category.trim(), amount, date.toString(), desc.trim());
            DashboardKpiService.invalidateExpenseMetrics(); // Update dashboard cache
            
            // Clear form
            txtAmount.clear();
            txtDescription.clear();
            comboCategory.getEditor().clear();
            datePicker.setValue(LocalDate.now());
            
            // Refresh
            loadExpenses();
            showAlert(Alert.AlertType.INFORMATION, "Success", "Expense recorded successfully.");

        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Invalid Input", "Please enter a valid amount.");
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to save expense.");
        }
    }

    @FXML
    private void handleDeleteExpense() {
        Expense selected = expenseTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Validation", "Please select an expense to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete this expense record?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    expenseDAO.deleteExpense(selected.getId());
                    DashboardKpiService.invalidateExpenseMetrics();
                    loadExpenses();
                } catch (SQLException e) {
                    e.printStackTrace();
                    showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to delete expense.");
                }
            }
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
