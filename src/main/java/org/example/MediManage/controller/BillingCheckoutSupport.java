package org.example.MediManage.controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.example.MediManage.model.Customer;
import org.example.MediManage.model.PaymentSplit;
import org.example.MediManage.service.BillingService;
import org.example.MediManage.service.EmailService;
import org.example.MediManage.service.WhatsAppService;

import java.util.ArrayList;
import java.util.List;

final class BillingCheckoutSupport {
    private static final double PAYMENT_TOLERANCE = 0.01;

    @FunctionalInterface
    interface AlertHandler {
        void show(Alert.AlertType type, String title, String content);
    }

    private final AlertHandler alertHandler;

    BillingCheckoutSupport(AlertHandler alertHandler) {
        this.alertHandler = alertHandler;
    }

    List<PaymentSplit> showSplitPaymentDialog(double totalAmount) {
        Dialog<List<PaymentSplit>> dialog = new Dialog<>();
        dialog.setTitle("Split Payment");
        dialog.setHeaderText(String.format("Total: Rs. %.2f - Split across payment methods", totalAmount));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(500);

        VBox container = new VBox(10);
        container.setPadding(new Insets(10));

        ObservableList<PaymentSplit> splits = FXCollections.observableArrayList();
        splits.add(new PaymentSplit("Cash", totalAmount));

        ListView<PaymentSplit> listView = new ListView<>(splits);
        listView.setPrefHeight(150);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(PaymentSplit item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
            }
        });

        ComboBox<String> methodBox = new ComboBox<>(FXCollections.observableArrayList(
                "Cash", "UPI", "Card", "Credit", "Cheque", "Other"));
        methodBox.setValue("Cash");
        TextField amountField = new TextField();
        amountField.setPromptText("Amount");
        TextField refField = new TextField();
        refField.setPromptText("Ref# (optional)");

        Button addBtn = new Button("Add Split");
        addBtn.setOnAction(e -> {
            try {
                double amt = Double.parseDouble(amountField.getText());
                if (amt <= 0) {
                    return;
                }
                splits.add(new PaymentSplit(methodBox.getValue(), amt, refField.getText()));
                amountField.clear();
                refField.clear();
            } catch (NumberFormatException ignored) {
            }
        });

        Button removeBtn = new Button("Remove Selected");
        removeBtn.setOnAction(e -> {
            PaymentSplit sel = listView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                splits.remove(sel);
            }
        });

        Label remainingLabel = new Label();
        Runnable updateRemaining = () -> {
            double paid = splits.stream().mapToDouble(PaymentSplit::getAmount).sum();
            double remaining = totalAmount - paid;
            remainingLabel.setText(String.format("Remaining: Rs. %.2f", remaining));
            remainingLabel.setStyle(remaining > PAYMENT_TOLERANCE
                    ? "-fx-text-fill: #ff6b6b;"
                    : "-fx-text-fill: #5fe6b3;");
        };
        splits.addListener((javafx.collections.ListChangeListener<PaymentSplit>) c -> updateRemaining.run());
        updateRemaining.run();

        HBox addRow = new HBox(8, methodBox, amountField, refField, addBtn, removeBtn);
        addRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        container.getChildren().addAll(listView, addRow, remainingLabel);
        dialog.getDialogPane().setContent(container);

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                if (!hasMatchingTotal(splits, totalAmount)) {
                    double paid = splits.stream().mapToDouble(PaymentSplit::getAmount).sum();
                    alertHandler.show(
                            Alert.AlertType.WARNING,
                            "Amount Mismatch",
                            String.format("Total splits (Rs. %.2f) must equal bill total (Rs. %.2f)", paid,
                                    totalAmount));
                    return null;
                }
                return new ArrayList<>(splits);
            }
            return null;
        });

        java.util.Optional<List<PaymentSplit>> result = dialog.showAndWait();
        return result.orElse(null);
    }

    void showPostCheckoutDialog(
            BillingService.CheckoutResult result,
            Customer customer,
            double totalAmount,
            String careProtocol) {
        Alert dialog = new Alert(result.pdfAvailable() ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
        dialog.setTitle(result.pdfAvailable() ? "Checkout Successful" : "Checkout Completed With Warning");
        if (result.pdfAvailable()) {
            dialog.setHeaderText("Bill #" + result.billId() + " Generated successfully!\nSaved to: " + result.pdfPath());
        } else {
            dialog.setHeaderText("Bill #" + result.billId() + " was saved, but the invoice PDF could not be generated.\n"
                    + "You can regenerate it later from Dashboard history.");
            dialog.setContentText("PDF error: " + result.pdfErrorMessage());
        }

        ButtonType btnClose = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        if (!result.pdfAvailable()) {
            dialog.getButtonTypes().setAll(btnClose);
            dialog.showAndWait();
            return;
        }

        ButtonType btnBoth = new ButtonType("Send Both");
        ButtonType btnEmail = new ButtonType("Email Invoice");
        ButtonType btnWhatsApp = new ButtonType("WhatsApp Invoice");
        dialog.getButtonTypes().setAll(btnBoth, btnEmail, btnWhatsApp, btnClose);

        java.util.Optional<ButtonType> choice = dialog.showAndWait();
        if (choice.isPresent()) {
            if (choice.get() == btnBoth) {
                handleSendEmail(result, customer, totalAmount, careProtocol);
                handleSendWhatsApp(result, customer, totalAmount, careProtocol);
            } else if (choice.get() == btnEmail) {
                handleSendEmail(result, customer, totalAmount, careProtocol);
            } else if (choice.get() == btnWhatsApp) {
                handleSendWhatsApp(result, customer, totalAmount, careProtocol);
            }
        }
    }

    static String compositePaymentMode(List<PaymentSplit> splits) {
        if (splits == null || splits.isEmpty()) {
            return "Cash";
        }
        if (splits.size() == 1) {
            return splits.get(0).getPaymentMethod().toUpperCase();
        }
        return splits.stream()
                .map(PaymentSplit::getPaymentMethod)
                .distinct()
                .reduce((a, b) -> a + "+" + b)
                .orElse("Cash");
    }

    static boolean hasMatchingTotal(List<PaymentSplit> splits, double totalAmount) {
        if (splits == null || splits.isEmpty()) {
            return false;
        }
        double paid = splits.stream().mapToDouble(PaymentSplit::getAmount).sum();
        return Math.abs(paid - totalAmount) <= PAYMENT_TOLERANCE;
    }

    private void handleSendEmail(
            BillingService.CheckoutResult result,
            Customer customer,
            double totalAmount,
            String careProtocol) {
        if (!result.pdfAvailable()) {
            alertHandler.show(Alert.AlertType.ERROR, "Email Unavailable", "Invoice PDF is not available for this checkout.");
            return;
        }
        TextInputDialog emailDialog = new TextInputDialog(customer != null ? customer.getEmail() : "");
        emailDialog.setTitle("Send Email");
        emailDialog.setHeaderText("Enter Customer Email Address");
        java.util.Optional<String> emailResult = emailDialog.showAndWait();

        if (emailResult.isPresent() && !emailResult.get().trim().isEmpty()) {
            String toEmail = emailResult.get().trim();
            String name = customer != null ? customer.getName() : "Customer";

            org.example.MediManage.util.ToastNotification.info("Sending Email to " + toEmail + "...");

            EmailService.sendInvoiceEmail(toEmail, name, careProtocol, result.pdfPath(), result.billId(), totalAmount)
                    .thenAccept(success -> Platform.runLater(
                            () -> org.example.MediManage.util.ToastNotification.success("Email sent successfully!")))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> alertHandler.show(Alert.AlertType.ERROR, "Email Failed", ex.getMessage()));
                        return null;
                    });
        }
    }

    private void handleSendWhatsApp(
            BillingService.CheckoutResult result,
            Customer customer,
            double totalAmount,
            String careProtocol) {
        if (!result.pdfAvailable()) {
            alertHandler.show(Alert.AlertType.ERROR, "WhatsApp Unavailable",
                    "Invoice PDF is not available for this checkout.");
            return;
        }
        TextInputDialog phoneDialog = new TextInputDialog(customer != null ? customer.getPhone() : "");
        phoneDialog.setTitle("Send WhatsApp");
        phoneDialog.setHeaderText("Enter Customer WhatsApp Number (+CCxxxxxxxxxx)");

        TextField phoneField = phoneDialog.getEditor();
        phoneField.setTextFormatter(new TextFormatter<>(change -> {
            if (change.getControlNewText().length() > 15) {
                return null;
            }
            if (change.getText().matches("[^0-9+]")) {
                return null;
            }
            return change;
        }));

        java.util.Optional<String> phoneResult = phoneDialog.showAndWait();

        if (phoneResult.isPresent() && !phoneResult.get().trim().isEmpty()) {
            String toPhone = phoneResult.get().trim();
            String name = customer != null ? customer.getName() : "Customer";

            org.example.MediManage.util.ToastNotification.info("Sending WhatsApp to " + toPhone + "...");

            WhatsAppService.sendInvoiceWhatsApp(
                    toPhone,
                    name,
                    totalAmount,
                    careProtocol,
                    result.billId(),
                    result.pdfPath())
                    .thenAccept(success -> Platform.runLater(() -> org.example.MediManage.util.ToastNotification
                            .success("WhatsApp message sent successfully!")))
                    .exceptionally(ex -> {
                        Platform.runLater(
                                () -> alertHandler.show(Alert.AlertType.ERROR, "WhatsApp Failed", ex.getMessage()));
                        return null;
                    });
        }
    }
}
