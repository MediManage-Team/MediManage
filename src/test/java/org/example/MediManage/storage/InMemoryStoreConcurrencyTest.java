package org.example.MediManage.storage;

import org.example.MediManage.model.Customer;
import org.example.MediManage.model.Medicine;
import org.example.MediManage.storage.customer.InMemoryCustomerStore;
import org.example.MediManage.storage.inventory.InMemoryMedicineStore;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryStoreConcurrencyTest {

    @Test
    void medicineStoreSupportsConcurrentAddsWithUniqueIds() throws Exception {
        InMemoryMedicineStore store = new InMemoryMedicineStore();
        int workers = 120;

        ExecutorService pool = Executors.newFixedThreadPool(12);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(workers);

        for (int i = 0; i < workers; i++) {
            int index = i;
            pool.submit(() -> {
                try {
                    store.addMedicine(
                            "Drug-" + index,
                            "Generic-" + index,
                            "Company-" + (index % 7),
                            "2030-12-31",
                            10.0 + index,
                            50,
                            0.0,
                            10);
                } finally {
                    done.countDown();
                }
                return null;
            });
        }

        assertTrue(done.await(15, TimeUnit.SECONDS));
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        assertEquals(workers, store.countActiveMedicines());
        List<Medicine> all = store.getAllMedicines();
        Set<Integer> ids = new HashSet<>();
        for (Medicine medicine : all) {
            ids.add(medicine.getId());
        }
        assertEquals(workers, ids.size());
        assertTrue(store.countMedicines("Generic-1") >= 11);
    }

    @Test
    void customerStoreSupportsConcurrentBalanceUpdates() throws Exception {
        InMemoryCustomerStore store = new InMemoryCustomerStore();
        Customer customer = new Customer(0, "Alice", "9999999999");
        store.addCustomer(customer);

        int updates = 500;
        ExecutorService pool = Executors.newFixedThreadPool(10);
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(updates);

        for (int i = 0; i < updates; i++) {
            pool.submit(() -> {
                try {
                    store.updateBalance(customer.getCustomerId(), 1.0);
                } finally {
                    done.countDown();
                }
                return null;
            });
        }

        assertTrue(done.await(15, TimeUnit.SECONDS));
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        List<Customer> allCustomers = store.getAllCustomers();
        assertEquals(1, allCustomers.size());
        assertEquals(500.0, allCustomers.get(0).getCurrentBalance(), 0.0001);
        assertEquals(1, store.searchCustomer("9999").size());
    }

    @Test
    void storageFactoryHonorsInMemoryBackendSetting() {
        String previous = System.getProperty(StorageBackendConfig.BACKEND_PROPERTY);
        System.setProperty(StorageBackendConfig.BACKEND_PROPERTY, "inmemory");
        try {
            assertTrue(StorageFactory.medicineStore() instanceof InMemoryMedicineStore);
            assertTrue(StorageFactory.customerStore() instanceof InMemoryCustomerStore);
        } finally {
            if (previous == null) {
                System.clearProperty(StorageBackendConfig.BACKEND_PROPERTY);
            } else {
                System.setProperty(StorageBackendConfig.BACKEND_PROPERTY, previous);
            }
        }
    }
}
