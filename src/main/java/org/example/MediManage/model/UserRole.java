package org.example.MediManage.model;

public enum UserRole {
    ADMIN,
    MANAGER,
    PHARMACIST,
    CASHIER,
    STAFF;

    public static UserRole fromString(String role) {
        if (role == null) {
            return null;
        }
        // Handle Legacy Role Migration

        try {
            return UserRole.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            System.err.println("Unknown role: " + role);
            return null;
        }
    }
}
