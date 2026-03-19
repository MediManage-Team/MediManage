const sqlite3 = require('sqlite3').verbose();
const path = require('path');
const fs = require('fs');

const baseDbPath = path.join(__dirname, '..', 'base_medimanage.db');
const sourceDbPath = path.join(__dirname, '..', 'medimanage.db');
const schemaPath = path.join(__dirname, '..', 'src', 'main', 'resources', 'db', 'schema.sql');

const DEMO_PASSWORD_HASH = '$2a$10$abcdefghijklmnopqrstuuGJQZ0fD7FQYJYqj5H4Qx7lW7N6m';
const SUPPLIER_COUNT = 12;

function openDatabase(filePath) {
    return new sqlite3.Database(filePath, (err) => {
        if (err) {
            console.error(`Failed to open database ${filePath}:`, err.message);
            process.exit(1);
        }
    });
}

function exec(db, sql, label) {
    return new Promise((resolve, reject) => {
        db.exec(sql, (err) => {
            if (err) {
                reject(new Error(`${label}: ${err.message}`));
                return;
            }
            resolve();
        });
    });
}

async function execStatements(db, sqlText, label, ignoredPatterns = []) {
    const statements = sqlText
        .split(';')
        .map((statement) => statement.trim())
        .filter((statement) => statement.length > 0);

    for (const statement of statements) {
        try {
            await exec(db, statement + ';', label);
        } catch (error) {
            const message = error.message || '';
            if (ignoredPatterns.some((pattern) => message.includes(pattern))) {
                continue;
            }
            const preview = statement.replace(/\s+/g, ' ').slice(0, 140);
            throw new Error(`${label}: ${message} | Statement: ${preview}`);
        }
    }
}

function run(db, sql, params = []) {
    return new Promise((resolve, reject) => {
        db.run(sql, params, function callback(err) {
            if (err) {
                reject(err);
                return;
            }
            resolve(this);
        });
    });
}

function get(db, sql, params = []) {
    return new Promise((resolve, reject) => {
        db.get(sql, params, (err, row) => {
            if (err) {
                reject(err);
                return;
            }
            resolve(row);
        });
    });
}

function all(db, sql, params = []) {
    return new Promise((resolve, reject) => {
        db.all(sql, params, (err, rows) => {
            if (err) {
                reject(err);
                return;
            }
            resolve(rows);
        });
    });
}

function close(db) {
    return new Promise((resolve, reject) => {
        db.close((err) => {
            if (err) {
                reject(err);
                return;
            }
            resolve();
        });
    });
}

