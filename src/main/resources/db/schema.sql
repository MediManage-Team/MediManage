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
-- 7A. INVENTORY ADJUSTMENTS (RETURNS / DAMAGES)
CREATE TABLE IF NOT EXISTS inventory_adjustments (
    adjustment_id INTEGER PRIMARY KEY AUTOINCREMENT,
    medicine_id INTEGER NOT NULL,
    adjustment_type TEXT NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price REAL NOT NULL DEFAULT 0.0,
    root_cause_tag TEXT,
    notes TEXT,
    occurred_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_user_id INTEGER,
    FOREIGN KEY (medicine_id) REFERENCES medicines(medicine_id),
    FOREIGN KEY (created_by_user_id) REFERENCES users(user_id),
    CHECK (adjustment_type IN ('RETURN', 'DAMAGED'))
);
-- 7B. WEEKLY ANOMALY ACTION TRACKER
CREATE TABLE IF NOT EXISTS anomaly_action_tracker (
    action_id INTEGER PRIMARY KEY AUTOINCREMENT,
    week_start_date TEXT NOT NULL,
    week_end_date TEXT NOT NULL,
    timezone_name TEXT NOT NULL,
    alert_type TEXT NOT NULL,
    severity TEXT NOT NULL,
    metric_value TEXT,
    threshold_rule TEXT,
    alert_message TEXT,
    owner_user_id INTEGER,
    due_date TEXT,
    closure_status TEXT NOT NULL DEFAULT 'OPEN',
    closed_at TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_user_id) REFERENCES users(user_id),
    CHECK (
        closure_status IN ('OPEN', 'IN_PROGRESS', 'CLOSED')
    ),
    UNIQUE (week_start_date, timezone_name, alert_type)
);
-- 7C. ANALYTICS REPORT DISPATCH SCHEDULES
CREATE TABLE IF NOT EXISTS analytics_report_dispatch_schedules (
    schedule_id INTEGER PRIMARY KEY AUTOINCREMENT,
    channel TEXT NOT NULL,
    recipient TEXT NOT NULL,
    report_format TEXT NOT NULL,
    frequency TEXT NOT NULL,
    timezone_name TEXT NOT NULL,
    filter_start_date TEXT,
    filter_end_date TEXT,
    supplier_filter TEXT,
    category_filter TEXT,
    active INTEGER NOT NULL DEFAULT 1,
    next_run_at TEXT NOT NULL,
    last_run_at TEXT,
    last_status TEXT,
    last_error TEXT,
    created_by_user_id INTEGER,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by_user_id) REFERENCES users(user_id),
    CHECK (channel IN ('EMAIL', 'WHATSAPP')),
    CHECK (report_format IN ('PDF', 'EXCEL', 'CSV')),
    CHECK (frequency IN ('DAILY', 'WEEKLY', 'MONTHLY')),
    CHECK (active IN (0, 1))
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
CREATE INDEX IF NOT EXISTS idx_customers_name ON customers(name);
CREATE INDEX IF NOT EXISTS idx_expenses_date ON expenses(date);
CREATE INDEX IF NOT EXISTS idx_inventory_adjustments_medicine ON inventory_adjustments(medicine_id);
CREATE INDEX IF NOT EXISTS idx_inventory_adjustments_type_date ON inventory_adjustments(adjustment_type, occurred_at);
CREATE INDEX IF NOT EXISTS idx_anomaly_action_tracker_week ON anomaly_action_tracker(week_start_date, timezone_name);
CREATE INDEX IF NOT EXISTS idx_anomaly_action_tracker_status_due ON anomaly_action_tracker(closure_status, due_date);
CREATE INDEX IF NOT EXISTS idx_report_dispatch_schedule_next_run ON analytics_report_dispatch_schedules(active, next_run_at);
CREATE INDEX IF NOT EXISTS idx_report_dispatch_schedule_channel ON analytics_report_dispatch_schedules(channel, recipient);
-- ======================== MIGRATIONS ========================
-- Keep ADD COLUMN migrations for existing installs that predate these fields.
-- DatabaseUtil checks PRAGMA table_info and skips when columns already exist.
-- Original migrations
ALTER TABLE medicines
ADD COLUMN generic_name TEXT;
ALTER TABLE customers
ADD COLUMN email TEXT;
ALTER TABLE customers
ADD COLUMN phone TEXT;
ALTER TABLE customers
ADD COLUMN address TEXT;
ALTER TABLE customers
ADD COLUMN nominee_name TEXT;
ALTER TABLE customers
ADD COLUMN nominee_relation TEXT;
ALTER TABLE customers
ADD COLUMN insurance_provider TEXT;
ALTER TABLE customers
ADD COLUMN insurance_policy_no TEXT;
ALTER TABLE customers
ADD COLUMN diseases TEXT;
ALTER TABLE customers
ADD COLUMN photo_id_path TEXT;
ALTER TABLE customers
ADD COLUMN current_balance REAL DEFAULT 0.0;
-- Phase 1 Optimization: new columns
CREATE TABLE IF NOT EXISTS message_templates (
    template_id INTEGER PRIMARY KEY AUTOINCREMENT,
    template_key TEXT UNIQUE NOT NULL,
    subject TEXT,
    body_template TEXT NOT NULL,
    updated_at TEXT DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_message_templates_key ON message_templates(template_key);
ALTER TABLE bills
ADD COLUMN payment_mode TEXT DEFAULT 'CASH';
ALTER TABLE bills
ADD COLUMN ai_care_protocol TEXT;
ALTER TABLE medicines
ADD COLUMN active INTEGER DEFAULT 1;
-- Index on the new generic_name column
CREATE INDEX IF NOT EXISTS idx_medicines_generic ON medicines(generic_name);
-- 8. AI PROMPT REGISTRY
CREATE TABLE IF NOT EXISTS ai_prompt_registry (
    prompt_version_id INTEGER PRIMARY KEY AUTOINCREMENT,
    prompt_key TEXT NOT NULL,
    version_number INTEGER NOT NULL,
    template_text TEXT NOT NULL,
    change_type TEXT NOT NULL,
    change_note TEXT,
    rolled_back_from_version INTEGER,
    is_active INTEGER NOT NULL DEFAULT 0,
    changed_by_user_id INTEGER,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(prompt_key, version_number),
    FOREIGN KEY (changed_by_user_id) REFERENCES users(user_id)
);
CREATE INDEX IF NOT EXISTS idx_ai_prompt_registry_active ON ai_prompt_registry(prompt_key, is_active, version_number DESC);
CREATE INDEX IF NOT EXISTS idx_ai_prompt_registry_history ON ai_prompt_registry(prompt_key, version_number DESC);

-- 8B. SUBSCRIPTION PLANS (roadmap table kept available for seed/migration compatibility)
CREATE TABLE IF NOT EXISTS subscription_plans (
    plan_id INTEGER PRIMARY KEY AUTOINCREMENT,
    plan_code TEXT NOT NULL UNIQUE,
    plan_name TEXT NOT NULL,
    description TEXT,
    price REAL NOT NULL DEFAULT 0.0,
    duration_days INTEGER NOT NULL DEFAULT 30,
    grace_days INTEGER NOT NULL DEFAULT 0,
    default_discount_percent REAL NOT NULL DEFAULT 0.0,
    max_discount_percent REAL NOT NULL DEFAULT 0.0,
    minimum_margin_percent REAL NOT NULL DEFAULT 0.0,
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    auto_renew INTEGER NOT NULL DEFAULT 0,
    requires_approval INTEGER NOT NULL DEFAULT 0,
    created_by_user_id INTEGER,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by_user_id) REFERENCES users(user_id)
);
CREATE INDEX IF NOT EXISTS idx_subscription_plans_status ON subscription_plans(status);

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
-- ======================== PHASE 1: POS FEATURES ========================
-- 19. BARCODE COLUMN ON MEDICINES
ALTER TABLE medicines
ADD COLUMN barcode TEXT;
CREATE INDEX IF NOT EXISTS idx_medicines_barcode ON medicines(barcode);
-- 20. PAYMENT SPLITS (for mixed/split payments)
CREATE TABLE IF NOT EXISTS payment_splits (
    split_id INTEGER PRIMARY KEY AUTOINCREMENT,
    bill_id INTEGER NOT NULL,
    payment_method TEXT NOT NULL,
    amount REAL NOT NULL,
    reference_number TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (bill_id) REFERENCES bills(bill_id),
    CHECK (
        payment_method IN (
            'CASH',
            'UPI',
            'CARD',
            'CREDIT',
            'CHEQUE',
            'OTHER'
        )
    )
);
CREATE INDEX IF NOT EXISTS idx_payment_splits_bill ON payment_splits(bill_id);
-- 21. HELD ORDERS (layaway / hold-and-recall)
CREATE TABLE IF NOT EXISTS held_orders (
    hold_id INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_id INTEGER,
    user_id INTEGER,
    items_json TEXT NOT NULL,
    total_amount REAL NOT NULL DEFAULT 0.0,
    notes TEXT,
    status TEXT NOT NULL DEFAULT 'HELD',
    held_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    recalled_at TEXT,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id),
    CHECK (status IN ('HELD', 'RECALLED', 'CANCELLED'))
);
CREATE INDEX IF NOT EXISTS idx_held_orders_status ON held_orders(status, held_at DESC);
CREATE INDEX IF NOT EXISTS idx_held_orders_user ON held_orders(user_id, status);
-- 22. RECEIPT SETTINGS (pharmacy branding for receipts)
CREATE TABLE IF NOT EXISTS receipt_settings (
    setting_id INTEGER PRIMARY KEY AUTOINCREMENT,
    pharmacy_name TEXT NOT NULL DEFAULT 'MediManage Pharmacy',
    address_line1 TEXT,
    address_line2 TEXT,
    phone TEXT,
    email TEXT,
    gst_number TEXT,
    logo_path TEXT,
    footer_text TEXT DEFAULT 'Thank you for your purchase!',
    show_barcode_on_receipt INTEGER NOT NULL DEFAULT 1,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- 23. PRICE OVERRIDE ON BILL ITEMS
ALTER TABLE bill_items
ADD COLUMN price_override REAL;
ALTER TABLE bill_items
ADD COLUMN override_reason TEXT;
ALTER TABLE bill_items
ADD COLUMN override_by_user_id INTEGER;
-- ======================== PHASE 2: BACKEND MANAGEMENT ========================
-- 24. SUPPLIERS
CREATE TABLE IF NOT EXISTS suppliers (
    supplier_id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    contact_person TEXT,
    phone TEXT,
    email TEXT,
    address TEXT,
    gst_number TEXT,
    active INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_suppliers_name ON suppliers(name);
CREATE INDEX IF NOT EXISTS idx_suppliers_active ON suppliers(active);
-- 25. PURCHASE ORDERS
CREATE TABLE IF NOT EXISTS purchase_orders (
    po_id INTEGER PRIMARY KEY AUTOINCREMENT,
    supplier_id INTEGER NOT NULL,
    order_date TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expected_delivery TEXT,
    status TEXT NOT NULL DEFAULT 'DRAFT',
    total_amount REAL NOT NULL DEFAULT 0.0,
    notes TEXT,
    created_by_user_id INTEGER,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id),
    FOREIGN KEY (created_by_user_id) REFERENCES users(user_id),
    CHECK (
        status IN (
            'DRAFT',
            'ORDERED',
            'PARTIAL',
            'RECEIVED',
            'CANCELLED'
        )
    )
);
CREATE INDEX IF NOT EXISTS idx_purchase_orders_supplier ON purchase_orders(supplier_id);
CREATE INDEX IF NOT EXISTS idx_purchase_orders_status ON purchase_orders(status);
-- 26. PURCHASE ORDER ITEMS
CREATE TABLE IF NOT EXISTS purchase_order_items (
    poi_id INTEGER PRIMARY KEY AUTOINCREMENT,
    po_id INTEGER NOT NULL,
    medicine_id INTEGER NOT NULL,
    medicine_name_snapshot TEXT,
    generic_name_snapshot TEXT,
    company_snapshot TEXT,
    batch_number TEXT,
    expiry_date TEXT,
    purchase_date TEXT,
    ordered_qty INTEGER NOT NULL,
    received_qty INTEGER NOT NULL DEFAULT 0,
    unit_cost REAL NOT NULL,
    selling_price REAL NOT NULL DEFAULT 0.0,
    reorder_threshold INTEGER NOT NULL DEFAULT 10,
    FOREIGN KEY (po_id) REFERENCES purchase_orders(po_id) ON DELETE CASCADE,
    FOREIGN KEY (medicine_id) REFERENCES medicines(medicine_id)
);
CREATE INDEX IF NOT EXISTS idx_poi_po ON purchase_order_items(po_id);
CREATE INDEX IF NOT EXISTS idx_poi_medicine_batch ON purchase_order_items(medicine_id, batch_number);
CREATE INDEX IF NOT EXISTS idx_poi_expiry_date ON purchase_order_items(expiry_date);
-- 27. EMPLOYEE ATTENDANCE
CREATE TABLE IF NOT EXISTS employee_attendance (
    attendance_id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    check_in_time TEXT NOT NULL,
    check_out_time TEXT,
    date TEXT NOT NULL,
    total_hours REAL,
    notes TEXT,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);
CREATE INDEX IF NOT EXISTS idx_attendance_user_date ON employee_attendance(user_id, date);
CREATE INDEX IF NOT EXISTS idx_attendance_date ON employee_attendance(date);
-- 28. SUPPLIER LINK ON MEDICINES
ALTER TABLE medicines
ADD COLUMN supplier_id INTEGER;
ALTER TABLE purchase_order_items
ADD COLUMN medicine_name_snapshot TEXT;
ALTER TABLE purchase_order_items
ADD COLUMN generic_name_snapshot TEXT;
ALTER TABLE purchase_order_items
ADD COLUMN company_snapshot TEXT;
ALTER TABLE purchase_order_items
ADD COLUMN batch_number TEXT;
ALTER TABLE purchase_order_items
ADD COLUMN expiry_date TEXT;
ALTER TABLE purchase_order_items
ADD COLUMN purchase_date TEXT;
ALTER TABLE purchase_order_items
ADD COLUMN selling_price REAL DEFAULT 0.0;
ALTER TABLE purchase_order_items
ADD COLUMN reorder_threshold INTEGER DEFAULT 10;
-- ======================== PHASE 3: ADVANCED FEATURES ========================
-- 29. PURCHASE PRICE ON MEDICINES (profit margin tracking)
ALTER TABLE medicines
ADD COLUMN purchase_price REAL DEFAULT 0.0;
-- 30. CUSTOMER LOYALTY POINTS
ALTER TABLE customers
ADD COLUMN loyalty_points INTEGER DEFAULT 0;
-- 31. REORDER THRESHOLD ON MEDICINES (low stock reorder workflow)
ALTER TABLE medicines
ADD COLUMN reorder_threshold INTEGER DEFAULT 10;
-- 32. ADD CHECK CONSTRAINT TO STOCK
DROP VIEW IF EXISTS v_medicine_management_overview;
DROP VIEW IF EXISTS v_inventory_batch_expiry_timeline;

CREATE TABLE IF NOT EXISTS stock_new (
    stock_id INTEGER PRIMARY KEY AUTOINCREMENT,
    medicine_id INTEGER UNIQUE,
    quantity INTEGER NOT NULL CHECK (quantity >= 0),
    FOREIGN KEY (medicine_id) REFERENCES medicines(medicine_id)
);
INSERT OR IGNORE INTO stock_new (stock_id, medicine_id, quantity)
SELECT stock_id, medicine_id, quantity FROM stock;
PRAGMA foreign_keys=off;
DROP TABLE IF EXISTS stock;
ALTER TABLE stock_new RENAME TO stock;
PRAGMA foreign_keys=on;
CREATE UNIQUE INDEX IF NOT EXISTS idx_stock_medicine ON stock(medicine_id);

-- ======================== PHASE 4: PRODUCTION OPERATIONS ========================
-- 37. BATCH INVENTORY (FEFO inventory control)
CREATE TABLE IF NOT EXISTS inventory_batches (
    batch_id INTEGER PRIMARY KEY AUTOINCREMENT,
    medicine_id INTEGER NOT NULL,
    source_poi_id INTEGER,
    batch_number TEXT NOT NULL,
    batch_barcode TEXT,
    expiry_date TEXT,
    purchase_date TEXT,
    unit_cost REAL NOT NULL DEFAULT 0.0,
    selling_price REAL NOT NULL DEFAULT 0.0,
    initial_quantity INTEGER NOT NULL DEFAULT 0 CHECK (initial_quantity >= 0),
    available_quantity INTEGER NOT NULL DEFAULT 0 CHECK (available_quantity >= 0),
    supplier_id INTEGER,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (medicine_id) REFERENCES medicines(medicine_id),
    FOREIGN KEY (source_poi_id) REFERENCES purchase_order_items(poi_id) ON DELETE SET NULL,
    FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id)
);
CREATE INDEX IF NOT EXISTS idx_inventory_batches_medicine ON inventory_batches(medicine_id, expiry_date, purchase_date);
CREATE INDEX IF NOT EXISTS idx_inventory_batches_expiry ON inventory_batches(expiry_date, available_quantity);
CREATE INDEX IF NOT EXISTS idx_inventory_batches_supplier ON inventory_batches(supplier_id, medicine_id);
CREATE INDEX IF NOT EXISTS idx_inventory_batches_barcode ON inventory_batches(batch_barcode);

