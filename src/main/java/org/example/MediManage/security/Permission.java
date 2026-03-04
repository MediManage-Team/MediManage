package org.example.MediManage.security;

public enum Permission {
    MANAGE_MEDICINES("manage medicines and stock"),
    MANAGE_USERS("manage user accounts"),
    MANAGE_SYSTEM_SETTINGS("manage system settings"),
    EXECUTE_DATABASE_MIGRATION("execute database migrations");

    private final String description;

    Permission(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
