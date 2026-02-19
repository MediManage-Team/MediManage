package org.example.MediManage.util;

import javafx.scene.control.Button;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.example.MediManage.model.UserRole;

public class SidebarManager {

    public static void generateSidebar(VBox container, UserRole role, Runnable onLogout, ViewSwitcher switcher) {
        container.getChildren().clear();
        container.setSpacing(4);

        // Add Common Dashboard Button (Admin, Manager, and Pharmacist)
        if (role == UserRole.ADMIN || role == UserRole.MANAGER || role == UserRole.PHARMACIST) {
            addButton(container, "📊 Stats Dashboard", "dashboard-view", switcher);
        }

        // Add Role-Specific Buttons
        if (role != null) {
            switch (role) {
                case ADMIN:
                    addButton(container, "👤 Users", "users-view", switcher);
                    addButton(container, "📦 Inventory", "inventory-view", switcher);
                    addButton(container, "📈 Reports", "reports-view", switcher);
                    addButton(container, "⚙ Settings", "settings-view", switcher);
                    break;
                case MANAGER:
                    addButton(container, "📦 Inventory", "inventory-view", switcher);
                    addButton(container, "📈 Reports", "reports-view", switcher);
                    addButton(container, "⚙ Settings", "settings-view", switcher);
                    break;
                case PHARMACIST:
                    addButton(container, "🔍 Medicine Search", "medicine-search-view", switcher);
                    addButton(container, "📋 Prescriptions", "prescriptions-view", switcher);
                    break;
                case CASHIER:
                    addButton(container, "💳 Billing", "billing-view", switcher);
                    addButton(container, "👥 Customers", "customers-view", switcher);
                    break;
                case STAFF:
                    addButton(container, "💳 Billing", "billing-view", switcher);
                    addButton(container, "🔍 Medicine Search", "medicine-search-view", switcher);
                    break;
            }
        }

        // AI Assistant — available to ALL roles
        addButton(container, "🤖 AI Assistant", "ai-view", switcher);

        // Spacer to push Logout to bottom
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        container.getChildren().add(spacer);

        // Logout Button at bottom
        Button logoutBtn = new Button("⏻ Logout");
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.setOnAction(e -> onLogout.run());
        logoutBtn.getStyleClass().add("sidebar-button-logout");
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
