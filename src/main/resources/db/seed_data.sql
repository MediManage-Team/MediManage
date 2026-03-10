-- ======================================================================
-- MediManage — Seed Data for uniCenta POS Features (Phases 1-3)
-- ======================================================================
-- ── PHASE 1: RECEIPT SETTINGS ──
INSERT
    OR IGNORE INTO receipt_settings (setting_key, setting_value)
VALUES ('pharmacy_name', 'MediManage Pharmacy'),
    (
        'pharmacy_address',
        '123 Health Street, Medical Colony, Bengaluru - 560001'
    ),
    ('pharmacy_phone', '+91 80 2345 6789'),
    ('pharmacy_email', 'contact@medimanage.in'),
    ('pharmacy_gst', '29AABCM1234F1Z5'),
    ('pharmacy_dl', 'KA/BNG/20B/2024/12345'),
    (
        'footer_message',
        'Thank you for choosing MediManage! Get well soon.'
    ),
    ('show_gst', 'true'),
    ('show_logo', 'true'),
    ('paper_width_mm', '80');
-- ── PHASE 1: ASSIGN BARCODES TO FIRST 50 MEDICINES ──
UPDATE medicines
SET barcode = '880100' || printf('%06d', medicine_id)
WHERE medicine_id IN (
        SELECT medicine_id
        FROM medicines
        WHERE active = 1
        ORDER BY medicine_id
        LIMIT 50
    )
    AND (
        barcode IS NULL
        OR barcode = ''
    );
-- ── PHASE 2: SUPPLIERS ──
INSERT INTO suppliers (
        name,
        contact_person,
        phone,
        email,
        address,
        gst_number
    )
VALUES (
        'Sun Pharmaceutical Industries',
        'Rajesh Kumar',
        '+91 22 4324 4324',
        'orders@sunpharma.com',
        'Mumbai, Maharashtra',
        '27AAACS1234F1ZP'
    ),
    (
        'Cipla Limited',
        'Priya Sharma',
        '+91 22 2482 6000',
        'supply@cipla.com',
        'Mumbai, Maharashtra',
        '27AABCC5678G1ZQ'
    ),
    (
        'Dr. Reddy''s Laboratories',
        'Anil Reddy',
        '+91 40 4900 2900',
        'procurement@drreddys.com',
        'Hyderabad, Telangana',
        '36AABCD1234H1ZR'
    ),
    (
        'Lupin Limited',
        'Meena Patel',
        '+91 22 6640 2323',
        'orders@lupin.com',
        'Mumbai, Maharashtra',
        '27AABCL9876J1ZS'
    ),
    (
        'Aurobindo Pharma',
        'Vijay Nair',
        '+91 40 6672 5000',
        'sales@aurobindo.com',
        'Hyderabad, Telangana',
        '36AABCA2345K1ZT'
    ),
    (
        'Zydus Lifesciences',
        'Sneha Desai',
        '+91 79 2686 8100',
        'supply@zyduslife.com',
        'Ahmedabad, Gujarat',
        '24AABCZ5678L1ZU'
    ),
    (
        'Mankind Pharma',
        'Ravi Gupta',
        '+91 11 4905 5055',
        'orders@mankindpharma.com',
        'New Delhi',
        '07AABCM3456M1ZV'
    ),
    (
        'Torrent Pharmaceuticals',
        'Anita Joshi',
        '+91 79 2658 5090',
        'supply@torrentpharma.com',
        'Ahmedabad, Gujarat',
        '24AABCT4567N1ZW'
    ),
    (
        'Glenmark Pharmaceuticals',
        'Sameer Khan',
        '+91 22 4018 9999',
        'orders@glenmarkpharma.com',
        'Mumbai, Maharashtra',
        '27AABCG6789P1ZX'
    ),
    (
        'Alkem Laboratories',
        'Deepak Tiwari',
        '+91 22 3982 8888',
        'supply@alkemlabs.com',
        'Mumbai, Maharashtra',
        '27AABCA7890Q1ZY'
    ),
    (
        'Biocon Limited',
        'Kavitha Rao',
        '+91 80 2808 2808',
        'pharma@biocon.com',
        'Bengaluru, Karnataka',
        '29AABCB1234R1ZZ'
    ),
    (
        'Cadila Healthcare',
        'Nitin Shah',
        '+91 79 2685 6001',
        'sales@cadila.com',
        'Ahmedabad, Gujarat',
        '24AABCC2345S1ZA'
    );
