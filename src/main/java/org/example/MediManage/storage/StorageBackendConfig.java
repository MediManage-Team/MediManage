package org.example.MediManage.storage;

public final class StorageBackendConfig {
    public static final String BACKEND_PROPERTY = "medimanage.storage.backend";
    public static final String BACKEND_ENV = "MEDIMANAGE_STORAGE_BACKEND";

    private StorageBackendConfig() {
    }

    public static StorageBackend activeBackend() {
        String configured = System.getProperty(BACKEND_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(BACKEND_ENV);
        }
        return StorageBackend.from(configured);
    }
}
