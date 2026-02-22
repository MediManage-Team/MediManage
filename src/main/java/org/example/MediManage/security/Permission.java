package org.example.MediManage.security;

public enum Permission {
    MANAGE_MEDICINES("manage medicines and stock"),
    MANAGE_USERS("manage user accounts"),
    MANAGE_SYSTEM_SETTINGS("manage system settings"),
    EXECUTE_DATABASE_MIGRATION("execute database migrations"),
    MANAGE_SUBSCRIPTION_POLICY("manage subscription plans and discount policy"),
    MANAGE_SUBSCRIPTION_ENROLLMENTS("manage subscription customer enrollments"),
    APPROVE_SUBSCRIPTION_OVERRIDES("approve subscription discount overrides");

    private final String description;

    Permission(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
