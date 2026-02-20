# MediManage Database Schema

## Overview

MediManage uses **SQLite** as an embedded, zero-configuration database. The schema is designed to support a complete pharmacy management system with inventory, billing, customer tracking, and financial management.

**Database Location:**
- **Development:** `medimanage.db` in project root
- **Production:** `C:\Program Files\MediManage\runtime\db\medimanage.db`

## Schema Initialization

The database schema is automatically created on first run by `DatabaseUtil.initializeSchema()`.

## Entity Relationship Diagram

```
┌─────────────┐       ┌──────────────┐       ┌─────────────┐
│    users    │       │   medicines  │       │  customers  │
├─────────────┤       ├──────────────┤       ├─────────────┤
│ id [PK]     │       │ id [PK]      │       │ id [PK]     │
│ username    │       │ name         │       │ name        │
│ password    │       │ generic_name │       │ phone       │
│ role        │       │ company      │       │ email       │
│ created_at  │       │ price        │       │ address     │
└──────┬──────┘       │ expiry_date  │       │ balance     │
       │              │ barcode      │       │ created_at  │
       │              │ category     │       └──────┬──────┘
       │              └──────┬───────┘              │
       │                     │                      │
       │                     │                      │
       │              ┌──────▼───────┐              │
       │              │    stock     │              │
       │              ├──────────────┤              │
       │              │ id [PK]      │              │
       │              │ medicine_id  │◄─────────────┘
       │              │ quantity     │
       │              └──────────────┘
       │
       │              ┌──────────────┐
       └──────────────► bills        │
                      ├──────────────┤
                      │ id [PK]      │
                      │ date         │
                      │ customer_id  │──────────────┐
                      │ user_id [FK] │              │
                      │ subtotal     │              │
                      │ discount     │              │
                      │ total        │              │
                      │ payment_mode │              │
                      └──────┬───────┘              │
                             │                      │
                      ┌──────▼───────┐              │
                      │  bill_items  │              │
                      ├──────────────┤              │
                      │ id [PK]      │              │
                      │ bill_id [FK] │              │
                      │ medicine_id  │──────────┐   │
                      │ medicine_name│          │   │
                      │ quantity     │          │   │
                      │ price        │          │   │
                      │ total        │          │   │
                      └──────────────┘          │   │
                                                │   │
┌──────────────┐                               │   │
│   expenses   │                               │   │
├──────────────┤                               │   │
│ id [PK]      │                               │   │
│ category     │                               │   │
│ amount       │                               │   │
│ description  │                               │   │
│ date         │                               │   │
└──────────────┘                               │   │
                                                │   │
                          References ───────────┘   │
                          References ───────────────┘
```

## Table Schemas

### 1. users
Stores user accounts with role-based access control.

