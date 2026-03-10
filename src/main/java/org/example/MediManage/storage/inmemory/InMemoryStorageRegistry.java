package org.example.MediManage.storage.inmemory;

import org.example.MediManage.dao.CustomerDAO;
import org.example.MediManage.dao.MedicineDAO;
import org.example.MediManage.model.Customer;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.storage.customer.CustomerStore;
import org.example.MediManage.storage.customer.InMemoryCustomerStore;
import org.example.MediManage.storage.inventory.InMemoryMedicineStore;
import org.example.MediManage.storage.inventory.MedicineStore;

import java.util.List;

public final class InMemoryStorageRegistry {
    private static final MedicineStore MEDICINE_STORE = new InMemoryMedicineStore(loadMedicineSeed());
    private static final CustomerStore CUSTOMER_STORE = new InMemoryCustomerStore(loadCustomerSeed());

    private InMemoryStorageRegistry() {
    }

    public static MedicineStore medicineStore() {
        return MEDICINE_STORE;
    }

    public static CustomerStore customerStore() {
        return CUSTOMER_STORE;
    }

    private static List<Medicine> loadMedicineSeed() {
        try {
            return new MedicineDAO().getAllMedicines();
        } catch (Exception e) {
            System.err.println("InMemoryStorageRegistry: failed to seed medicines, starting empty. " + e.getMessage());
            return List.of();
        }
    }

    private static List<Customer> loadCustomerSeed() {
        try {
            return new CustomerDAO().getAllCustomers();
        } catch (Exception e) {
            System.err.println("InMemoryStorageRegistry: failed to seed customers, starting empty. " + e.getMessage());
            return List.of();
        }
    }
}
