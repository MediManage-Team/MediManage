package org.example.MediManage.storage.customer;

import org.example.MediManage.dao.CustomerDAO;
import org.example.MediManage.model.Customer;

import java.sql.SQLException;
import java.util.List;

public class SqliteCustomerStore implements CustomerStore {
    private final CustomerDAO customerDAO;

    public SqliteCustomerStore() {
        this(new CustomerDAO());
    }

    public SqliteCustomerStore(CustomerDAO customerDAO) {
        this.customerDAO = customerDAO;
    }

    @Override
    public List<Customer> getAllCustomers() {
        return customerDAO.getAllCustomers();
    }

    @Override
    public void addCustomer(Customer customer) throws SQLException {
        customerDAO.addCustomer(customer);
    }

    @Override
    public void updateCustomer(Customer customer) throws SQLException {
        customerDAO.updateCustomer(customer);
    }

    @Override
    public void deleteCustomer(int customerId) throws SQLException {
        customerDAO.deleteCustomer(customerId);
    }

    @Override
    public List<Customer> searchCustomer(String query) {
        return customerDAO.searchCustomer(query);
    }

    @Override
    public void updateBalance(int customerId, double amount) throws SQLException {
        customerDAO.updateBalance(customerId, amount);
    }
}
