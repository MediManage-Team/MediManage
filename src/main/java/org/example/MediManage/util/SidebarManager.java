package org.example.MediManage.util;

import javafx.scene.control.Button;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.example.MediManage.config.FeatureFlag;
import org.example.MediManage.config.FeatureFlags;
import org.example.MediManage.model.UserRole;

public class SidebarManager {

    public static void generateSidebar(VBox container, UserRole role, Runnable onLogout, ViewSwitcher switcher) {
        container.getChildren().clear();
        container.setSpacing(4);

        // Add Common Dashboard Button (Admin, Manager, and Pharmacist)
        if (role == UserRole.ADMIN || role == UserRole.MANAGER || role == UserRole.PHARMACIST) {
            addButton(container, "\ud83d\udcca Dashboard", "dashboard-view", switcher);
        }

        // Add Role-Specific Buttons
        if (role != null) {
            switch (role) {
                case ADMIN:
                    addButton(container, "\ud83d\udc64 Users", "users-view", switcher);
                    addButton(container, "\ud83d\udce6 Inventory", "inventory-view", switcher);
                    addButton(container, "\ud83c\udfed Suppliers", "supplier-view", switcher);
                    addButton(container, "\ud83d\udce6 Purchases", "purchases-view", switcher);
                    addButton(container, "\ud83d\udcb8 Expenses", "expenses-view", switcher);
                    addButton(container, "\ud83d\udcc8 Reports", "reports-view", switcher);
                    addButton(container, "\ud83d\udd50 Attendance", "attendance-view", switcher);
                    addButton(container, "\ud83d\udcb3 Billing", "billing-view", switcher);
                    addButton(container, "\ud83d\udc65 Customers", "customers-view", switcher);
                    addButton(container, "\u2699 Settings", "settings-view", switcher);
                    break;
                case MANAGER:
                    addButton(container, "\ud83d\udce6 Inventory", "inventory-view", switcher);
                    addButton(container, "\ud83c\udfed Suppliers", "supplier-view", switcher);
                    addButton(container, "\ud83d\udce6 Purchases", "purchases-view", switcher);
                    addButton(container, "\ud83d\udcb8 Expenses", "expenses-view", switcher);
                    addButton(container, "\ud83d\udcc8 Reports", "reports-view", switcher);
                    addButton(container, "\ud83d\udd50 Attendance", "attendance-view", switcher);
                    addButton(container, "\u2699 Settings", "settings-view", switcher);
                    break;
                case PHARMACIST:
                    addButton(container, "\ud83d\udd0d Medicine Search", "medicine-search-view", switcher);
                    addButton(container, "\ud83d\udccb Prescriptions", "prescriptions-view", switcher);
                    break;
                case CASHIER:
                    addButton(container, "\ud83d\udcb3 Billing", "billing-view", switcher);
                    addButton(container, "\ud83d\udc65 Customers", "customers-view", switcher);
                    break;
                case STAFF:
                    addButton(container, "\ud83d\udcb3 Billing", "billing-view", switcher);
                    addButton(container, "\ud83d\udd0d Medicine Search", "medicine-search-view", switcher);
                    break;
            }
        }

        // AI Assistant — controlled by feature flag for staged rollouts
        if (FeatureFlags.isEnabled(FeatureFlag.AI_ASSISTANT)) {
            addButton(container, "\ud83e\udd16 AI Assistant", "ai-view", switcher);
        }

        // Spacer to push Logout to bottom
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        container.getChildren().add(spacer);

        // Logout Button at bottom
        Button logoutBtn = new Button("\u23fb Logout");
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.setOnAction(e -> onLogout.run());
        logoutBtn.getStyleClass().add("sidebar-button-logout");
        container.getChildren().add(logoutBtn);
    }

    private static void addButton(VBox container, String text, String viewName, ViewSwitcher switcher) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(e -> switcher.switchView(viewName));
        btn.getStyleClass().add("sidebar-button");
        container.getChildren().add(btn);
    }
}
