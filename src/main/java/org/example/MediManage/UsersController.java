package org.example.MediManage;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import org.example.MediManage.dao.UserDAO;
import org.example.MediManage.model.User;
import org.example.MediManage.model.UserRole;

import java.util.Optional;

public class UsersController {

    @FXML
    private TableView<User> userTable;
    @FXML
    private TableColumn<User, Integer> colId;
    @FXML
    private TableColumn<User, String> colUsername;
    @FXML
    private TableColumn<User, UserRole> colRole;

    @FXML
    private TextField txtUsername;
    @FXML
    private PasswordField txtPassword;
    @FXML
    private TextField txtPasswordVisible;
    @FXML
    private CheckBox chkShowPassword;
    @FXML
    private ComboBox<UserRole> cmbRole;

    @FXML
    private Button btnSave;
    @FXML
    private Button btnDelete;

    private UserDAO userDAO = new UserDAO();
    private ObservableList<User> userList = FXCollections.observableArrayList();

    // Track selected user for update vs create
    private User selectedUser = null;

    @FXML
    public void initialize() {
        // Setup Columns
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colRole.setCellValueFactory(new PropertyValueFactory<>("role"));

        // Load Roles
        cmbRole.setItems(FXCollections.observableArrayList(UserRole.values()));

        // Bind Table
        userTable.setItems(userList);

        // Bind Password Visibility
        txtPasswordVisible.visibleProperty().bind(chkShowPassword.selectedProperty());
        txtPassword.visibleProperty().bind(chkShowPassword.selectedProperty().not());
        txtPasswordVisible.textProperty().bindBidirectional(txtPassword.textProperty());

        // Selection Listener
        userTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                populateForm(newVal);
            }
        });

        loadUsers();
    }

    private void loadUsers() {
        userList.clear();
        userList.addAll(userDAO.getAllUsers());
    }

    private void populateForm(User user) {
        selectedUser = user;
        txtUsername.setText(user.getUsername());
        txtPassword.setText(user.getPassword()); // In real app, might not show password
        cmbRole.setValue(user.getRole());
        btnSave.setText("Update User");
        btnDelete.setDisable(false);
    }

    @FXML
    private void handleSave() {
        String username = txtUsername.getText();
        String password = txtPassword.getText();
        UserRole role = cmbRole.getValue();

        if (username.isEmpty() || password.isEmpty() || role == null) {
            showAlert(Alert.AlertType.ERROR, "Validation Error", "All fields are required.");
            return;
        }

        if (selectedUser == null) {
            // Create New
            User newUser = new User(0, username, password, role); // ID 0 is ignored on insert
            userDAO.addUser(newUser);
            showAlert(Alert.AlertType.INFORMATION, "Success", "User added successfully.");
        } else {
            // Update Existing (Create new object with same ID)
            User updatedUser = new User(selectedUser.getId(), username, password, role);
            userDAO.updateUser(updatedUser);
            showAlert(Alert.AlertType.INFORMATION, "Success", "User updated successfully.");
        }

        handleClear();
        loadUsers();
    }

    @FXML
    private void handleDelete() {
        if (selectedUser == null)
            return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete User");
        alert.setContentText("Are you sure you want to delete user " + selectedUser.getUsername() + "?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            userDAO.deleteUser(selectedUser.getId());
            handleClear();
            loadUsers();
        }
    }

    @FXML
    private void handleClear() {
        selectedUser = null;
        txtUsername.clear();
        txtPassword.clear();
        cmbRole.setValue(null);
        chkShowPassword.setSelected(false);
        userTable.getSelectionModel().clearSelection();
        btnSave.setText("Save User");
        btnDelete.setDisable(true);
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }
}