ALTER TABLE inventory_batches
ADD COLUMN batch_barcode TEXT;

DROP VIEW IF EXISTS v_medicine_management_overview;
DROP VIEW IF EXISTS v_inventory_batch_expiry_timeline;

CREATE VIEW v_inventory_batch_expiry_timeline AS
SELECT ib.batch_id,
       ib.medicine_id,
       COALESCE(m.name, '') AS medicine_name,
       COALESCE(m.company, '') AS company,
       COALESCE(s.name, '') AS supplier_name,
       ib.batch_number,
       COALESCE(NULLIF(TRIM(ib.batch_barcode), ''), 'BT-' || ib.medicine_id || '-' || REPLACE(REPLACE(UPPER(COALESCE(ib.batch_number, 'NA')), ' ', ''), '/', '-')) AS batch_barcode,
       COALESCE(ib.expiry_date, '') AS expiry_date,
       CASE
           WHEN ib.expiry_date IS NULL OR TRIM(ib.expiry_date) = '' THEN NULL
           ELSE CAST(julianday(ib.expiry_date) - julianday('now') AS INTEGER)
       END AS days_to_expiry,
       ROW_NUMBER() OVER (
           PARTITION BY ib.medicine_id
           ORDER BY CASE WHEN ib.expiry_date IS NULL OR TRIM(ib.expiry_date) = '' THEN 1 ELSE 0 END,
                    ib.expiry_date ASC,
                    CASE WHEN ib.purchase_date IS NULL OR TRIM(ib.purchase_date) = '' THEN 1 ELSE 0 END,
                    ib.purchase_date ASC,
                    ib.batch_id ASC
       ) AS expiry_sequence,
       COALESCE(ib.purchase_date, '') AS purchase_date,
       ib.unit_cost,
       ib.selling_price,
       ib.initial_quantity,
       ib.available_quantity,
       ROUND(ib.available_quantity * ib.unit_cost, 2) AS stock_cost_value,
       ROUND(ib.available_quantity * ib.selling_price, 2) AS stock_sales_value,
       ib.created_at
