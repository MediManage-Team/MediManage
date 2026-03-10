package org.example.MediManage.controller;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.example.MediManage.model.BillItem;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Controller for a second-screen customer-facing display.
 * Shows the current bill items and running total.
 */
public class CustomerDisplayController {

    @FXML
    private Label lblPharmacyName;
    @FXML
    private Label lblWelcome;
    @FXML
    private Label lblTotal;
    @FXML
    private Label lblFooter;

    @FXML
    private TableView<BillItem> itemsTable;
    @FXML
    private TableColumn<BillItem, String> colItem;
    @FXML
    private TableColumn<BillItem, Number> colQty;
    @FXML
    private TableColumn<BillItem, Number> colPrice;
    @FXML
    private TableColumn<BillItem, Number> colTotal;

    private final ObservableList<BillItem> displayItems = FXCollections.observableArrayList();
    private static CustomerDisplayController activeInstance;

    @FXML
    public void initialize() {
        colItem.setCellValueFactory(cd -> cd.getValue().nameProperty());
        colQty.setCellValueFactory(cd -> cd.getValue().qtyProperty());
        colPrice.setCellValueFactory(cd -> cd.getValue().priceProperty());
        colTotal.setCellValueFactory(cd -> cd.getValue().totalProperty());

        itemsTable.setItems(displayItems);
        activeInstance = this;
    }

    /**
     * Update the displayed items and total from the billing controller.
     */
    public void updateDisplay(List<BillItem> items, double total) {
        displayItems.setAll(items);
        lblTotal.setText(String.format("₹ %.2f", total));
    }

    /**
     * Set custom footer text.
     */
    public void setFooterText(String text) {
        if (lblFooter != null && text != null) {
            lblFooter.setText(text);
        }
    }

    /**
     * Set pharmacy name.
     */
    public void setPharmacyName(String name) {
        if (lblPharmacyName != null && name != null) {
            lblPharmacyName.setText(name);
        }
    }

    /**
     * Clear the display (after checkout).
     */
    public void clearDisplay() {
        displayItems.clear();
        lblTotal.setText("₹ 0.00");
        lblWelcome.setText("Thank you! Please come again.");
    }

    /**
     * Get the currently active instance (if the window is open).
     */
    public static CustomerDisplayController getActiveInstance() {
        return activeInstance;
    }

    /**
     * Open the customer-facing display on the second screen (if available),
     * or as a separate window on the primary screen.
     */
    public static Stage openDisplay() throws IOException {
        FXMLLoader loader = new FXMLLoader(
                CustomerDisplayController.class.getResource("/org/example/MediManage/customer-display-view.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 800, 600);
        try {
            scene.getStylesheets().add(
                    Objects.requireNonNull(
                            CustomerDisplayController.class.getResource("/org/example/MediManage/css/common.css")).toExternalForm());
        } catch (Exception ignored) {
        }

        Stage displayStage = new Stage();
        displayStage.setTitle("MediManage - Customer Display");
        displayStage.setScene(scene);
        displayStage.initStyle(StageStyle.UNDECORATED);

        // Position on second monitor if available
        List<Screen> screens = Screen.getScreens();
        if (screens.size() > 1) {
            Screen secondScreen = screens.get(1);
            var bounds = secondScreen.getVisualBounds();
            displayStage.setX(bounds.getMinX());
            displayStage.setY(bounds.getMinY());
            displayStage.setWidth(bounds.getWidth());
            displayStage.setHeight(bounds.getHeight());
            displayStage.setFullScreen(true);
        }

        displayStage.setOnCloseRequest(e -> activeInstance = null);
        displayStage.show();
        return displayStage;
    }
}
