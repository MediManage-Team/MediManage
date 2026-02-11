package org.example.MediManage.dao;

/**
 * Factory class for creating DAO instances.
 * Provides centralized DAO instantiation following the Factory pattern.
 */
public class DAOFactory {

    private static MedicineDAO medicineDAO;
    private static CustomerDAO customerDAO;
    private static BillDAO billDAO;
    private static UserDAO userDAO;
    private static ExpenseDAO expenseDAO;

    /**
     * Gets the singleton instance of MedicineDAO.
     * 
     * @return MedicineDAO instance
     */
    public static synchronized MedicineDAO getMedicineDAO() {
        if (medicineDAO == null) {
            medicineDAO = new MedicineDAO();
        }
        return medicineDAO;
    }

    /**
     * Gets the singleton instance of CustomerDAO.
     * 
     * @return CustomerDAO instance
     */
    public static synchronized CustomerDAO getCustomerDAO() {
        if (customerDAO == null) {
            customerDAO = new CustomerDAO();
        }
        return customerDAO;
    }

    /**
     * Gets the singleton instance of BillDAO.
     * 
     * @return BillDAO instance
     */
    public static synchronized BillDAO getBillDAO() {
        if (billDAO == null) {
            billDAO = new BillDAO();
        }
        return billDAO;
    }

    /**
     * Gets the singleton instance of UserDAO.
     * 
     * @return UserDAO instance
     */
    public static synchronized UserDAO getUserDAO() {
        if (userDAO == null) {
            userDAO = new UserDAO();
        }
        return userDAO;
    }

    /**
     * Gets the singleton instance of ExpenseDAO.
     * 
     * @return ExpenseDAO instance
     */
    public static synchronized ExpenseDAO getExpenseDAO() {
        if (expenseDAO == null) {
            expenseDAO = new ExpenseDAO();
        }
        return expenseDAO;
    }

    /**
     * Resets all DAO instances (primarily for testing).
     */
    public static synchronized void resetAll() {
        medicineDAO = null;
        customerDAO = null;
        billDAO = null;
        userDAO = null;
        expenseDAO = null;
    }
}
