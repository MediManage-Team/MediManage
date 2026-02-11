package org.example.MediManage;

import javafx.fxml.FXML;

public class SettingsController {

    @FXML
    private javafx.scene.control.PasswordField txtApiKey;
    @FXML
    private javafx.scene.control.Label lblStatus;

    private static final String PREF_KEY = "gemini_api_key";
    private java.util.prefs.Preferences prefs = java.util.prefs.Preferences
            .userNodeForPackage(SettingsController.class);

    @FXML
    public void initialize() {
        String currentKey = prefs.get(PREF_KEY, "");
        txtApiKey.setText(currentKey);
    }

    @FXML
    private void handleSave() {
        String key = txtApiKey.getText().trim();
        if (key.isEmpty()) {
            lblStatus.setText("Key cannot be empty.");
            lblStatus.setStyle("-fx-text-fill: red;");
            return;
        }
        prefs.put(PREF_KEY, key);
        lblStatus.setText("API Key Saved Successfully!");
        lblStatus.setStyle("-fx-text-fill: green;");
    }

    public static String getApiKey() {
        return java.util.prefs.Preferences.userNodeForPackage(SettingsController.class).get(PREF_KEY, "");
    }
}
