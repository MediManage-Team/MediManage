package org.example.MediManage.storage;

public enum StorageBackend {
    SQLITE,
    INMEMORY;

    public static StorageBackend from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return SQLITE;
        }
        String normalized = rawValue.trim().toLowerCase();
        return switch (normalized) {
            case "inmemory", "memory", "mem" -> INMEMORY;
            case "sqlite", "local" -> SQLITE;
            default -> SQLITE;
        };
    }
}
