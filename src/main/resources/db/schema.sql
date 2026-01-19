-- SQLite Schema for MediManage
-- 1. USERS
CREATE TABLE IF NOT EXISTS users (
    user_id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    password TEXT NOT NULL,
    role TEXT DEFAULT 'STAFF'
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username ON users(username);
-- 2. MEDICINES
CREATE TABLE IF NOT EXISTS medicines (
    medicine_id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    company TEXT,
    price REAL NOT NULL,
    expiry_date TEXT
);
-- 3. STOCK
CREATE TABLE IF NOT EXISTS stock (
    stock_id INTEGER PRIMARY KEY AUTOINCREMENT,
    medicine_id INTEGER,
    quantity INTEGER NOT NULL,
    FOREIGN KEY (medicine_id) REFERENCES medicines(medicine_id)
);
-- 4. CUSTOMERS
CREATE TABLE IF NOT EXISTS customers (
    customer_id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    email TEXT,
    phone TEXT,
    address TEXT,
    nominee_name TEXT,
    nominee_relation TEXT,
    insurance_provider TEXT,
    insurance_policy_no TEXT,
    diseases TEXT,
    photo_id_path TEXT
);
-- 5. BILLS
CREATE TABLE IF NOT EXISTS bills (
    bill_id INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_id INTEGER,
    user_id INTEGER,
    total_amount REAL,
    bill_date TEXT DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);
-- 6. BILL ITEMS
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
-- 7. EXPENSES
CREATE TABLE IF NOT EXISTS expenses (
    expense_id INTEGER PRIMARY KEY AUTOINCREMENT,
    category TEXT NOT NULL,
    amount REAL NOT NULL,
    date TEXT NOT NULL,
    description TEXT
);
-- Migrations (Will fail safely if columns exist, or we can use a more robust DBUtil approach, but for now appending)
-- Note: schema.sql is run line-by-line by DatabaseUtil. simpler validation there would be better,
-- but standard SQLite ALTER TABLE ADD COLUMN is supported.
-- We can't easily check "IF NOT EXISTS" for columns in SQL script without procedural logic.
-- Ideally, DatabaseUtil should handle these errors gracefully if column exists.
-- For a strict script, we will just assume this runs on new DBs or handled by app.
-- However, to support the user's request "Run the Schema updates", we will rely on DatabaseUtil's existing try-catch for "Error executing schema statement" to swallow "duplicate column" errors.
ALTER TABLE medicines
ADD COLUMN generic_name TEXT;
ALTER TABLE customers
ADD COLUMN current_balance REAL DEFAULT 0.0;