FROM inventory_batches ib
JOIN medicines m ON m.medicine_id = ib.medicine_id
LEFT JOIN suppliers s ON s.supplier_id = ib.supplier_id;

CREATE VIEW v_medicine_management_overview AS
SELECT m.medicine_id,
       m.name,
       COALESCE(m.generic_name, '') AS generic_name,
       COALESCE(m.company, '') AS company,
       COALESCE(m.barcode, '') AS medicine_barcode,
       COALESCE(stock.quantity, 0) AS current_stock,
       ROUND(SUM(CASE
           WHEN timeline.available_quantity > 0 THEN timeline.available_quantity
           ELSE 0
       END), 0) AS tracked_batch_units,
       COALESCE(stock.quantity, 0) - ROUND(SUM(CASE
           WHEN timeline.available_quantity > 0 THEN timeline.available_quantity
           ELSE 0
       END), 0) AS stock_gap_units,
       COUNT(CASE WHEN timeline.available_quantity > 0 THEN 1 END) AS active_batch_count,
       MIN(CASE
           WHEN timeline.available_quantity > 0 AND timeline.expiry_date <> '' THEN timeline.expiry_date
           ELSE NULL
       END) AS earliest_batch_expiry,
       ROUND(SUM(CASE
           WHEN timeline.available_quantity > 0 AND timeline.days_to_expiry IS NOT NULL AND timeline.days_to_expiry < 0
           THEN timeline.available_quantity
           ELSE 0
       END), 0) AS expired_units,
       ROUND(SUM(CASE
           WHEN timeline.available_quantity > 0 AND timeline.days_to_expiry BETWEEN 0 AND 30
           THEN timeline.available_quantity
           ELSE 0
       END), 0) AS expiring_30d_units,
       ROUND(SUM(CASE
           WHEN timeline.available_quantity > 0 AND timeline.days_to_expiry IS NOT NULL AND timeline.days_to_expiry <= 30
           THEN timeline.stock_cost_value
           ELSE 0
       END), 2) AS expiry_exposure_cost,
       COALESCE(dumped.dumped_units, 0) AS dumped_units
