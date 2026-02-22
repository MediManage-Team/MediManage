package org.example.MediManage.storage.customer;

import org.example.MediManage.model.Customer;

import java.sql.SQLException;
import java.util.List;

public interface CustomerStore {
    List<Customer> getAllCustomers();

    void addCustomer(Customer customer) throws SQLException;

    void updateCustomer(Customer customer) throws SQLException;

    void deleteCustomer(int customerId) throws SQLException;

    List<Customer> searchCustomer(String query);

    void updateBalance(int customerId, double amount) throws SQLException;
}
