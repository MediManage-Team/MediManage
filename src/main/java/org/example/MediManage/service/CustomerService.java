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
            
            // Check for profile updates for notifications
            boolean phoneChanged = !String.valueOf(formCustomer.getPhoneNumber()).equals(String.valueOf(selectedCustomer.getPhoneNumber()));
            boolean emailChanged = !String.valueOf(formCustomer.getEmail()).equals(String.valueOf(selectedCustomer.getEmail()));
            
            if (phoneChanged && formCustomer.getPhoneNumber() != null && !formCustomer.getPhoneNumber().isBlank()) {
                String msg = "Hello " + formCustomer.getName() + ",\n\nYour contact phone number has been successfully updated in your MediManage Pharmacy profile. If you did not request this, please contact us immediately.";
                WhatsAppService.sendNotificationWhatsApp(formCustomer.getPhoneNumber(), msg);
            }
            if (emailChanged && formCustomer.getEmail() != null && !formCustomer.getEmail().isBlank()) {
                String subject = "MediManage Profile Update";
                String body = "Hello " + formCustomer.getName() + ",\n\nYour contact email address has been successfully updated in your MediManage Pharmacy profile. If you did not request this, please contact us immediately.";
                EmailService.sendNotificationEmail(formCustomer.getEmail(), subject, body);
            }

            return new SaveResult(SaveOperation.UPDATED, "Customer updated successfully.");
        }

        customerStore.addCustomer(formCustomer);
        
        // Welcome New Customer
        if (formCustomer.getPhoneNumber() != null && !formCustomer.getPhoneNumber().isBlank()) {
            String welcomeMsg = "👋 Welcome to *MediManage Pharmacy*, " + formCustomer.getName() + "!\n\nYour profile has been successfully created. We look forward to serving you with the best care possible. Stay healthy! 🌿";
            WhatsAppService.sendNotificationWhatsApp(formCustomer.getPhoneNumber(), welcomeMsg);
        }
        if (formCustomer.getEmail() != null && !formCustomer.getEmail().isBlank()) {
            String subject = "Welcome to MediManage Pharmacy!";
            String body = "Hello " + formCustomer.getName() + ",\n\nWelcome to MediManage Pharmacy! Your profile has been successfully created. We look forward to serving you with the best care possible.\n\nStay healthy!";
            EmailService.sendNotificationEmail(formCustomer.getEmail(), subject, body);
        }
        
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
