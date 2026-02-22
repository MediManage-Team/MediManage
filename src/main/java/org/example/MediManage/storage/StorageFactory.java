package org.example.MediManage.storage;

import org.example.MediManage.storage.customer.CustomerStore;
import org.example.MediManage.storage.customer.SqliteCustomerStore;
import org.example.MediManage.storage.inmemory.InMemoryStorageRegistry;
import org.example.MediManage.storage.inventory.MedicineStore;
import org.example.MediManage.storage.inventory.SqliteMedicineStore;

public final class StorageFactory {
    private StorageFactory() {
    }

    public static MedicineStore medicineStore() {
        return switch (StorageBackendConfig.activeBackend()) {
            case INMEMORY -> InMemoryStorageRegistry.medicineStore();
            case SQLITE -> new SqliteMedicineStore();
        };
    }

    public static CustomerStore customerStore() {
        return switch (StorageBackendConfig.activeBackend()) {
            case INMEMORY -> InMemoryStorageRegistry.customerStore();
            case SQLITE -> new SqliteCustomerStore();
        };
    }
}