-- ── PHASE 2: LINK SOME MEDICINES TO SUPPLIERS ──
UPDATE medicines
SET supplier_id = 1
WHERE medicine_id IN (
        SELECT medicine_id
        FROM medicines
        WHERE active = 1
        ORDER BY RANDOM()
        LIMIT 500
    );
UPDATE medicines
SET supplier_id = 2
WHERE medicine_id IN (
        SELECT medicine_id
        FROM medicines
        WHERE active = 1
            AND supplier_id IS NULL
        ORDER BY RANDOM()
        LIMIT 500
    );
UPDATE medicines
SET supplier_id = 3
WHERE medicine_id IN (
        SELECT medicine_id
        FROM medicines
        WHERE active = 1
            AND supplier_id IS NULL
        ORDER BY RANDOM()
        LIMIT 400
    );
UPDATE medicines
SET supplier_id = 4
WHERE medicine_id IN (
        SELECT medicine_id
        FROM medicines
        WHERE active = 1
            AND supplier_id IS NULL
        ORDER BY RANDOM()
        LIMIT 300
    );
UPDATE medicines
SET supplier_id = 5
WHERE medicine_id IN (
        SELECT medicine_id
        FROM medicines
        WHERE active = 1
            AND supplier_id IS NULL
        ORDER BY RANDOM()
        LIMIT 300
    );
-- ── PHASE 2: PURCHASE ORDERS ──
INSERT INTO purchase_orders (
        supplier_id,
        order_date,
        expected_delivery,
        status,
        total_amount,
        notes,
        created_by_user_id
    )
VALUES (
        1,
        '2026-02-25 09:00:00',
        '2026-03-05',
        'RECEIVED',
        45000.00,
        'Monthly restock — cardiovascular meds',
        1
    ),
    (
        2,
        '2026-02-26 10:30:00',
        '2026-03-06',
        'RECEIVED',
        32000.00,
        'Respiratory medicines reorder',
        1
    ),
    (
        3,
        '2026-02-28 14:00:00',
        '2026-03-10',
        'ORDERED',
        28500.00,
        'Antibiotics weekly restock',
        3
    ),
    (
        4,
        '2026-03-01 08:00:00',
        '2026-03-12',
        'DRAFT',
        15000.00,
        'Pain management supplies',
        3
    ),
    (
        5,
        '2026-03-02 09:30:00',
        '2026-03-15',
        'ORDERED',
        21000.00,
        'Diabetes medications',
        1
    );
-- ── PHASE 2: PURCHASE ORDER ITEMS ──
INSERT INTO purchase_order_items (
        po_id,
        medicine_id,
        ordered_qty,
        received_qty,
        unit_cost
    )
VALUES (1, 1, 100, 100, 45.00),
    (1, 2, 200, 200, 32.50),
    (1, 3, 150, 150, 67.00),
    (2, 4, 80, 80, 55.00),
    (2, 5, 120, 120, 42.00),
    (3, 6, 200, 0, 28.00),
    (3, 7, 150, 0, 35.00),
    (4, 8, 60, 0, 85.00),
    (4, 9, 100, 0, 48.00),
    (5, 10, 250, 0, 38.00);
-- ── PHASE 2: EMPLOYEE ATTENDANCE (last 7 days) ──
INSERT INTO employee_attendance (
        user_id,
        check_in_time,
        check_out_time,
        date,
        total_hours,
        notes
    )
