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
-- ======================== PERFORMANCE INDEXES ========================
-- These use IF NOT EXISTS so they are safe to re-run on existing DBs.
CREATE INDEX IF NOT EXISTS idx_medicines_name ON medicines(name);
CREATE INDEX IF NOT EXISTS idx_medicines_expiry ON medicines(expiry_date);
CREATE INDEX IF NOT EXISTS idx_stock_medicine ON stock(medicine_id);
CREATE INDEX IF NOT EXISTS idx_bills_date ON bills(bill_date);
CREATE INDEX IF NOT EXISTS idx_bills_customer ON bills(customer_id);
CREATE INDEX IF NOT EXISTS idx_bill_items_bill ON bill_items(bill_id);
CREATE INDEX IF NOT EXISTS idx_bill_items_medicine ON bill_items(medicine_id);
CREATE INDEX IF NOT EXISTS idx_customers_phone ON customers(phone);
CREATE INDEX IF NOT EXISTS idx_expenses_date ON expenses(date);
-- ======================== MIGRATIONS ========================
-- ALTER TABLE ADD COLUMN will fail safely on existing DBs (DatabaseUtil's try-catch
-- swallows "duplicate column" errors). Safe to re-run.
-- Original migrations
ALTER TABLE medicines
ADD COLUMN generic_name TEXT;
ALTER TABLE customers
ADD COLUMN current_balance REAL DEFAULT 0.0;
-- Phase 1 Optimization: new columns
ALTER TABLE bills
ADD COLUMN payment_mode TEXT DEFAULT 'CASH';
ALTER TABLE bills
ADD COLUMN ai_care_protocol TEXT;
ALTER TABLE medicines
ADD COLUMN active INTEGER DEFAULT 1;
-- Index on the new generic_name column
CREATE INDEX IF NOT EXISTS idx_medicines_generic ON medicines(generic_name);
-- 8. PRESCRIPTIONS
CREATE TABLE IF NOT EXISTS prescriptions (
    prescription_id INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_id INTEGER,
    customer_name TEXT NOT NULL,
    doctor_name TEXT,
    status TEXT DEFAULT 'PENDING',
    prescribed_date TEXT DEFAULT CURRENT_TIMESTAMP,
    notes TEXT,
    medicines_text TEXT,
    ai_validation TEXT,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
);
CREATE INDEX IF NOT EXISTS idx_prescriptions_customer ON prescriptions(customer_id);
CREATE INDEX IF NOT EXISTS idx_prescriptions_status ON prescriptions(status);
-- Migration: ensure prescriptions table has all required columns (safe on existing DBs)
ALTER TABLE prescriptions
ADD COLUMN customer_name TEXT;
ALTER TABLE prescriptions
ADD COLUMN doctor_name TEXT;
ALTER TABLE prescriptions
ADD COLUMN status TEXT DEFAULT 'PENDING';
ALTER TABLE prescriptions
ADD COLUMN prescribed_date TEXT DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE prescriptions
ADD COLUMN notes TEXT;
ALTER TABLE prescriptions
ADD COLUMN medicines_text TEXT;
ALTER TABLE prescriptions
ADD COLUMN ai_validation TEXT;