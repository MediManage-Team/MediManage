package org.example.MediManage.util;

import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import org.example.MediManage.model.UserRole;

public class SidebarManager {

    public static void generateSidebar(VBox container, UserRole role, Runnable onLogout, ViewSwitcher switcher) {
        container.getChildren().clear();
        container.setSpacing(10);

        // Add Common Dashboard Button (Only for Admin/Manager)
        if (role == UserRole.ADMIN || role == UserRole.MANAGER) {
            addButton(container, "Stats Dashboard", "dashboard-view", switcher);
        }

        // Add Role-Specific Buttons
        if (role != null) {
            switch (role) {
                case ADMIN:
                    addButton(container, "Users", "users-view", switcher);
                    addButton(container, "Inventory", "inventory-view", switcher);
                    addButton(container, "Reports", "reports-view", switcher);
                    addButton(container, "Settings", "settings-view", switcher);
                    break;
                case MANAGER:
                    addButton(container, "Inventory", "inventory-view", switcher);
                    addButton(container, "Reports", "reports-view", switcher);
                    break;
                case PHARMACIST:
                    addButton(container, "Medicine Search", "medicine-search-view", switcher);
                    addButton(container, "Prescriptions", "prescriptions-view", switcher);
                    break;
                case CASHIER:
                    addButton(container, "Billing", "billing-view", switcher);
                    addButton(container, "Customers", "customers-view", switcher);
                    break;
            }
        }

        // Add Spacer (using Region or just rely on VBox layout)
        // For simplicity, just adding Logout at the bottom effectively if container is
        // set up securely,
        // but here we just append it. To push it to bottom, the VBox in FXML should
        // handle alignment or spacer.

        // Add Logout Button
        Button logoutBtn = new Button("Logout");
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.setOnAction(e -> onLogout.run());
        logoutBtn.getStyleClass().add("sidebar-button-logout"); // Assuming CSS class exists or for future
        container.getChildren().add(logoutBtn);
    }

    private static void addButton(VBox container, String text, String viewName, ViewSwitcher switcher) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(e -> switcher.switchView(viewName));
        btn.getStyleClass().add("sidebar-button"); // Assuming CSS class exists
        container.getChildren().add(btn);
    }
}