VALUES -- admin (user_id=1)
    (
        1,
        '2026-02-24 08:55:00',
        '2026-02-24 18:10:00',
        '2026-02-24',
        9.25,
        NULL
    ),
    (
        1,
        '2026-02-25 09:00:00',
        '2026-02-25 18:00:00',
        '2026-02-25',
        9.00,
        NULL
    ),
    (
        1,
        '2026-02-26 08:45:00',
        '2026-02-26 17:50:00',
        '2026-02-26',
        9.08,
        NULL
    ),
    (
        1,
        '2026-02-27 09:10:00',
        '2026-02-27 18:20:00',
        '2026-02-27',
        9.17,
        NULL
    ),
    (
        1,
        '2026-02-28 08:50:00',
        '2026-02-28 18:05:00',
        '2026-02-28',
        9.25,
        NULL
    ),
    (
        1,
        '2026-03-01 09:00:00',
        '2026-03-01 14:00:00',
        '2026-03-01',
        5.00,
        'Half day — Saturday'
    ),
    (
        1,
        '2026-03-02 08:30:00',
        NULL,
        '2026-03-02',
        NULL,
        'Currently on shift'
    ),
    -- manager (user_id=3)
    (
        3,
        '2026-02-24 09:00:00',
        '2026-02-24 17:30:00',
        '2026-02-24',
        8.50,
        NULL
    ),
    (
        3,
        '2026-02-25 09:15:00',
        '2026-02-25 18:00:00',
        '2026-02-25',
        8.75,
        NULL
    ),
    (
        3,
        '2026-02-26 08:30:00',
        '2026-02-26 17:45:00',
        '2026-02-26',
        9.25,
        NULL
    ),
    (
        3,
        '2026-02-27 09:00:00',
        '2026-02-27 18:15:00',
        '2026-02-27',
        9.25,
        NULL
    ),
    (
        3,
        '2026-02-28 09:30:00',
        '2026-02-28 18:00:00',
        '2026-02-28',
        8.50,
        NULL
    ),
    (
        3,
        '2026-03-01 09:00:00',
        '2026-03-01 13:30:00',
        '2026-03-01',
        4.50,
        'Half day — Saturday'
    ),
    -- pharmacist (user_id=4)
    (
        4,
        '2026-02-24 08:00:00',
        '2026-02-24 16:00:00',
        '2026-02-24',
        8.00,
        NULL
    ),
    (
        4,
        '2026-02-25 08:00:00',
        '2026-02-25 16:30:00',
        '2026-02-25',
        8.50,
        NULL
    ),
    (
        4,
        '2026-02-26 07:45:00',
        '2026-02-26 16:15:00',
        '2026-02-26',
        8.50,
        NULL
    ),
    (
        4,
        '2026-02-27 08:15:00',
        '2026-02-27 16:00:00',
        '2026-02-27',
        7.75,
        NULL
    ),
    (
        4,
        '2026-02-28 08:00:00',
        '2026-02-28 16:30:00',
        '2026-02-28',
        8.50,
        NULL
    ),
    (
        4,
        '2026-03-01 08:00:00',
        '2026-03-01 12:00:00',
        '2026-03-01',
        4.00,
        'Half day'
    ),
    (
        4,
        '2026-03-02 08:00:00',
        NULL,
        '2026-03-02',
        NULL,
        'Currently on shift'
    ),
    -- cashier (user_id=5)
    (
        5,
        '2026-02-24 10:00:00',
        '2026-02-24 19:00:00',
        '2026-02-24',
        9.00,
        NULL
    ),
    (
        5,
        '2026-02-25 10:00:00',
        '2026-02-25 19:30:00',
        '2026-02-25',
        9.50,
        NULL
    ),
    (
        5,
        '2026-02-26 09:30:00',
        '2026-02-26 19:00:00',
        '2026-02-26',
        9.50,
        NULL
    ),
    (
        5,
        '2026-02-27 10:00:00',
        '2026-02-27 19:00:00',
        '2026-02-27',
        9.00,
        NULL
    ),
    (
        5,
        '2026-02-28 10:00:00',
        '2026-02-28 18:30:00',
        '2026-02-28',
        8.50,
        NULL
    ),
    (
        5,
        '2026-03-02 10:00:00',
        NULL,
        '2026-03-02',
        NULL,
        'Currently on shift'
    );
-- ── PHASE 3: LOCATIONS ──
INSERT INTO locations (name, address, phone, location_type)
VALUES (
        'Main Store — Koramangala',
        '45, 1st Cross, Koramangala 4th Block, Bengaluru 560034',
        '+91 80 2345 6789',
        'PHARMACY'
    ),
    (
        'Branch — Indiranagar',
        '12, 100 Ft Road, Indiranagar, Bengaluru 560038',
        '+91 80 2567 8901',
        'PHARMACY'
    ),
    (
        'Branch — Whitefield',
        '78, ITPL Main Rd, Whitefield, Bengaluru 560066',
        '+91 80 2789 0123',
        'PHARMACY'
    ),
    (
        'Central Warehouse',
        'Plot 22, Industrial Area, Peenya, Bengaluru 560058',
        '+91 80 2890 1234',
        'WAREHOUSE'
    ),
    (
        'Clinic Dispensary — JP Nagar',
        '34, 15th Cross, JP Nagar Phase 1, Bengaluru 560078',
        '+91 80 2901 2345',
        'CLINIC'
    );
-- ── PHASE 3: LOCATION STOCK (sample inventory across locations) ──
-- Main Store (location_id=1)
INSERT
    OR IGNORE INTO location_stock (location_id, medicine_id, quantity, min_stock)
