CREATE DATABASE IF NOT EXISTS medimanage_db;
USE medimanage_db;  -- <--- THIS LINE FIXES YOUR ERROR

-- USERS
CREATE TABLE IF NOT EXISTS users (
                                     user_id INT AUTO_INCREMENT PRIMARY KEY,
                                     username VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) DEFAULT 'STAFF'
    );

INSERT IGNORE INTO users (username, password, role) VALUES
('admin', 'admin123', 'ADMIN'),
('staff', 'staff123', 'STAFF');

-- MEDICINES
CREATE TABLE IF NOT EXISTS medicines (
                                         medicine_id INT AUTO_INCREMENT PRIMARY KEY,
                                         name VARCHAR(255) NOT NULL,
    company VARCHAR(255),
    price DOUBLE NOT NULL,
    expiry_date DATE
    );

-- STOCK
CREATE TABLE IF NOT EXISTS stock (
                                     stock_id INT AUTO_INCREMENT PRIMARY KEY,
                                     medicine_id INT,
                                     quantity INT NOT NULL,
                                     FOREIGN KEY (medicine_id) REFERENCES medicines(medicine_id)
    );

-- CUSTOMERS
CREATE TABLE IF NOT EXISTS customers (
                                         customer_id INT AUTO_INCREMENT PRIMARY KEY,
                                         name VARCHAR(255) NOT NULL,
    phone VARCHAR(20)
    );

-- BILLS
CREATE TABLE IF NOT EXISTS bills (
                                     bill_id INT AUTO_INCREMENT PRIMARY KEY,
                                     customer_id INT,
                                     total_amount DOUBLE,
                                     bill_date DATETIME DEFAULT CURRENT_TIMESTAMP,
                                     FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
    );

-- BILL ITEMS
CREATE TABLE IF NOT EXISTS bill_items (
                                          item_id INT AUTO_INCREMENT PRIMARY KEY,
                                          bill_id INT,
                                          medicine_id INT,
                                          quantity INT,
                                          price DOUBLE,
                                          total DOUBLE,
                                          FOREIGN KEY (bill_id) REFERENCES bills(bill_id),
    FOREIGN KEY (medicine_id) REFERENCES medicines(medicine_id)
    );