FROM medicines m
LEFT JOIN stock ON stock.medicine_id = m.medicine_id
LEFT JOIN v_inventory_batch_expiry_timeline timeline ON timeline.medicine_id = m.medicine_id
LEFT JOIN (
    SELECT medicine_id,
           SUM(quantity) AS dumped_units
    FROM inventory_adjustments
    WHERE adjustment_type = 'DAMAGED'
      AND (
          UPPER(COALESCE(root_cause_tag, '')) LIKE '%DUMP%'
          OR UPPER(COALESCE(root_cause_tag, '')) LIKE '%WASTE%'
      )
    GROUP BY medicine_id
) dumped ON dumped.medicine_id = m.medicine_id
WHERE m.active = 1
GROUP BY m.medicine_id,
         m.name,
         m.generic_name,
         m.company,
         m.barcode,
         stock.quantity,
         dumped.dumped_units;

-- 38. BATCH ALLOCATION PER BILL ITEM (actual COGS tracking)
CREATE TABLE IF NOT EXISTS bill_item_batch_allocations (
    allocation_id INTEGER PRIMARY KEY AUTOINCREMENT,
    bill_id INTEGER NOT NULL,
    bill_item_id INTEGER NOT NULL,
    medicine_id INTEGER NOT NULL,
    batch_id INTEGER,
    batch_number TEXT,
    expiry_date TEXT,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_cost REAL NOT NULL DEFAULT 0.0,
    selling_price REAL NOT NULL DEFAULT 0.0,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (bill_id) REFERENCES bills(bill_id) ON DELETE CASCADE,
    FOREIGN KEY (bill_item_id) REFERENCES bill_items(item_id) ON DELETE CASCADE,
    FOREIGN KEY (medicine_id) REFERENCES medicines(medicine_id),
    FOREIGN KEY (batch_id) REFERENCES inventory_batches(batch_id) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_bill_item_batch_allocations_bill ON bill_item_batch_allocations(bill_id, bill_item_id);
CREATE INDEX IF NOT EXISTS idx_bill_item_batch_allocations_batch ON bill_item_batch_allocations(batch_id, medicine_id);

-- 39. BATCH / COGS FIELDS ON OPERATIONAL TABLES
ALTER TABLE bill_items
ADD COLUMN cost_of_goods REAL DEFAULT 0.0;
ALTER TABLE inventory_batches
ADD COLUMN batch_barcode TEXT;

UPDATE inventory_batches
SET batch_barcode = 'BT-' || medicine_id || '-' || REPLACE(REPLACE(UPPER(COALESCE(batch_number, 'NA')), ' ', ''), '/', '-')
WHERE batch_barcode IS NULL
   OR TRIM(batch_barcode) = '';
ALTER TABLE inventory_adjustments
ADD COLUMN batch_id INTEGER;

-- 40. AUDIT EVENTS
CREATE TABLE IF NOT EXISTS audit_events (
    event_id INTEGER PRIMARY KEY AUTOINCREMENT,
    occurred_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_user_id INTEGER,
    event_type TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id INTEGER,
    summary TEXT NOT NULL,
    details_json TEXT,
    FOREIGN KEY (actor_user_id) REFERENCES users(user_id)
);
CREATE INDEX IF NOT EXISTS idx_audit_events_occurred_at ON audit_events(occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_events_entity ON audit_events(entity_type, entity_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_events_actor ON audit_events(actor_user_id, occurred_at DESC);

-- 41. SUPERVISOR APPROVALS
CREATE TABLE IF NOT EXISTS supervisor_approvals (
    approval_id INTEGER PRIMARY KEY AUTOINCREMENT,
    requested_by_user_id INTEGER,
    approved_by_user_id INTEGER NOT NULL,
    action_type TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id INTEGER,
    justification TEXT,
    approval_notes TEXT,
    approved_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (requested_by_user_id) REFERENCES users(user_id),
    FOREIGN KEY (approved_by_user_id) REFERENCES users(user_id)
);
CREATE INDEX IF NOT EXISTS idx_supervisor_approvals_action ON supervisor_approvals(action_type, approved_at DESC);
CREATE INDEX IF NOT EXISTS idx_supervisor_approvals_requested_by ON supervisor_approvals(requested_by_user_id, approved_at DESC);

-- 42. BACKUP HISTORY
CREATE TABLE IF NOT EXISTS backup_history (
    backup_id INTEGER PRIMARY KEY AUTOINCREMENT,
    backup_path TEXT NOT NULL,
    backup_type TEXT NOT NULL DEFAULT 'MANUAL',
    file_size_bytes INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'SUCCESS',
    initiated_by_user_id INTEGER,
    notes TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (initiated_by_user_id) REFERENCES users(user_id)
);
CREATE INDEX IF NOT EXISTS idx_backup_history_created_at ON backup_history(created_at DESC);
