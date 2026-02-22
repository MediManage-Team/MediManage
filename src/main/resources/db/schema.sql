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
    generic_name TEXT,
    company TEXT,
    price REAL NOT NULL,
    expiry_date TEXT,
    active INTEGER DEFAULT 1
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
    photo_id_path TEXT,
    current_balance REAL DEFAULT 0.0
);
-- 5. BILLS
CREATE TABLE IF NOT EXISTS bills (
    bill_id INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_id INTEGER,
    user_id INTEGER,
    total_amount REAL,
    bill_date TEXT DEFAULT CURRENT_TIMESTAMP,
    payment_mode TEXT DEFAULT 'CASH',
    ai_care_protocol TEXT,
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
CREATE INDEX IF NOT EXISTS idx_medicines_active_name ON medicines(active, name);
CREATE INDEX IF NOT EXISTS idx_medicines_company ON medicines(company);
CREATE INDEX IF NOT EXISTS idx_medicines_expiry ON medicines(expiry_date);
CREATE INDEX IF NOT EXISTS idx_stock_medicine ON stock(medicine_id);
CREATE INDEX IF NOT EXISTS idx_bills_date ON bills(bill_date);
CREATE INDEX IF NOT EXISTS idx_bills_customer ON bills(customer_id);
CREATE INDEX IF NOT EXISTS idx_bill_items_bill ON bill_items(bill_id);
CREATE INDEX IF NOT EXISTS idx_bill_items_medicine ON bill_items(medicine_id);
CREATE INDEX IF NOT EXISTS idx_customers_phone ON customers(phone);
CREATE INDEX IF NOT EXISTS idx_expenses_date ON expenses(date);
-- ======================== MIGRATIONS ========================
-- Keep ADD COLUMN migrations for existing installs that predate these fields.
-- DatabaseUtil checks PRAGMA table_info and skips when columns already exist.
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
CREATE INDEX IF NOT EXISTS idx_prescriptions_status_date ON prescriptions(status, prescribed_date DESC);

-- 9. SUBSCRIPTION PLANS
CREATE TABLE IF NOT EXISTS subscription_plans (
    plan_id INTEGER PRIMARY KEY AUTOINCREMENT,
    plan_code TEXT UNIQUE NOT NULL,
    plan_name TEXT NOT NULL,
    description TEXT,
    price REAL NOT NULL DEFAULT 0.0,
    duration_days INTEGER NOT NULL,
    grace_days INTEGER NOT NULL DEFAULT 0,
    default_discount_percent REAL NOT NULL DEFAULT 0.0,
    max_discount_percent REAL NOT NULL DEFAULT 0.0,
    minimum_margin_percent REAL NOT NULL DEFAULT 0.0,
    status TEXT NOT NULL DEFAULT 'DRAFT',
    auto_renew INTEGER NOT NULL DEFAULT 0,
    requires_approval INTEGER NOT NULL DEFAULT 1,
    created_by_user_id INTEGER,
    updated_by_user_id INTEGER,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by_user_id) REFERENCES users(user_id),
    FOREIGN KEY (updated_by_user_id) REFERENCES users(user_id),
    CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'RETIRED'))
);

-- 10. SUBSCRIPTION PLAN CATEGORY RULES
CREATE TABLE IF NOT EXISTS subscription_plan_category_rules (
    rule_id INTEGER PRIMARY KEY AUTOINCREMENT,
    plan_id INTEGER NOT NULL,
    category_name TEXT NOT NULL,
    include_rule INTEGER NOT NULL DEFAULT 1,
    discount_percent REAL NOT NULL DEFAULT 0.0,
    max_discount_amount REAL,
    min_margin_percent REAL,
    active INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (plan_id) REFERENCES subscription_plans(plan_id) ON DELETE CASCADE
);

-- 11. SUBSCRIPTION PLAN MEDICINE RULES
CREATE TABLE IF NOT EXISTS subscription_plan_medicine_rules (
    rule_id INTEGER PRIMARY KEY AUTOINCREMENT,
    plan_id INTEGER NOT NULL,
    medicine_id INTEGER NOT NULL,
    include_rule INTEGER NOT NULL DEFAULT 1,
    discount_percent REAL NOT NULL DEFAULT 0.0,
    max_discount_amount REAL,
    min_margin_percent REAL,
    active INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (plan_id) REFERENCES subscription_plans(plan_id) ON DELETE CASCADE,
    FOREIGN KEY (medicine_id) REFERENCES medicines(medicine_id)
);

-- 12. CUSTOMER SUBSCRIPTIONS (ENROLLMENTS)
CREATE TABLE IF NOT EXISTS customer_subscriptions (
    enrollment_id INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_id INTEGER NOT NULL,
    plan_id INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    start_date TEXT NOT NULL,
    end_date TEXT NOT NULL,
    grace_end_date TEXT,
    enrollment_channel TEXT,
    enrolled_by_user_id INTEGER,
    approved_by_user_id INTEGER,
    approval_reference TEXT,
    cancellation_reason TEXT,
    frozen_reason TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (plan_id) REFERENCES subscription_plans(plan_id),
    FOREIGN KEY (enrolled_by_user_id) REFERENCES users(user_id),
    FOREIGN KEY (approved_by_user_id) REFERENCES users(user_id),
    CHECK (status IN ('ACTIVE', 'FROZEN', 'CANCELLED', 'EXPIRED'))
);

