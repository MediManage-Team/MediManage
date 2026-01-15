package org.example.MediManage;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class PrescriptionsController {
    @FXML
    private Label lblMessage;

    @FXML
    public void initialize() {
        lblMessage.setText("Prescription Queue Under Development.");
    }
}