VALUES (1, 1, 85, 10),
    (1, 2, 120, 20),
    (1, 3, 50, 10),
    (1, 4, 200, 30),
    (1, 5, 95, 15),
    (1, 6, 60, 10),
    (1, 7, 150, 20),
    (1, 8, 40, 5),
    (1, 9, 110, 15),
    (1, 10, 180, 25);
-- Indiranagar Branch (location_id=2)
INSERT
    OR IGNORE INTO location_stock (location_id, medicine_id, quantity, min_stock)
VALUES (2, 1, 30, 5),
    (2, 2, 45, 10),
    (2, 3, 20, 5),
    (2, 4, 80, 15),
    (2, 5, 35, 10),
    (2, 6, 25, 5),
    (2, 7, 60, 10),
    (2, 8, 15, 3),
    (2, 9, 40, 10),
    (2, 10, 70, 15);
-- Whitefield Branch (location_id=3)
INSERT
    OR IGNORE INTO location_stock (location_id, medicine_id, quantity, min_stock)
VALUES (3, 1, 25, 5),
    (3, 2, 35, 10),
    (3, 3, 15, 5),
    (3, 4, 65, 10),
    (3, 5, 30, 8),
    (3, 6, 20, 5),
    (3, 7, 50, 10),
    (3, 8, 10, 3),
    (3, 9, 35, 8),
    (3, 10, 55, 12);
-- Central Warehouse (location_id=4) — high stock
INSERT
    OR IGNORE INTO location_stock (location_id, medicine_id, quantity, min_stock)
VALUES (4, 1, 500, 50),
    (4, 2, 800, 100),
    (4, 3, 350, 50),
    (4, 4, 1000, 100),
    (4, 5, 600, 80),
    (4, 6, 400, 50),
    (4, 7, 900, 100),
    (4, 8, 250, 30),
    (4, 9, 700, 80),
    (4, 10, 1200, 150);
-- ── PHASE 3: STOCK TRANSFERS (sample) ──
INSERT INTO stock_transfers (
        from_location_id,
        to_location_id,
        medicine_id,
        quantity,
        status,
        requested_by,
        requested_at,
        completed_at
    )
VALUES (
        4,
        1,
        2,
        50,
        'COMPLETED',
        1,
        '2026-02-28 10:00:00',
        '2026-02-28 15:00:00'
    ),
    (
        4,
        2,
        4,
        30,
        'COMPLETED',
        3,
        '2026-02-28 11:00:00',
        '2026-03-01 09:00:00'
    ),
    (
        4,
        3,
        7,
        25,
        'IN_TRANSIT',
        1,
        '2026-03-01 14:00:00',
        NULL
    ),
    (
        4,
        1,
        10,
        40,
        'PENDING',
        3,
        '2026-03-02 08:00:00',
        NULL
    ),
    (
        1,
        2,
        5,
        15,
        'PENDING',
        1,
        '2026-03-02 09:30:00',
        NULL
    );
-- ── HELD ORDERS (sample layaway) ──
INSERT INTO held_orders (
        customer_id,
        user_id,
        items_json,
        total_amount,
        notes,
        status,
        held_at
    )
VALUES (
        1,
        5,
        '[{"medicineId":1,"name":"Paracetamol 500mg","qty":3,"price":12.50},{"medicineId":2,"name":"Amoxicillin 250mg","qty":2,"price":45.00}]',
        127.50,
        'Customer will return in evening',
        'HELD',
        '2026-03-02 10:30:00'
    ),
    (
        2,
        5,
        '[{"medicineId":4,"name":"Metformin 500mg","qty":5,"price":8.50}]',
        42.50,
        'Waiting for prescription verification',
        'HELD',
        '2026-03-02 11:15:00'
    );
-- Verify counts
SELECT 'suppliers' AS tbl,
    COUNT(*) AS cnt
FROM suppliers
UNION ALL
SELECT 'locations',
    COUNT(*)
FROM locations
UNION ALL
SELECT 'employee_attendance',
    COUNT(*)
FROM employee_attendance
UNION ALL
SELECT 'location_stock',
    COUNT(*)
FROM location_stock
UNION ALL
SELECT 'stock_transfers',
    COUNT(*)
FROM stock_transfers
UNION ALL
SELECT 'purchase_orders',
    COUNT(*)
FROM purchase_orders
UNION ALL
SELECT 'held_orders',
    COUNT(*)
FROM held_orders
UNION ALL
SELECT 'receipt_settings',
    COUNT(*)
FROM receipt_settings
UNION ALL
SELECT 'medicines_with_barcode',
    COUNT(*)
FROM medicines
WHERE barcode IS NOT NULL
    AND barcode != '';