-- 13. CUSTOMER SUBSCRIPTION STATUS/EVENT HISTORY
CREATE TABLE IF NOT EXISTS customer_subscription_events (
    event_id INTEGER PRIMARY KEY AUTOINCREMENT,
    enrollment_id INTEGER NOT NULL,
    event_type TEXT NOT NULL,
    old_plan_id INTEGER,
    new_plan_id INTEGER,
    event_note TEXT,
    effective_at TEXT NOT NULL,
    created_by_user_id INTEGER,
    approved_by_user_id INTEGER,
    approval_reference TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (enrollment_id) REFERENCES customer_subscriptions(enrollment_id) ON DELETE CASCADE,
    FOREIGN KEY (old_plan_id) REFERENCES subscription_plans(plan_id),
    FOREIGN KEY (new_plan_id) REFERENCES subscription_plans(plan_id),
    FOREIGN KEY (created_by_user_id) REFERENCES users(user_id),
    FOREIGN KEY (approved_by_user_id) REFERENCES users(user_id)
);

-- 14. SUBSCRIPTION APPROVAL WORKFLOW
CREATE TABLE IF NOT EXISTS subscription_approvals (
    approval_id INTEGER PRIMARY KEY AUTOINCREMENT,
    approval_type TEXT NOT NULL,
    request_ref_type TEXT NOT NULL,
    request_ref_id INTEGER NOT NULL,
    requested_by_user_id INTEGER NOT NULL,
    approver_user_id INTEGER,
    approval_status TEXT NOT NULL DEFAULT 'PENDING',
    reason TEXT NOT NULL,
    approved_at TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (requested_by_user_id) REFERENCES users(user_id),
    FOREIGN KEY (approver_user_id) REFERENCES users(user_id),
    CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

-- 15. SUBSCRIPTION DISCOUNT OVERRIDES
CREATE TABLE IF NOT EXISTS subscription_discount_overrides (
    override_id INTEGER PRIMARY KEY AUTOINCREMENT,
    bill_id INTEGER,
    bill_item_id INTEGER,
    customer_id INTEGER,
    enrollment_id INTEGER,
    requested_discount_percent REAL NOT NULL,
    approved_discount_percent REAL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    reason TEXT NOT NULL,
    requested_by_user_id INTEGER NOT NULL,
    approved_by_user_id INTEGER,
    approval_id INTEGER,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at TEXT,
    FOREIGN KEY (bill_id) REFERENCES bills(bill_id),
    FOREIGN KEY (bill_item_id) REFERENCES bill_items(item_id),
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (enrollment_id) REFERENCES customer_subscriptions(enrollment_id),
    FOREIGN KEY (requested_by_user_id) REFERENCES users(user_id),
    FOREIGN KEY (approved_by_user_id) REFERENCES users(user_id),
    FOREIGN KEY (approval_id) REFERENCES subscription_approvals(approval_id),
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED'))
);

-- 16. TAMPER-EVIDENT SUBSCRIPTION AUDIT LOG
CREATE TABLE IF NOT EXISTS subscription_audit_log (
    audit_id INTEGER PRIMARY KEY AUTOINCREMENT,
    event_type TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    actor_user_id INTEGER,
    approval_id INTEGER,
    reason TEXT,
    before_json TEXT,
    after_json TEXT,
    previous_checksum TEXT,
    checksum TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (actor_user_id) REFERENCES users(user_id),
    FOREIGN KEY (approval_id) REFERENCES subscription_approvals(approval_id)
);

CREATE INDEX IF NOT EXISTS idx_subscription_plans_status ON subscription_plans(status);
CREATE INDEX IF NOT EXISTS idx_subscription_plans_code ON subscription_plans(plan_code);
CREATE INDEX IF NOT EXISTS idx_sub_cat_rules_plan ON subscription_plan_category_rules(plan_id, active);
CREATE INDEX IF NOT EXISTS idx_sub_med_rules_plan ON subscription_plan_medicine_rules(plan_id, active);
CREATE INDEX IF NOT EXISTS idx_sub_med_rules_medicine ON subscription_plan_medicine_rules(medicine_id, active);
CREATE INDEX IF NOT EXISTS idx_customer_subscriptions_customer ON customer_subscriptions(customer_id, status);
CREATE INDEX IF NOT EXISTS idx_customer_subscriptions_plan ON customer_subscriptions(plan_id, status);
CREATE INDEX IF NOT EXISTS idx_customer_subscriptions_end_date ON customer_subscriptions(end_date);
CREATE INDEX IF NOT EXISTS idx_customer_sub_events_enrollment ON customer_subscription_events(enrollment_id, effective_at);
CREATE INDEX IF NOT EXISTS idx_subscription_approvals_status ON subscription_approvals(approval_status, created_at);
CREATE INDEX IF NOT EXISTS idx_subscription_overrides_status ON subscription_discount_overrides(status, created_at);
CREATE INDEX IF NOT EXISTS idx_subscription_audit_created ON subscription_audit_log(created_at);
CREATE INDEX IF NOT EXISTS idx_subscription_audit_entity ON subscription_audit_log(entity_type, entity_id);

-- Phase 1 Subscription Discount Columns
ALTER TABLE bills
ADD COLUMN subscription_enrollment_id INTEGER;
ALTER TABLE bills
ADD COLUMN subscription_plan_id INTEGER;
ALTER TABLE bills
ADD COLUMN subscription_discount_percent REAL DEFAULT 0.0;
ALTER TABLE bills
ADD COLUMN subscription_savings_amount REAL DEFAULT 0.0;
ALTER TABLE bills
ADD COLUMN subscription_approval_reference TEXT;
ALTER TABLE bill_items
ADD COLUMN subscription_discount_percent REAL DEFAULT 0.0;
ALTER TABLE bill_items
ADD COLUMN subscription_discount_amount REAL DEFAULT 0.0;
ALTER TABLE bill_items
ADD COLUMN subscription_rule_source TEXT;
ALTER TABLE subscription_plans
ADD COLUMN default_discount_percent REAL DEFAULT 0.0;
