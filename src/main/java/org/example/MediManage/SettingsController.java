package org.example.MediManage;

import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.scene.control.Alert;

import java.io.File;
import java.util.prefs.Preferences;

public class SettingsController {

    @FXML
    private TextField modelPathField;
    @FXML
    private ComboBox<String> hardwareCombo;
    @FXML
    private PasswordField apiKeyField;

    private Preferences prefs;

    @FXML
    public void initialize() {
        prefs = Preferences.userNodeForPackage(SettingsController.class);

        // Load saved settings
        modelPathField.setText(prefs.get("local_model_path", ""));
        apiKeyField.setText(prefs.get("cloud_api_key", ""));

        hardwareCombo.getItems().addAll("Auto", "OpenVINO (Beta)", "Ryzen AI / DirectML", "CUDA (NVIDIA)", "CPU");
        hardwareCombo.setValue(prefs.get("ai_hardware", "Auto"));
    }

    @FXML
    private void handleBrowseModel() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Local AI Model");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("AI Models", "*.onnx", "*.xml"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        File selectedFile = fileChooser.showOpenDialog(modelPathField.getScene().getWindow());
        if (selectedFile != null) {
            modelPathField.setText(selectedFile.getAbsolutePath());
        }
    }

    @FXML
    private void handleSaveSettings() {
        prefs.put("local_model_path", modelPathField.getText());
        prefs.put("ai_hardware", hardwareCombo.getValue());
        prefs.put("cloud_api_key", apiKeyField.getText());

        showAlert(Alert.AlertType.INFORMATION, "Settings Saved", "AI Configuration has been saved successfully.");

        // Trigger reload of AI Engine
        new org.example.MediManage.service.ai.LocalAIService().loadModel();
    }

    @FXML
    private void handleOpenModelStore() {
        try {
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/org/example/MediManage/model-store-view.fxml"));
            javafx.scene.Parent root = loader.load();
            javafx.stage.Stage stage = new javafx.stage.Stage();
            stage.setTitle("AI Model Store");
            stage.setScene(new javafx.scene.Scene(root));
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Could not open Model Store: " + e.getMessage());
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
