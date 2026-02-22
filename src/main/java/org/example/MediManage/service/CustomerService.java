package org.example.MediManage.service;

import org.example.MediManage.model.Customer;
import org.example.MediManage.service.ai.AIAssistantService;
import org.example.MediManage.storage.StorageFactory;
import org.example.MediManage.storage.customer.CustomerStore;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CustomerService {
    public enum SaveOperation {
        ADDED,
        UPDATED
    }

    public record SaveResult(SaveOperation operation, String message) {
    }

    public record HealthAnalysisPreparation(boolean canProceed, String message, String diseases) {
    }

    private final CustomerStore customerStore;
    private final AIAssistantService aiService;

    public CustomerService() {
        this(StorageFactory.customerStore(), new AIAssistantService());
    }

    CustomerService(CustomerStore customerStore, AIAssistantService aiService) {
        this.customerStore = customerStore;
        this.aiService = aiService;
    }

    public List<Customer> getAllCustomers() {
        return customerStore.getAllCustomers();
    }

    public boolean matchesSearch(Customer customer, String query) {
        if (query == null || query.trim().isEmpty()) {
            return true;
        }
        String lower = query.toLowerCase().trim();
        return (customer.getName() != null && customer.getName().toLowerCase().contains(lower))
                || (customer.getPhoneNumber() != null && customer.getPhoneNumber().contains(lower))
                || (customer.getEmail() != null && customer.getEmail().toLowerCase().contains(lower));
    }

    public String validateRequiredFields(String name, String phone) {
        if (name == null || name.trim().isEmpty()) {
            return "Name is required.";
        }
        if (phone == null || phone.trim().isEmpty()) {
            return "Phone number is required.";
        }
        return null;
    }

    public SaveResult saveCustomer(Customer formCustomer, Customer selectedCustomer) throws SQLException {
        if (selectedCustomer != null) {
            formCustomer.setCustomerId(selectedCustomer.getCustomerId());
            customerStore.updateCustomer(formCustomer);
            return new SaveResult(SaveOperation.UPDATED, "Customer updated successfully.");
        }

        customerStore.addCustomer(formCustomer);
        return new SaveResult(SaveOperation.ADDED, "Customer added successfully.");
    }

    public void deleteCustomer(Customer customer) throws SQLException {
        customerStore.deleteCustomer(customer.getCustomerId());
    }

    public HealthAnalysisPreparation prepareHealthAnalysis(Customer selectedCustomer, String diseases) {
        if (selectedCustomer == null) {
            return new HealthAnalysisPreparation(false, "Please select a customer first.", "");
        }
        if (diseases == null || diseases.trim().isEmpty()) {
            return new HealthAnalysisPreparation(false, "No known conditions listed. Add conditions to get a health analysis.", "");
        }
        return new HealthAnalysisPreparation(true, "Analyzing health profile with AI...", diseases.trim());
    }

    public CompletableFuture<String> analyzeCustomerHealth(Customer customer, String diseases) {
        return aiService.analyzeCustomerHistory(
                customer.getCustomerId(),
                customer.getName(),
                diseases);
    }
}