```sql
CREATE TABLE users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE,
    password TEXT NOT NULL,
    role TEXT NOT NULL CHECK(role IN ('ADMIN', 'MANAGER', 'PHARMACIST', 'CASHIER')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Columns:**
- `id` - Auto-incrementing primary key
- `username` - Unique username for login
- `password` - Password (should be hashed - enhancement needed)
- `role` - User role: ADMIN, MANAGER, PHARMACIST, or CASHIER
- `created_at` - Account creation timestamp

**Default Data:**
- Admin account: `admin` / `admin123` (change after first login)

**Indexes:**
```sql
CREATE UNIQUE INDEX idx_users_username ON users(username);
```

---

### 2. medicines
Product catalog with medicine information.

```sql
CREATE TABLE medicines (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    generic_name TEXT,
    company TEXT,
    price REAL NOT NULL,
    expiry_date DATE,
    barcode TEXT UNIQUE,
    category TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Columns:**
- `id` - UUID string (e.g., "MED-123e4567...")
- `name` - Brand name (e.g., "Calpol 500mg")
- `generic_name` - Generic/salt name (e.g., "Paracetamol")
- `company` - Manufacturer
- `price` - Selling price per unit
- `expiry_date` - Expiration date
- `barcode` - Unique barcode for scanning
- `category` - Medicine category (e.g., "Tablet", "Syrup")

**Indexes:**
```sql
CREATE INDEX idx_medicines_name ON medicines(name);
CREATE INDEX idx_medicines_generic ON medicines(generic_name);
CREATE INDEX idx_medicines_expiry ON medicines(expiry_date);
CREATE UNIQUE INDEX idx_medicines_barcode ON medicines(barcode);
```

**Business Rules:**
- Name and price are required
- Barcode must be unique
- Expiry alerts trigger at 30 days before expiration

---

### 3. stock
Tracks quantity for each medicine.

```sql
CREATE TABLE stock (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    medicine_id TEXT NOT NULL UNIQUE,
    quantity INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (medicine_id) REFERENCES medicines(id) ON DELETE CASCADE
);
```

**Columns:**
- `id` - Auto-incrementing primary key
- `medicine_id` - Reference to medicines table
- `quantity` - Current stock level

**Indexes:**
```sql
CREATE UNIQUE INDEX idx_stock_medicine ON stock(medicine_id);
CREATE INDEX idx_stock_quantity ON stock(quantity);
```

**Business Rules:**
- One stock entry per medicine
- Quantity decrements when bill is generated
- Low stock alert when quantity < 10

---

### 4. customers
Customer profiles for credit tracking and history.

```sql
CREATE TABLE customers (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    phone TEXT NOT NULL UNIQUE,
    email TEXT,
    address TEXT,
    current_balance REAL DEFAULT 0.0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Columns:**
- `id` - UUID string
- `name` - Customer full name
- `phone` - Unique phone number (used for search)
- `email` - Email address (optional)
- `address` - Physical address (optional)
- `current_balance` - Credit/Udhar balance (positive = owed to store)
- `created_at` - Registration timestamp

**Indexes:**
```sql
CREATE UNIQUE INDEX idx_customers_phone ON customers(phone);
CREATE INDEX idx_customers_balance ON customers(current_balance);
```

**Business Rules:**
- Phone number is unique identifier
- Balance increases when credit purchase
- Balance decreases when customer pays

---

### 5. bills
Invoice headers with payment information.

```sql
CREATE TABLE bills (
    id TEXT PRIMARY KEY,
    date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    customer_id TEXT,
    user_id INTEGER,
    subtotal REAL NOT NULL,
    discount REAL DEFAULT 0.0,
    total REAL NOT NULL,
    payment_mode TEXT NOT NULL CHECK(payment_mode IN ('Cash', 'Credit', 'UPI')),
    FOREIGN KEY (customer_id) REFERENCES customers(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

**Columns:**
- `id` - UUID string (bill/invoice number)
- `date` - Transaction timestamp
- `customer_id` - Reference to customers (nullable for cash sales)
- `user_id` - User who created the bill
- `subtotal` - Sum before discount
- `discount` - Discount amount
- `total` - Final amount (subtotal - discount)
- `payment_mode` - Cash, Credit, or UPI

**Indexes:**
```sql
CREATE INDEX idx_bills_date ON bills(date);
CREATE INDEX idx_bills_customer ON bills(customer_id);
CREATE INDEX idx_bills_user ON bills(user_id);
CREATE INDEX idx_bills_payment_mode ON bills(payment_mode);
```

**Business Rules:**
- Every bill must have a user
- Credit mode requires customer
- Total = subtotal - discount

---

### 6. bill_items
Line items for each bill (products sold).

```sql
CREATE TABLE bill_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    bill_id TEXT NOT NULL,
    medicine_id TEXT NOT NULL,
    medicine_name TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    price REAL NOT NULL,
    total REAL NOT NULL,
    FOREIGN KEY (bill_id) REFERENCES bills(id) ON DELETE CASCADE,
    FOREIGN KEY (medicine_id) REFERENCES medicines(id)
);
```

**Columns:**
- `id` - Auto-incrementing primary key
- `bill_id` - Reference to bills table
- `medicine_id` - Reference to medicines
- `medicine_name` - Cached medicine name (for historical records)
- `quantity` - Quantity sold
- `price` - Unit price at time of sale
- `total` - Line total (quantity × price)

**Indexes:**
```sql
CREATE INDEX idx_bill_items_bill ON bill_items(bill_id);
CREATE INDEX idx_bill_items_medicine ON bill_items(medicine_id);
```

**Business Rules:**
- Multiple items per bill
- Quantity must be > 0
- Total = quantity × price
- Cascade delete when bill is deleted

---

### 7. expenses
Operating cost tracking.

```sql
CREATE TABLE expenses (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    category TEXT NOT NULL,
    amount REAL NOT NULL,
    description TEXT,
    date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Columns:**
- `id` - Auto-incrementing primary key
- `category` - Expense type (Rent, Salary, Utilities, etc.)
- `amount` - Expense amount
- `description` - Additional notes (optional)
- `date` - Expense date
- `created_at` - Record creation timestamp

**Indexes:**
```sql
CREATE INDEX idx_expenses_date ON expenses(date);
CREATE INDEX idx_expenses_category ON expenses(category);
```

**Business Rules:**
- Used for net profit calculation
- Net Profit = Total Sales - Total Expenses

---

## Relationships

### One-to-One
- `medicines` ↔ `stock` (Each medicine has one stock record)

### One-to-Many
- `users` → `bills` (User creates many bills)
- `customers` → `bills` (Customer has many bills)
- `bills` → `bill_items` (Bill contains many items)
- `medicines` → `bill_items` (Medicine appears in many bills)

### Many-to-Many (through bill_items)
- `medicines` ↔ `bills` (Many medicines in many bills)

## Transaction Patterns

### Generate Invoice (ACID Transaction)

```sql
BEGIN TRANSACTION;

-- 1. Insert bill
INSERT INTO bills (id, customer_id, user_id, subtotal, discount, total, payment_mode)
VALUES (?, ?, ?, ?, ?, ?, ?);

-- 2. Insert bill items (batch)
INSERT INTO bill_items (bill_id, medicine_id, medicine_name, quantity, price, total)
VALUES (?, ?, ?, ?, ?, ?);
-- ... repeat for each item

-- 3. Update stock (for each item)
UPDATE stock SET quantity = quantity - ? WHERE medicine_id = ?;

-- 4. Update customer balance (if Credit payment)
UPDATE customers SET current_balance = current_balance + ? WHERE id = ?;

COMMIT;
```

**Rollback on Error:** If any step fails, entire transaction is rolled back.

## Query Patterns

### Common Queries

**Get Daily Sales:**
```sql
SELECT SUM(total) FROM bills
WHERE DATE(date) = DATE('now');
```

**Get Monthly Sales:**
```sql
SELECT SUM(total) FROM bills
WHERE strftime('%Y-%m', date) = strftime('%Y-%m', 'now');
```

**Low Stock Items:**
```sql
SELECT m.*, s.quantity
FROM medicines m
JOIN stock s ON m.id = s.medicine_id
WHERE s.quantity < 10
ORDER BY s.quantity ASC;
```

**Expiring Soon (30 days):**
```sql
SELECT * FROM medicines
WHERE expiry_date BETWEEN DATE('now') AND DATE('now', '+30 days')
ORDER BY expiry_date ASC;
```

**Search by Generic Name:**
```sql
SELECT m.*, s.quantity
FROM medicines m
LEFT JOIN stock s ON m.id = s.medicine_id
WHERE m.generic_name LIKE ?
ORDER BY m.name;
```

**Customer Credit Balance:**
```sql
SELECT * FROM customers
WHERE current_balance > 0
ORDER BY current_balance DESC;
```

**Total Expenses (Date Range):**
```sql
SELECT SUM(amount) FROM expenses
WHERE date BETWEEN ? AND ?;
```

**Bill History with Customer:**
```sql
SELECT b.*, c.name as customer_name, u.username
FROM bills b
LEFT JOIN customers c ON b.customer_id = c.id
JOIN users u ON b.user_id = u.id
ORDER BY b.date DESC
LIMIT 100;
```

## Schema Migrations

### Current Version: 1.0

Future migrations will be handled by adding migration scripts:
- `migration_v1_to_v2.sql`
- Version tracking table (to be added)

### Planned Enhancements

1. **Password Hashing**
   - Add salt column to users
   - Hash passwords with BCrypt

2. **Audit Trail**
   - Add `updated_at` to all tables
   - Create `audit_log` table

3. **Batch/Serial Numbers**
   - Add `batch_number` to medicines
   - Track multiple batches per medicine

4. **Supplier Management**
   - Create `suppliers` table
   - Link to medicines

5. **Prescription Tracking**
   - Create `prescriptions` table
   - Link to bills

## Performance Optimization

### Indexes
All foreign keys and frequently queried columns are indexed.

### Query Optimization
- Use prepared statements (compiled once, executed many times)
- Limit result sets where appropriate
- Avoid SELECT * in production queries

### Connection Management
- Reuse connections via `DatabaseConfig`
- Close resources in finally blocks
- Connection pooling (future enhancement)

## Backup Strategy

### Manual Backup
Simply copy `medimanage.db` file to backup location.

### Programmatic Backup
```java
DatabaseService.backupDatabase(); // Creates timestamped backup
```

### Recommended Schedule
- Daily automated backups
- Weekly off-site backups
- Monthly archives

---

**Schema Version:** 1.0  
**Last Updated:** January 2026
