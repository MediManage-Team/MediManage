package org.example.MediManage.util;

import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.example.MediManage.model.User;
import org.example.MediManage.model.UserRole;
import org.example.MediManage.service.SupervisorApprovalService;

import java.util.Set;

public final class SupervisorApprovalDialogs {
    private SupervisorApprovalDialogs() {
    }

    public static SupervisorApprovalService.ApprovalResult requestApproval(
            String title,
            String actionDescription,
            String actionType,
            String entityType,
            Integer entityId,
            Set<UserRole> allowedApproverRoles) {
        SupervisorApprovalService approvalService = new SupervisorApprovalService();
        User currentUser = UserSession.getInstance().getUser();
        if (approvalService.currentUserCanBypassApproval()) {
            return new SupervisorApprovalService.ApprovalResult(
                    true,
                    0,
                    currentUser == null ? 0 : currentUser.getId(),
                    currentUser == null ? "" : currentUser.getUsername(),
                    "Approved by admin session.");
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(actionDescription);

        ButtonType approveButtonType = new ButtonType("Approve", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(approveButtonType, ButtonType.CANCEL);

        TextField usernameField = new TextField();
        usernameField.setPromptText("Supervisor username");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Supervisor password");
        TextField justificationField = new TextField();
        justificationField.setPromptText("Reason / business need");
        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Approval notes (optional)");
        notesArea.setPrefRowCount(3);

        Label errorLabel = new Label();
        errorLabel.setWrapText(true);
        errorLabel.setStyle("-fx-text-fill: #ff6b6b;");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Username"), 0, 0);
        grid.add(usernameField, 1, 0);
        grid.add(new Label("Password"), 0, 1);
        grid.add(passwordField, 1, 1);
        grid.add(new Label("Justification"), 0, 2);
        grid.add(justificationField, 1, 2);
        grid.add(new Label("Notes"), 0, 3);
        grid.add(notesArea, 1, 3);
        grid.add(errorLabel, 0, 4, 2, 1);
        dialog.getDialogPane().setContent(grid);

        final SupervisorApprovalService.ApprovalResult[] resultHolder = {
                new SupervisorApprovalService.ApprovalResult(false, 0, 0, "", "Approval cancelled.")
        };

        dialog.getDialogPane().lookupButton(approveButtonType).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                SupervisorApprovalService.ApprovalResult result = approvalService.approveAction(
                        usernameField.getText(),
                        passwordField.getText(),
                        allowedApproverRoles,
                        actionType,
                        entityType,
                        entityId,
                        justificationField.getText(),
                        notesArea.getText());
                if (!result.approved()) {
                    errorLabel.setText(result.message());
                    event.consume();
                    return;
                }
                resultHolder[0] = result;
            } catch (Exception e) {
                errorLabel.setText(e.getMessage());
                event.consume();
            }
        });

        return dialog.showAndWait()
                .filter(buttonType -> buttonType == approveButtonType)
                .map(buttonType -> resultHolder[0])
                .orElseGet(() -> new SupervisorApprovalService.ApprovalResult(false, 0, 0, "", "Approval cancelled."));
    }
}
