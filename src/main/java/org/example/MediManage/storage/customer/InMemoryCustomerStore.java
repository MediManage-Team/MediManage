package org.example.MediManage.storage.customer;

import org.example.MediManage.model.Customer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class InMemoryCustomerStore implements CustomerStore {
    private final Map<Integer, Customer> customers = new ConcurrentHashMap<>();
    private final AtomicInteger customerIdSequence = new AtomicInteger(1);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public InMemoryCustomerStore() {
    }

    public InMemoryCustomerStore(List<Customer> seedCustomers) {
        if (seedCustomers == null || seedCustomers.isEmpty()) {
            return;
        }

        lock.writeLock().lock();
        try {
            int maxId = 0;
            for (Customer customer : seedCustomers) {
                Customer copy = copyCustomer(customer);
                customers.put(copy.getCustomerId(), copy);
                maxId = Math.max(maxId, copy.getCustomerId());
            }
            customerIdSequence.set(Math.max(1, maxId + 1));
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<Customer> getAllCustomers() {
        lock.readLock().lock();
        try {
            return customers.values().stream()
                    .map(this::copyCustomer)
                    .sorted(Comparator.comparing(
                            customer -> customer.getName() == null ? "" : customer.getName(),
                            String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void addCustomer(Customer customer) {
        if (customer == null) {
            return;
        }

        lock.writeLock().lock();
        try {
            Customer copy = copyCustomer(customer);
            int providedId = copy.getCustomerId();
            int assignedId;
            if (providedId > 0 && !customers.containsKey(providedId)) {
                assignedId = providedId;
                customerIdSequence.updateAndGet(current -> Math.max(current, providedId + 1));
            } else {
                assignedId = customerIdSequence.getAndIncrement();
            }
            copy.setCustomerId(assignedId);
            customers.put(assignedId, copy);
            customer.setCustomerId(assignedId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void updateCustomer(Customer customer) {
        if (customer == null || customer.getCustomerId() <= 0) {
            return;
        }

        lock.writeLock().lock();
        try {
            if (!customers.containsKey(customer.getCustomerId())) {
                return;
            }
            customers.put(customer.getCustomerId(), copyCustomer(customer));
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void deleteCustomer(int customerId) {
        lock.writeLock().lock();
        try {
            customers.remove(customerId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<Customer> searchCustomer(String query) {
        String safeQuery = query == null ? "" : query.trim().toLowerCase();
        if (safeQuery.isEmpty()) {
            return getAllCustomers();
        }

        lock.readLock().lock();
        try {
            List<Customer> matches = new ArrayList<>();
            for (Customer customer : customers.values()) {
                String name = customer.getName() == null ? "" : customer.getName().toLowerCase();
                String phone = customer.getPhoneNumber() == null ? "" : customer.getPhoneNumber().toLowerCase();
                if (name.contains(safeQuery) || phone.contains(safeQuery)) {
                    matches.add(copyCustomer(customer));
                }
            }
            matches.sort(Comparator.comparing(
                    customer -> customer.getName() == null ? "" : customer.getName(),
                    String.CASE_INSENSITIVE_ORDER));
            return matches;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void updateBalance(int customerId, double amount) {
        lock.writeLock().lock();
        try {
            Customer existing = customers.get(customerId);
            if (existing == null) {
                return;
            }
            existing.setCurrentBalance(existing.getCurrentBalance() + amount);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private Customer copyCustomer(Customer source) {
        Customer copy = new Customer();
        copy.setCustomerId(source.getCustomerId());
        copy.setName(source.getName());
        copy.setEmail(source.getEmail());
        copy.setPhoneNumber(source.getPhoneNumber());
        copy.setAddress(source.getAddress());
        copy.setNomineeName(source.getNomineeName());
        copy.setNomineeRelation(source.getNomineeRelation());
        copy.setInsuranceProvider(source.getInsuranceProvider());
        copy.setInsurancePolicyNo(source.getInsurancePolicyNo());
        copy.setDiseases(source.getDiseases());
        copy.setPhotoIdPath(source.getPhotoIdPath());
        copy.setCurrentBalance(source.getCurrentBalance());
        return copy;
    }
}