function escapeSqlString(value) {
    return String(value).replace(/'/g, "''");
}

async function seedCoreActorsAndCustomers(db) {
    console.log('1. Seeding non-admin staff and customers...');
    await exec(db, `
        INSERT INTO users (user_id, username, password, role) VALUES
            (1, 'manager.demo', '${DEMO_PASSWORD_HASH}', 'MANAGER'),
            (2, 'inventory.demo', '${DEMO_PASSWORD_HASH}', 'STAFF'),
            (3, 'procurement.demo', '${DEMO_PASSWORD_HASH}', 'MANAGER'),
            (4, 'pharmacist.demo', '${DEMO_PASSWORD_HASH}', 'PHARMACIST'),
            (5, 'cashier.demo', '${DEMO_PASSWORD_HASH}', 'CASHIER');

        INSERT INTO customers (
            customer_id,
            name,
            email,
            phone,
            address,
            nominee_name,
            nominee_relation,
            insurance_provider,
            insurance_policy_no,
            diseases,
            current_balance
        ) VALUES
            (1, 'Ram Kumar', 'ram.kumar@example.com', '+91-9000000001', 'Bengaluru', 'Asha Kumar', 'Spouse', 'MediSure', 'MS-001', 'Hypertension', 320.00),
            (2, 'Vikash Rao', 'vikash.rao@example.com', '+91-9000000002', 'Indiranagar', 'Ritu Rao', 'Spouse', 'HealthPrime', 'HP-002', 'Type 2 Diabetes', 0.00),
            (3, 'Asha Menon', 'asha.menon@example.com', '+91-9000000003', 'Whitefield', 'Kiran Menon', 'Brother', NULL, NULL, 'Migraine', 120.00),
            (4, 'Sunita Iyer', 'sunita.iyer@example.com', '+91-9000000004', 'Koramangala', 'Maya Iyer', 'Daughter', 'CarePlus', 'CP-004', 'Arthritis', 0.00),
            (5, 'Harish Patel', 'harish.patel@example.com', '+91-9000000005', 'JP Nagar', 'Nisha Patel', 'Spouse', 'MediSure', 'MS-005', 'Gastritis', 210.00),
            (6, 'Geetha Nair', 'geetha.nair@example.com', '+91-9000000006', 'HSR Layout', 'Rohan Nair', 'Son', NULL, NULL, 'Seasonal Allergy', 0.00),
            (7, 'Mahesh Gowda', 'mahesh.gowda@example.com', '+91-9000000007', 'Yelahanka', 'Shanthi Gowda', 'Mother', 'HealthPrime', 'HP-007', 'Joint Pain', 80.00),
            (8, 'Pooja Sharma', 'pooja.sharma@example.com', '+91-9000000008', 'BTM Layout', 'Rakesh Sharma', 'Father', NULL, NULL, 'Asthma', 0.00),
            (9, 'Deepa Joshi', 'deepa.joshi@example.com', '+91-9000000009', 'Malleshwaram', 'Suresh Joshi', 'Spouse', 'CarePlus', 'CP-009', 'Thyroid', 455.00),
            (10, 'Farhan Ali', 'farhan.ali@example.com', '+91-9000000010', 'Electronic City', 'Amina Ali', 'Mother', NULL, NULL, 'High Cholesterol', 0.00);

        WITH RECURSIVE seq(n) AS (
            SELECT 11
            UNION ALL
            SELECT n + 1 FROM seq WHERE n < 120
        )
        INSERT INTO customers (
            customer_id,
            name,
            email,
            phone,
            address,
            nominee_name,
            nominee_relation,
            insurance_provider,
            insurance_policy_no,
            diseases,
            current_balance
        )
        SELECT
            n,
            'Customer ' || printf('%03d', n),
            'customer' || printf('%03d', n) || '@example.com',
            '+91-9' || printf('%09d', n),
            'Demo Area ' || ((n % 8) + 1),
            'Nominee ' || printf('%03d', n),
            CASE n % 4
                WHEN 0 THEN 'Spouse'
                WHEN 1 THEN 'Parent'
                WHEN 2 THEN 'Sibling'
                ELSE 'Child'
            END,
            CASE WHEN n % 3 = 0 THEN 'HealthPrime' WHEN n % 5 = 0 THEN 'MediSure' ELSE NULL END,
            CASE WHEN n % 3 = 0 OR n % 5 = 0 THEN 'POL-' || printf('%05d', n) ELSE NULL END,
            CASE
                WHEN n % 11 = 0 THEN 'Hypertension'
                WHEN n % 13 = 0 THEN 'Type 2 Diabetes'
                WHEN n % 17 = 0 THEN 'Asthma'
                ELSE 'General'
            END,
            CASE WHEN n % 9 = 0 THEN ROUND((n % 7) * 125.75, 2) ELSE 0.0 END
        FROM seq;
    `, 'Seed core actors and customers');
}

async function importMedicinesFromSource(db) {
    if (!fs.existsSync(sourceDbPath)) {
        console.warn('Source medimanage.db not found. Falling back to synthetic catalog.');
        await seedFallbackMedicines(db);
        return;
    }

    console.log('2. Importing full medicine catalog from source database...');
    await exec(db, `ATTACH DATABASE '${escapeSqlString(sourceDbPath)}' AS source;`, 'Attach source DB');
    await exec(db, `
        INSERT INTO medicines (medicine_id, name, generic_name, company, price, expiry_date, active)
        SELECT
            medicine_id,
            name,
            COALESCE(NULLIF(TRIM(generic_name), ''), 'General Composition'),
            COALESCE(NULLIF(TRIM(company), ''), 'General Pharma'),
            CASE
                WHEN COALESCE(price, 0) > 0 THEN ROUND(price, 2)
                ELSE ROUND(12 + (ABS(medicine_id * 17) % 900) / 10.0, 2)
            END,
            DATE('now', '+' || (180 + ABS(medicine_id % 360)) || ' days'),
            1
        FROM source.medicines
        WHERE COALESCE(active, 1) = 1
        ORDER BY medicine_id;
    `, 'Import medicines');
    await exec(db, 'DETACH DATABASE source;', 'Detach source DB');
}

async function seedFallbackMedicines(db) {
    await exec(db, `
        WITH RECURSIVE seq(n) AS (
            SELECT 1
            UNION ALL
            SELECT n + 1 FROM seq WHERE n < 2500
        )
        INSERT INTO medicines (medicine_id, name, generic_name, company, price, expiry_date, active)
        SELECT
            n,
            'Demo Medicine ' || printf('%05d', n),
            'Demo Generic ' || ((n % 400) + 1),
            'Fallback Pharma ' || ((n % 20) + 1),
            ROUND(15 + (n % 500) / 5.0, 2),
            DATE('now', '+' || (120 + (n % 365)) || ' days'),
            1
        FROM seq;
    `, 'Seed fallback medicines');
}

async function seedFeatureMasters(db) {
    console.log('3. Seeding settings, suppliers, templates, and locations...');
    await exec(db, `
        INSERT OR REPLACE INTO receipt_settings (
            setting_id,
            pharmacy_name,
            address_line1,
            address_line2,
            phone,
            email,
            gst_number,
            logo_path,
            footer_text,
            show_barcode_on_receipt
        ) VALUES (
            1,
            'MediManage Demo Pharmacy',
            '123 Health Street',
            'Medical Colony, Bengaluru - 560001',
            '+91 80 2345 6789',
            'contact@medimanage.in',
            '29AABCM1234F1Z5',
            NULL,
            'Demo dataset loaded. Verify stock, expiry, purchases, billing, and alerts before going live.',
            1
        );

        INSERT OR REPLACE INTO message_templates (template_key, subject, body_template) VALUES
            ('whatsapp_invoice', NULL, 'Thank you for shopping with MediManage. Invoice total: ₹{{totalAmount}} for {{customerName}}.'),
            ('email_invoice_subject', 'MediManage Invoice {{billId}}', 'MediManage Invoice {{billId}}'),
            ('email_invoice_body', NULL, 'Hello {{customerName}}, your MediManage invoice total is ₹{{totalAmount}}. Please find the attached document.');

        INSERT INTO suppliers (supplier_id, name, contact_person, phone, email, address, gst_number, active) VALUES
            (1, 'Sun Pharmaceutical Industries', 'Rajesh Kumar', '+91 22 4324 4324', 'orders@sunpharma.com', 'Mumbai, Maharashtra', '27AAACS1234F1ZP', 1),
            (2, 'Cipla Limited', 'Priya Sharma', '+91 22 2482 6000', 'supply@cipla.com', 'Mumbai, Maharashtra', '27AABCC5678G1ZQ', 1),
            (3, 'Dr. Reddy''s Laboratories', 'Anil Reddy', '+91 40 4900 2900', 'procurement@drreddys.com', 'Hyderabad, Telangana', '36AABCD1234H1ZR', 1),
            (4, 'Lupin Limited', 'Meena Patel', '+91 22 6640 2323', 'orders@lupin.com', 'Mumbai, Maharashtra', '27AABCL9876J1ZS', 1),
            (5, 'Aurobindo Pharma', 'Vijay Nair', '+91 40 6672 5000', 'sales@aurobindo.com', 'Hyderabad, Telangana', '36AABCA2345K1ZT', 1),
            (6, 'Zydus Lifesciences', 'Sneha Desai', '+91 79 2686 8100', 'supply@zyduslife.com', 'Ahmedabad, Gujarat', '24AABCZ5678L1ZU', 1),
            (7, 'Mankind Pharma', 'Ravi Gupta', '+91 11 4905 5055', 'orders@mankindpharma.com', 'New Delhi', '07AABCM3456M1ZV', 1),
            (8, 'Torrent Pharmaceuticals', 'Anita Joshi', '+91 79 2658 5090', 'supply@torrentpharma.com', 'Ahmedabad, Gujarat', '24AABCT4567N1ZW', 1),
            (9, 'Glenmark Pharmaceuticals', 'Sameer Khan', '+91 22 4018 9999', 'orders@glenmarkpharma.com', 'Mumbai, Maharashtra', '27AABCG6789P1ZX', 1),
            (10, 'Alkem Laboratories', 'Deepak Tiwari', '+91 22 3982 8888', 'supply@alkemlabs.com', 'Mumbai, Maharashtra', '27AABCA7890Q1ZY', 1),
            (11, 'Biocon Limited', 'Kavitha Rao', '+91 80 2808 2808', 'pharma@biocon.com', 'Bengaluru, Karnataka', '29AABCB1234R1ZZ', 1),
            (12, 'Cadila Healthcare', 'Nitin Shah', '+91 79 2685 6001', 'sales@cadila.com', 'Ahmedabad, Gujarat', '24AABCC2345S1ZA', 1);

        INSERT INTO locations (location_id, name, address, phone, location_type) VALUES
            (1, 'Main Store — Koramangala', '45, 1st Cross, Koramangala 4th Block, Bengaluru 560034', '+91 80 2345 6789', 'PHARMACY'),
            (2, 'Branch — Indiranagar', '12, 100 Ft Road, Indiranagar, Bengaluru 560038', '+91 80 2567 8901', 'PHARMACY'),
            (3, 'Branch — Whitefield', '78, ITPL Main Rd, Whitefield, Bengaluru 560066', '+91 80 2789 0123', 'PHARMACY'),
            (4, 'Central Warehouse', 'Plot 22, Industrial Area, Peenya, Bengaluru 560058', '+91 80 2890 1234', 'WAREHOUSE'),
            (5, 'Clinic Dispensary — JP Nagar', '34, 15th Cross, JP Nagar Phase 1, Bengaluru 560078', '+91 80 2901 2345', 'CLINIC');
    `, 'Seed feature masters');
}

async function seedAttendance(db) {
    console.log('6a. Seeding staff attendance history...');
    const patterns = [
        { userId: 1, startHour: 9, endHour: 18, note: null },
        { userId: 3, startHour: 8, endHour: 17, note: null },
        { userId: 4, startHour: 8, endHour: 16, note: null },
        { userId: 5, startHour: 10, endHour: 19, note: null }
    ];

    await exec(db, 'BEGIN TRANSACTION;', 'Begin attendance seed');
    try {
        for (let dayOffset = 0; dayOffset < 21; dayOffset += 1) {
            const dateValue = new Date();
            dateValue.setDate(dateValue.getDate() - dayOffset);
            const dateStr = dateValue.toISOString().slice(0, 10);
            for (const pattern of patterns) {
                const isSunday = new Date(dateValue).getDay() === 0;
                const startHour = isSunday ? pattern.startHour : pattern.startHour + ((dayOffset + pattern.userId) % 2);
                const endHour = isSunday ? pattern.startHour + 4 : pattern.endHour - ((dayOffset + pattern.userId) % 2);
                const checkIn = `${dateStr} ${String(startHour).padStart(2, '0')}:${String((pattern.userId * 7 + dayOffset) % 60).padStart(2, '0')}:00`;
                const checkOut = dayOffset === 0 && pattern.userId !== 1
                    ? null
                    : `${dateStr} ${String(endHour).padStart(2, '0')}:${String((pattern.userId * 11 + dayOffset) % 60).padStart(2, '0')}:00`;
                const totalHours = checkOut === null ? null : Math.max(4, endHour - startHour);
                const notes = dayOffset === 0 && checkOut === null
                    ? 'Currently on shift'
                    : (isSunday ? 'Weekend / half day shift' : pattern.note);
                await run(
                    db,
                    `INSERT INTO employee_attendance (user_id, check_in_time, check_out_time, date, total_hours, notes)
                     VALUES (?, ?, ?, ?, ?, ?)`,
                    [pattern.userId, checkIn, checkOut, dateStr, totalHours, notes]
                );
            }
        }
        await exec(db, 'COMMIT;', 'Commit attendance seed');
    } catch (error) {
        await exec(db, 'ROLLBACK;', 'Rollback attendance seed');
        throw error;
    }
}

async function seedPurchaseOrdersAndItems(db) {
    console.log('6b. Seeding purchase order history...');
    const medicines = await all(
        db,
        `SELECT medicine_id, name, generic_name, company, price, purchase_price, reorder_threshold, expiry_date, supplier_id
         FROM medicines
         WHERE active = 1
         ORDER BY medicine_id
         LIMIT 480`
    );
    const supplierBuckets = new Map();
    for (const medicine of medicines) {
        if (!supplierBuckets.has(medicine.supplier_id)) {
            supplierBuckets.set(medicine.supplier_id, []);
        }
        supplierBuckets.get(medicine.supplier_id).push(medicine);
    }

    const statuses = ['RECEIVED', 'RECEIVED', 'PARTIAL', 'ORDERED', 'DRAFT', 'RECEIVED', 'ORDERED', 'PARTIAL', 'RECEIVED', 'CANCELLED', 'ORDERED', 'DRAFT'];
    await exec(db, 'BEGIN TRANSACTION;', 'Begin purchase order seed');
    try {
        for (let index = 0; index < 12; index += 1) {
            const supplierId = (index % SUPPLIER_COUNT) + 1;
            const pool = supplierBuckets.get(supplierId) || medicines;
            const orderDateValue = new Date();
            orderDateValue.setDate(orderDateValue.getDate() - (42 - index * 3));
            const expectedValue = new Date(orderDateValue);
            expectedValue.setDate(expectedValue.getDate() + 6);
            const orderDate = `${orderDateValue.toISOString().slice(0, 10)} ${String(8 + (index % 4)).padStart(2, '0')}:15:00`;
            const expectedDate = expectedValue.toISOString().slice(0, 10);
            const status = statuses[index];
            const createdByUserId = index % 2 === 0 ? 3 : 1;
            const poResult = await run(
                db,
                `INSERT INTO purchase_orders (supplier_id, order_date, expected_delivery, status, total_amount, notes, created_by_user_id)
                 VALUES (?, ?, ?, ?, 0.0, ?, ?)`,
                [
                    supplierId,
                    orderDate,
                    expectedDate,
                    status,
                    `Demo PO ${index + 1} for replenishment, expiry rotation, and supplier analytics.`,
                    createdByUserId
                ]
            );
            const poId = poResult.lastID;
            let totalAmount = 0;
            for (let itemIndex = 0; itemIndex < 8; itemIndex += 1) {
                const medicine = pool[(index * 8 + itemIndex) % pool.length];
                const orderedQty = 20 + ((index + 1) * (itemIndex + 2) % 90);
                const receivedQty = status === 'RECEIVED'
                    ? orderedQty
                    : (status === 'PARTIAL' ? Math.max(1, Math.floor(orderedQty * 0.55)) : 0);
                const unitCost = Number((medicine.purchase_price || (medicine.price * 0.6)).toFixed(2));
                const sellingPrice = Number((medicine.price || (unitCost * 1.4)).toFixed(2));
                const batchNumber = `PO${String(poId).padStart(3, '0')}-${String(itemIndex + 1).padStart(2, '0')}`;
                totalAmount += receivedQty * unitCost;
                await run(
                    db,
                    `INSERT INTO purchase_order_items (
                        po_id, medicine_id, medicine_name_snapshot, generic_name_snapshot, company_snapshot,
                        batch_number, expiry_date, purchase_date, ordered_qty, received_qty,
                        unit_cost, selling_price, reorder_threshold
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
                    [
                        poId,
                        medicine.medicine_id,
                        medicine.name,
                        medicine.generic_name,
                        medicine.company,
                        batchNumber,
                        medicine.expiry_date,
                        orderDateValue.toISOString().slice(0, 10),
                        orderedQty,
                        receivedQty,
                        unitCost,
                        sellingPrice,
                        medicine.reorder_threshold
                    ]
                );
            }
            await run(db, 'UPDATE purchase_orders SET total_amount = ? WHERE po_id = ?', [Number(totalAmount.toFixed(2)), poId]);
        }
        await exec(db, 'COMMIT;', 'Commit purchase order seed');
    } catch (error) {
        await exec(db, 'ROLLBACK;', 'Rollback purchase order seed');
        throw error;
    }
}

async function seedHeldOrders(db) {
    console.log('6c. Seeding held orders...');
    const medicines = await all(
        db,
        `SELECT medicine_id, name, price
         FROM medicines
         WHERE active = 1
         ORDER BY medicine_id
         LIMIT 24`
    );
    await exec(db, 'BEGIN TRANSACTION;', 'Begin held order seed');
    try {
        for (let index = 0; index < 6; index += 1) {
            const picked = medicines.slice(index * 3, index * 3 + 3);
            const items = picked.map((medicine, offset) => ({
                medicineId: medicine.medicine_id,
                name: medicine.name,
                qty: 1 + ((index + offset) % 4),
                price: Number(medicine.price.toFixed(2))
            }));
            const total = items.reduce((sum, item) => sum + (item.qty * item.price), 0);
            const heldAt = new Date();
            heldAt.setDate(heldAt.getDate() - index);
            const status = index < 3 ? 'HELD' : (index === 3 ? 'RECALLED' : 'CANCELLED');
            const recalledAt = status === 'RECALLED' ? new Date(heldAt.getTime() + 3 * 60 * 60 * 1000).toISOString().slice(0, 19).replace('T', ' ') : null;
            await run(
                db,
                `INSERT INTO held_orders (customer_id, user_id, items_json, total_amount, notes, status, held_at, recalled_at)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
                [
                    index + 1,
                    5,
                    JSON.stringify(items),
                    Number(total.toFixed(2)),
                    'Demo hold order for recall / reservation workflow.',
                    status,
                    heldAt.toISOString().slice(0, 19).replace('T', ' '),
                    recalledAt
                ]
            );
        }
        await exec(db, 'COMMIT;', 'Commit held order seed');
    } catch (error) {
        await exec(db, 'ROLLBACK;', 'Rollback held order seed');
        throw error;
    }
}

async function seedExpensesAndPrescriptions(db) {
    console.log('6d. Seeding expenses and prescriptions...');
    const medicines = await all(
        db,
        `SELECT name FROM medicines WHERE active = 1 ORDER BY medicine_id LIMIT 40`
    );
    const expenses = [
        ['Rent', 35000.0, 40, 'Monthly shop rent'],
        ['Electricity', 8200.0, 35, 'Power and refrigeration load'],
        ['Staff Salary', 91000.0, 32, 'Monthly payroll'],
        ['Maintenance', 4200.0, 28, 'AC and shelf maintenance'],
        ['Internet', 1600.0, 24, 'Broadband and phone bill'],
        ['Insurance', 12500.0, 21, 'Business insurance'],
        ['Delivery', 5800.0, 17, 'Home-delivery operations'],
        ['Packaging', 2600.0, 14, 'Bags, labels, and receipt rolls'],
        ['Marketing', 4300.0, 9, 'Local outreach campaign'],
        ['Miscellaneous', 1800.0, 4, 'General operating supplies']
    ];

    await exec(db, 'BEGIN TRANSACTION;', 'Begin expense and prescription seed');
    try {
        for (const [category, amount, dayOffset, description] of expenses) {
            const dateValue = new Date();
            dateValue.setDate(dateValue.getDate() - dayOffset);
            await run(
                db,
                'INSERT INTO expenses (category, amount, date, description) VALUES (?, ?, ?, ?)',
                [category, amount, dateValue.toISOString().slice(0, 10), description]
            );
        }

        for (let index = 0; index < 12; index += 1) {
            const medA = medicines[(index * 2) % medicines.length];
            const medB = medicines[(index * 2 + 1) % medicines.length];
            const prescribedDate = new Date();
            prescribedDate.setDate(prescribedDate.getDate() - (12 - index));
            await run(
                db,
                `INSERT INTO prescriptions (
                    customer_id, customer_name, doctor_name, status, prescribed_date, notes, medicines_text, ai_validation
                 ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
                [
                    (index % 12) + 1,
                    index < 10 ? ['Ram Kumar', 'Vikash Rao', 'Asha Menon', 'Sunita Iyer', 'Harish Patel', 'Geetha Nair', 'Mahesh Gowda', 'Pooja Sharma', 'Deepa Joshi', 'Farhan Ali'][index] : `Customer ${String(index + 1).padStart(3, '0')}`,
                    ['Dr. Anand Sharma', 'Dr. Priya Nair', 'Dr. Ravi Kumar'][index % 3],
                    index < 7 ? 'DISPENSED' : 'PENDING',
                    prescribedDate.toISOString().slice(0, 19).replace('T', ' '),
                    'Demo prescription for doctor workflow and AI validation.',
                    `${medA.name} - 1 tab twice daily, ${medB.name} - 1 tab after food`,
                    index % 2 === 0 ? 'AI verified: no interaction risk detected.' : 'AI review suggested counselling for adherence.'
                ]
            );
        }
        await exec(db, 'COMMIT;', 'Commit expense and prescription seed');
    } catch (error) {
        await exec(db, 'ROLLBACK;', 'Rollback expense and prescription seed');
        throw error;
    }
}

async function seedSalesHistory(db) {
    console.log('6e. Seeding billing history...');
    const medicines = await all(
        db,
        `SELECT m.medicine_id, m.name, m.price, m.purchase_price
         FROM medicines m
         JOIN stock s ON s.medicine_id = m.medicine_id
         WHERE m.active = 1 AND s.quantity > 0
         ORDER BY m.medicine_id
         LIMIT 900`
    );

    await exec(db, 'BEGIN TRANSACTION;', 'Begin billing history seed');
    try {
        for (let index = 0; index < 90; index += 1) {
            const billDate = new Date();
            billDate.setDate(billDate.getDate() - (45 - Math.floor(index / 2)));
            billDate.setHours(9 + (index % 8), 10 + ((index * 7) % 40), 0, 0);
            const paymentMode = ['CASH', 'UPI', 'CARD', 'UPI', 'CASH'][index % 5];
            const customerId = index % 6 === 0 ? null : ((index % 20) + 1);
            const userId = index % 3 === 0 ? 5 : (index % 3 === 1 ? 4 : 1);
            const locationId = (index % 3) + 1;
            const billResult = await run(
                db,
                `INSERT INTO bills (customer_id, user_id, total_amount, bill_date, payment_mode, ai_care_protocol, location_id)
                 VALUES (?, ?, 0.0, ?, ?, ?, ?)`,
                [
                    customerId,
                    userId,
                    billDate.toISOString().slice(0, 19).replace('T', ' '),
                    paymentMode,
                    index % 7 === 0 ? 'Maintain hydration, complete the full medicine course, and seek review if symptoms persist.' : null,
                    locationId
                ]
            );
            const billId = billResult.lastID;
            const itemCount = 2 + (index % 3);
            let totalAmount = 0;
            for (let itemIndex = 0; itemIndex < itemCount; itemIndex += 1) {
                const medicine = medicines[(index * 5 + itemIndex * 11) % medicines.length];
                const quantity = 1 + ((index + itemIndex) % 4);
                const price = Number(medicine.price.toFixed(2));
                const total = Number((quantity * price).toFixed(2));
                const cogs = Number((quantity * medicine.purchase_price).toFixed(2));
                totalAmount += total;
                const itemResult = await run(
                    db,
                    `INSERT INTO bill_items (bill_id, medicine_id, quantity, price, total, cost_of_goods)
                     VALUES (?, ?, ?, ?, ?, ?)`,
                    [billId, medicine.medicine_id, quantity, price, total, cogs]
                );
                const batchRow = await get(
                    db,
                    `SELECT batch_id, batch_number, expiry_date, unit_cost, selling_price
                     FROM inventory_batches
                     WHERE medicine_id = ?
                     ORDER BY expiry_date ASC, batch_id ASC
                     LIMIT 1`,
                    [medicine.medicine_id]
                );
                if (batchRow) {
                    await run(
                        db,
                        `INSERT INTO bill_item_batch_allocations (
                            bill_id, bill_item_id, medicine_id, batch_id, batch_number, expiry_date, quantity, unit_cost, selling_price
                         ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
                        [
                            billId,
                            itemResult.lastID,
                            medicine.medicine_id,
                            batchRow.batch_id,
                            batchRow.batch_number,
                            batchRow.expiry_date,
                            quantity,
                            batchRow.unit_cost,
                            batchRow.selling_price
                        ]
                    );
                }
            }
            await run(db, 'UPDATE bills SET total_amount = ? WHERE bill_id = ?', [Number(totalAmount.toFixed(2)), billId]);
        }
        await exec(db, 'COMMIT;', 'Commit billing history seed');
    } catch (error) {
        await exec(db, 'ROLLBACK;', 'Rollback billing history seed');
        throw error;
    }
}

async function normalizeMedicineCatalog(db) {
    console.log('4. Normalizing catalog pricing, suppliers, and barcodes...');
    await exec(db, `
        UPDATE medicines
        SET company = CASE
                WHEN company IS NULL OR TRIM(company) = '' THEN 'General Pharma'
                ELSE company
            END,
            generic_name = CASE
                WHEN generic_name IS NULL OR TRIM(generic_name) = '' THEN 'General Composition ' || printf('%04d', (medicine_id % 1000) + 1)
                ELSE generic_name
            END;

        UPDATE medicines
        SET price = CASE
                WHEN COALESCE(price, 0) > 0 THEN ROUND(price, 2)
                ELSE ROUND(12 + (ABS(medicine_id * 17) % 900) / 10.0, 2)
            END,
            supplier_id = ((ABS(medicine_id * 17) % ${SUPPLIER_COUNT}) + 1),
            reorder_threshold = 8 + (ABS(medicine_id * 13) % 18),
            barcode = CASE
                WHEN barcode IS NULL OR TRIM(barcode) = '' THEN 'MED' || printf('%08d', medicine_id)
                ELSE barcode
            END;

        UPDATE medicines
        SET purchase_price = ROUND(
                CASE
                    WHEN COALESCE(purchase_price, 0) > 0 AND purchase_price < price THEN purchase_price
                    ELSE price * (0.52 + ((ABS(medicine_id * 7) % 18) / 100.0))
                END,
                2
            );
    `, 'Normalize medicine catalog');
}

async function buildStockProfiles(db) {
    console.log('5. Building stock, expiry, and FEFO batch profiles...');
    await exec(db, `
        DELETE FROM bill_item_batch_allocations;
        DELETE FROM inventory_batches;
        DELETE FROM stock;

        DROP TABLE IF EXISTS medicine_demo_profile;
        CREATE TEMP TABLE medicine_demo_profile AS
        SELECT
            medicine_id,
            CASE
                WHEN ABS((medicine_id * 37) % 1000) < 900 THEN 'HEALTHY'
                WHEN ABS((medicine_id * 37) % 1000) < 950 THEN 'OUT_OF_STOCK'
                WHEN ABS((medicine_id * 37) % 1000) < 980 THEN 'NEAR_EXPIRY'
                ELSE 'EXPIRED'
            END AS demo_segment,
            CASE
                WHEN ABS((medicine_id * 37) % 1000) < 900 THEN 18 + ABS((medicine_id * 11) % 160)
                WHEN ABS((medicine_id * 37) % 1000) < 950 THEN 0
                WHEN ABS((medicine_id * 37) % 1000) < 980 THEN 4 + ABS((medicine_id * 7) % 18)
                ELSE 2 + ABS((medicine_id * 5) % 10)
            END AS target_stock,
            CASE
                WHEN ABS((medicine_id * 37) % 1000) < 900 THEN DATE('now', '+' || (120 + ABS(medicine_id % 540)) || ' days')
                WHEN ABS((medicine_id * 37) % 1000) < 950 THEN DATE('now', '+' || (45 + ABS(medicine_id % 120)) || ' days')
                WHEN ABS((medicine_id * 37) % 1000) < 980 THEN DATE('now', '+' || (1 + ABS(medicine_id % 25)) || ' days')
                ELSE DATE('now', '-' || (1 + ABS(medicine_id % 45)) || ' days')
            END AS master_expiry,
            CASE
                WHEN ABS((medicine_id * 37) % 1000) < 900
                     AND (18 + ABS((medicine_id * 11) % 160)) >= 30
                     AND ABS((medicine_id * 19) % 100) < 18
                THEN 1
                ELSE 0
            END AS has_second_batch
        FROM medicines
        WHERE active = 1;

        UPDATE medicines
        SET expiry_date = (
            SELECT p.master_expiry
            FROM medicine_demo_profile p
            WHERE p.medicine_id = medicines.medicine_id
        )
        WHERE medicine_id IN (SELECT medicine_id FROM medicine_demo_profile);

        INSERT INTO stock (medicine_id, quantity)
        SELECT medicine_id, target_stock
        FROM medicine_demo_profile;

        DROP TABLE IF EXISTS medicine_batch_plan;
        CREATE TEMP TABLE medicine_batch_plan AS
        SELECT
            p.medicine_id,
            p.demo_segment,
            p.target_stock,
            p.master_expiry,
            p.has_second_batch,
            CASE
                WHEN p.has_second_batch = 1 THEN CAST(ROUND(p.target_stock * 0.62) AS INTEGER)
                ELSE p.target_stock
            END AS first_qty,
            CASE
                WHEN p.has_second_batch = 1 THEN p.target_stock - CAST(ROUND(p.target_stock * 0.62) AS INTEGER)
                ELSE 0
            END AS second_qty
        FROM medicine_demo_profile p;

        INSERT INTO inventory_batches (
            medicine_id,
            batch_number,
            batch_barcode,
            expiry_date,
            purchase_date,
            unit_cost,
            selling_price,
            initial_quantity,
            available_quantity,
            supplier_id
        )
        SELECT
            p.medicine_id,
            'B1-' || printf('%08d', p.medicine_id),
            'BT-' || p.medicine_id || '-B1',
            CASE
                WHEN p.has_second_batch = 1 THEN DATE(p.master_expiry, '-' || (30 + ABS(p.medicine_id % 75)) || ' days')
                ELSE p.master_expiry
            END,
            DATE('now', '-' || (20 + ABS(p.medicine_id % 180)) || ' days'),
            ROUND(m.purchase_price, 2),
            ROUND(m.price, 2),
            p.first_qty,
            p.first_qty,
            m.supplier_id
        FROM medicine_batch_plan p
        JOIN medicines m ON m.medicine_id = p.medicine_id
        WHERE p.first_qty > 0;

        INSERT INTO inventory_batches (
            medicine_id,
            batch_number,
            batch_barcode,
            expiry_date,
            purchase_date,
            unit_cost,
            selling_price,
            initial_quantity,
            available_quantity,
            supplier_id
        )
        SELECT
            p.medicine_id,
            'B2-' || printf('%08d', p.medicine_id),
            'BT-' || p.medicine_id || '-B2',
            p.master_expiry,
            DATE('now', '-' || (5 + ABS(p.medicine_id % 60)) || ' days'),
            ROUND(m.purchase_price * 1.02, 2),
            ROUND(m.price, 2),
            p.second_qty,
            p.second_qty,
            m.supplier_id
        FROM medicine_batch_plan p
        JOIN medicines m ON m.medicine_id = p.medicine_id
        WHERE p.second_qty > 0;

        UPDATE stock
        SET quantity = COALESCE((
            SELECT SUM(available_quantity)
            FROM inventory_batches
            WHERE inventory_batches.medicine_id = stock.medicine_id
        ), 0);
    `, 'Build stock profiles');
}

async function enrichLocations(db) {
    console.log('6. Expanding location stock and transfer history...');
    await exec(db, `
        INSERT OR IGNORE INTO location_stock (location_id, medicine_id, quantity, min_stock)
        SELECT
            1,
            p.medicine_id,
            MAX(2, CAST(p.target_stock * 0.28 AS INTEGER)),
            MAX(5, CAST((m.reorder_threshold * 0.6) AS INTEGER))
        FROM medicine_demo_profile p
        JOIN medicines m ON m.medicine_id = p.medicine_id
        WHERE p.target_stock > 0 AND p.medicine_id % 29 = 0
        LIMIT 250;

        INSERT OR IGNORE INTO location_stock (location_id, medicine_id, quantity, min_stock)
        SELECT
            2,
            p.medicine_id,
            MAX(1, CAST(p.target_stock * 0.16 AS INTEGER)),
            MAX(4, CAST((m.reorder_threshold * 0.5) AS INTEGER))
        FROM medicine_demo_profile p
        JOIN medicines m ON m.medicine_id = p.medicine_id
        WHERE p.target_stock > 0 AND p.medicine_id % 31 = 0
        LIMIT 220;

        INSERT OR IGNORE INTO location_stock (location_id, medicine_id, quantity, min_stock)
        SELECT
            3,
            p.medicine_id,
            MAX(1, CAST(p.target_stock * 0.14 AS INTEGER)),
            MAX(4, CAST((m.reorder_threshold * 0.5) AS INTEGER))
        FROM medicine_demo_profile p
        JOIN medicines m ON m.medicine_id = p.medicine_id
        WHERE p.target_stock > 0 AND p.medicine_id % 37 = 0
        LIMIT 220;

        INSERT OR IGNORE INTO location_stock (location_id, medicine_id, quantity, min_stock)
        SELECT
            4,
            p.medicine_id,
            MAX(10, CAST(p.target_stock * 1.8 AS INTEGER)),
            MAX(20, CAST((m.reorder_threshold * 2.5) AS INTEGER))
        FROM medicine_demo_profile p
        JOIN medicines m ON m.medicine_id = p.medicine_id
        WHERE p.demo_segment != 'OUT_OF_STOCK' AND p.medicine_id % 23 = 0
        LIMIT 320;

        INSERT OR IGNORE INTO location_stock (location_id, medicine_id, quantity, min_stock)
        SELECT
            5,
            p.medicine_id,
            MAX(1, CAST(p.target_stock * 0.08 AS INTEGER)),
            MAX(3, CAST((m.reorder_threshold * 0.4) AS INTEGER))
        FROM medicine_demo_profile p
        JOIN medicines m ON m.medicine_id = p.medicine_id
        WHERE p.target_stock > 0 AND p.medicine_id % 41 = 0
        LIMIT 180;

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
        SELECT
            4,
            CASE
                WHEN p.medicine_id % 3 = 0 THEN 1
                WHEN p.medicine_id % 3 = 1 THEN 2
                ELSE 3
            END,
            p.medicine_id,
            5 + ABS(p.medicine_id % 18),
            CASE
                WHEN p.medicine_id % 5 = 0 THEN 'PENDING'
                WHEN p.medicine_id % 5 = 1 THEN 'IN_TRANSIT'
                ELSE 'COMPLETED'
            END,
            CASE WHEN p.medicine_id % 2 = 0 THEN 3 ELSE 1 END,
            DATETIME('now', '-' || (3 + ABS(p.medicine_id % 20)) || ' days'),
            CASE
                WHEN p.medicine_id % 5 IN (0, 1) THEN NULL
                ELSE DATETIME('now', '-' || (1 + ABS(p.medicine_id % 10)) || ' days')
            END
        FROM medicine_demo_profile p
        WHERE p.target_stock > 0 AND p.medicine_id % 211 = 0
        LIMIT 24;
    `, 'Enrich locations');
}

async function seedInventoryAdjustments(db) {
    console.log('7. Seeding return, damage, and expired-dump adjustments...');
    await exec(db, `
        DROP TABLE IF EXISTS demo_adjustments;
        CREATE TEMP TABLE demo_adjustments AS
        SELECT
            batch_id,
            medicine_id,
            CASE
                WHEN available_quantity >= 8 THEN 3
                WHEN available_quantity >= 5 THEN 2
                ELSE 1
            END AS adjustment_qty,
            unit_cost,
            CASE
                WHEN expiry_date < DATE('now') THEN 'DAMAGED'
                WHEN medicine_id % 2 = 0 THEN 'RETURN'
                ELSE 'DAMAGED'
            END AS adjustment_type,
            CASE
                WHEN expiry_date < DATE('now') THEN 'Expired Dump'
                WHEN medicine_id % 2 = 0 THEN 'Supplier Return'
                ELSE 'Packaging Damage'
            END AS root_cause_tag,
            CASE
                WHEN expiry_date < DATE('now') THEN 'Disposed expired stock during demo housekeeping.'
                WHEN medicine_id % 2 = 0 THEN 'Return-to-supplier demo transaction.'
                ELSE 'Damaged carton found during shelf audit.'
            END AS notes,
            CASE
                WHEN expiry_date < DATE('now') THEN 4
                WHEN medicine_id % 2 = 0 THEN 3
                ELSE 2
            END AS created_by_user_id
        FROM inventory_batches
        WHERE available_quantity > 2
          AND (
              (expiry_date < DATE('now') AND medicine_id % 149 = 0)
              OR (expiry_date >= DATE('now') AND expiry_date <= DATE('now', '+20 days') AND medicine_id % 173 = 0)
              OR (expiry_date > DATE('now', '+120 days') AND medicine_id % 197 = 0)
          );

        INSERT INTO inventory_adjustments (
            medicine_id,
            adjustment_type,
            quantity,
            unit_price,
            root_cause_tag,
            notes,
            occurred_at,
            created_by_user_id,
            batch_id
        )
        SELECT
            medicine_id,
            adjustment_type,
            adjustment_qty,
            ROUND(unit_cost, 2),
            root_cause_tag,
            notes,
            DATETIME('now', '-' || (1 + ABS(medicine_id % 25)) || ' days'),
            created_by_user_id,
            batch_id
        FROM demo_adjustments;

        UPDATE inventory_batches
        SET available_quantity = available_quantity - COALESCE((
            SELECT adjustment_qty
            FROM demo_adjustments
            WHERE demo_adjustments.batch_id = inventory_batches.batch_id
        ), 0)
        WHERE batch_id IN (SELECT batch_id FROM demo_adjustments);
    `, 'Seed inventory adjustments');
}

async function reconcileStockFromBatches(db) {
    console.log('8. Reconciling stock and expiry snapshots from batch ledger...');
    await exec(db, `
        UPDATE stock
        SET quantity = COALESCE((
            SELECT SUM(available_quantity)
            FROM inventory_batches
            WHERE inventory_batches.medicine_id = stock.medicine_id
        ), 0);

        UPDATE medicines
        SET expiry_date = COALESCE((
            SELECT MIN(expiry_date)
            FROM inventory_batches
            WHERE inventory_batches.medicine_id = medicines.medicine_id
              AND available_quantity > 0
              AND expiry_date IS NOT NULL
              AND TRIM(expiry_date) <> ''
        ), medicines.expiry_date);
    `, 'Reconcile stock');
}

async function enrichBillingArtifacts(db) {
    console.log('9. Seeding payment splits and bill costing...');
    await exec(db, `
        UPDATE bill_items
        SET cost_of_goods = ROUND(
            quantity * COALESCE((
                SELECT purchase_price
                FROM medicines
                WHERE medicines.medicine_id = bill_items.medicine_id
            ), 0.0),
            2
        )
        WHERE COALESCE(cost_of_goods, 0.0) = 0.0;

        UPDATE bills
        SET total_amount = COALESCE((
            SELECT ROUND(SUM(total), 2)
            FROM bill_items
            WHERE bill_items.bill_id = bills.bill_id
        ), total_amount),
            ai_care_protocol = CASE
                WHEN bill_id % 7 = 0 THEN 'Hydrate well, complete the dosage course, and contact a doctor if symptoms worsen.'
                WHEN bill_id % 7 = 3 THEN 'Avoid skipping doses and track blood pressure / sugar readings as advised.'
                ELSE ai_care_protocol
            END;

        INSERT INTO payment_splits (bill_id, payment_method, amount, reference_number)
        SELECT
            bill_id,
            payment_mode,
            total_amount,
            CASE
                WHEN payment_mode IN ('UPI', 'CARD', 'CHEQUE') THEN payment_mode || '-' || printf('%06d', bill_id)
                ELSE NULL
            END
        FROM bills
        WHERE bill_id % 5 IN (0, 1, 2);

        INSERT INTO payment_splits (bill_id, payment_method, amount, reference_number)
        SELECT
            bill_id,
            'CASH',
            ROUND(total_amount * 0.40, 2),
            NULL
        FROM bills
        WHERE bill_id % 5 = 3;

        INSERT INTO payment_splits (bill_id, payment_method, amount, reference_number)
        SELECT
            bill_id,
            'UPI',
            ROUND(total_amount - ROUND(total_amount * 0.40, 2), 2),
            'UPI-' || printf('%06d', bill_id)
        FROM bills
        WHERE bill_id % 5 = 3;

        INSERT INTO payment_splits (bill_id, payment_method, amount, reference_number)
        SELECT
            bill_id,
            'CARD',
            ROUND(total_amount * 0.55, 2),
            'CARD-' || printf('%06d', bill_id)
        FROM bills
        WHERE bill_id % 5 = 4;

        INSERT INTO payment_splits (bill_id, payment_method, amount, reference_number)
        SELECT
            bill_id,
            'CASH',
            ROUND(total_amount - ROUND(total_amount * 0.55, 2), 2),
            NULL
        FROM bills
        WHERE bill_id % 5 = 4;
    `, 'Enrich billing artifacts');
}

async function optimizeAndSummarize(db) {
    console.log('10. Optimizing and summarizing demo database...');
    await exec(db, 'ANALYZE;', 'Analyze DB');
    await exec(db, 'VACUUM;', 'Vacuum DB');

    db.get(`
        SELECT
            (SELECT COUNT(*) FROM medicines WHERE active = 1) AS medicines,
            (SELECT COUNT(*) FROM stock WHERE quantity > 0) AS in_stock,
            (SELECT COUNT(*) FROM stock WHERE quantity = 0) AS out_of_stock,
            (SELECT COUNT(*) FROM medicines WHERE expiry_date > DATE('now') AND expiry_date <= DATE('now', '+30 days')) AS near_expiry,
            (SELECT COUNT(*) FROM medicines WHERE expiry_date < DATE('now')) AS expired,
            (SELECT COUNT(*) FROM inventory_batches WHERE available_quantity > 0) AS active_batches,
            (SELECT COUNT(*) FROM bills) AS bills,
            (SELECT COUNT(*) FROM purchase_orders) AS purchase_orders,
            (SELECT COUNT(*) FROM customers) AS customers,
            (SELECT COUNT(*) FROM stock_transfers) AS transfers
    `, (err, row) => {
        if (err) {
            console.warn('Summary query failed:', err.message);
            return;
        }
        console.log('Demo DB summary:', row);
    });
}

async function main() {
    console.log(`Generating packaged demo database: ${baseDbPath}`);
    if (fs.existsSync(baseDbPath)) {
        fs.unlinkSync(baseDbPath);
    }

    const db = openDatabase(baseDbPath);
    try {
        await exec(db, `
            PRAGMA foreign_keys = OFF;
            PRAGMA journal_mode = WAL;
            PRAGMA synchronous = NORMAL;
            PRAGMA temp_store = MEMORY;
        `, 'Configure SQLite');

        console.log('0. Initializing schema...');
        await execStatements(
            db,
            fs.readFileSync(schemaPath, 'utf8'),
            'Initialize schema',
            ['duplicate column name', 'already exists']
        );
        await seedCoreActorsAndCustomers(db);
        await importMedicinesFromSource(db);
        await seedFeatureMasters(db);
        await normalizeMedicineCatalog(db);
        await buildStockProfiles(db);
        await seedAttendance(db);
        await seedPurchaseOrdersAndItems(db);
        await seedHeldOrders(db);
        await seedExpensesAndPrescriptions(db);
        await seedSalesHistory(db);
        await enrichLocations(db);
        await seedInventoryAdjustments(db);
        await reconcileStockFromBatches(db);
        await enrichBillingArtifacts(db);
        await optimizeAndSummarize(db);
        console.log('Successfully generated base_medimanage.db with full feature demo data.');
    } catch (error) {
        console.error('Demo database generation failed:', error.message);
        process.exitCode = 1;
    } finally {
        await close(db);
    }
}

main().catch((error) => {
    console.error('Unexpected demo DB generation failure:', error);
    process.exit(1);
});
