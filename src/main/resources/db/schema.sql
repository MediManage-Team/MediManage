-- USERS (LOGIN)
CREATE TABLE IF NOT EXISTS users (
    user_id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    password TEXT NOT NULL
);
-- user info by chandu
INSERT OR IGNORE INTO users (username, password) VALUES
('admin', 'admin123'),
('staff', 'staff123');
INSERT INTO users(username, password, role)
VALUES ('admin', 'admin123', 'ADMIN');

-- MEDICINES
CREATE TABLE IF NOT EXISTS medicines (
    medicine_id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    company TEXT,
    price REAL NOT NULL,
    expiry_date TEXT
);

-- STOCK
CREATE TABLE IF NOT EXISTS stock (
    stock_id INTEGER PRIMARY KEY AUTOINCREMENT,
    medicine_id INTEGER,
    quantity INTEGER NOT NULL,
    FOREIGN KEY (medicine_id) REFERENCES medicines(medicine_id)
);

-- CUSTOMERS
CREATE TABLE IF NOT EXISTS customers (
    customer_id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    phone TEXT
);

-- BILLS
CREATE TABLE IF NOT EXISTS bills (
    bill_id INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_id INTEGER,
    total_amount REAL,
    bill_date TEXT DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
);

-- BILL ITEMS
CREATE TABLE IF NOT EXISTS bill_items (
    item_id INTEGER PRIMARY KEY AUTOINCREMENT,
    bill_id INTEGER,
    medicine_id INTEGER,
    quantity INTEGER,
    price REAL,
    total REAL,
    FOREIGN KEY (bill_id) REFERENCES bills(bill_id),
    FOREIGN KEY (medicine_id) REFERENCES medicines(medicine_id)
);
