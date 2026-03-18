const sqlite3 = require('sqlite3').verbose();
const path = require('path');
const fs = require('fs');

const dbPath = path.join(__dirname, '..', 'base_medimanage.db');
const sourceDbPath = path.join(__dirname, '..', 'medimanage.db');
const schemaPath = path.join(__dirname, '..', 'src', 'main', 'resources', 'db', 'schema.sql');

console.log(`Seeding database at: ${dbPath}`);
console.log(`Source for medicines: ${sourceDbPath}`);

if (!fs.existsSync(dbPath)) {
    fs.writeFileSync(dbPath, '');
}

const db = new sqlite3.Database(dbPath, (err) => {
    if (err) {
        console.error('Error opening database', err.message);
        process.exit(1);
    }
});

db.serialize(() => {
    // Disable FKs temporarily for faster/easier seeding
    db.run("PRAGMA foreign_keys = OFF;");
    db.run("PRAGMA synchronous = OFF;");
    db.run("PRAGMA journal_mode = MEMORY;");

    console.log('1. Initializing Schema...');
    const schemaSql = fs.readFileSync(schemaPath, 'utf8');
    schemaSql.split(';').map(s => s.trim()).filter(s => s.length > 0).forEach(stmt => {
        db.run(stmt, (err) => {
            if (err && !err.message.includes("already exists") && !err.message.includes("duplicate column")) {
                console.error('Schema Error:', err.message, '| Statement:', stmt.substring(0, 50));
            }
        });
    });

    console.log('2. Preparing tables...');
    const tablesToWipe = [
        'bill_items', 'bills', 'payment_splits', 'held_orders', 'inventory_adjustments',
        'purchase_order_items', 'purchase_orders', 'stock', 'medicines', 'suppliers',
        'customers', 'expenses', 'employee_attendance', 'prescriptions'
    ];
    tablesToWipe.forEach(t => db.run(`DELETE FROM ${t}`));
    db.run(`DELETE FROM sqlite_sequence`);

    db.run(`INSERT OR IGNORE INTO users (user_id, username, password, role) VALUES (1, 'admin', 'admin', 'ADMIN')`);
    db.run(`INSERT OR REPLACE INTO message_templates (template_key, subject, body_template) VALUES 
        ('whatsapp_invoice', NULL, 'Thank you for your purchase at MediManage! Your invoice total is $%.2f.'), 
        ('email_invoice_subject', 'Invoice from MediManage', 'Invoice from MediManage'),
        ('email_invoice_body', NULL, 'Thank you for your purchase at MediManage. Please find your invoice details inside.')`);

    console.log('3. Seeding Suppliers (10)...');
    const supplierData = [
        ['Apex Pharma', 'Raj'], ['Blue Horizon', 'Sarah'], ['Crestview', 'David'], ['Delta Life', 'Meera'],
        ['Elite Health', 'Kumar'], ['Fusion Drugs', 'Anita'], ['Global Med', 'Wilson'], ['HealWay', 'Priya'],
        ['Infinity', 'George'], ['Jade Pharma', 'Linda']
    ];
    supplierData.forEach(sup => {
        db.run(`INSERT INTO suppliers (name, contact_person) VALUES ('${sup[0]}', '${sup[1]}')`);
    });

    console.log('4. Importing 50% of medicines from medimanage.db...');
    if (fs.existsSync(sourceDbPath)) {
        db.run(`ATTACH DATABASE '${sourceDbPath}' AS source;`);
        // Map supplier_id to 1..10 to match our seeds
        db.run(`INSERT INTO medicines (name, generic_name, company, price, expiry_date, active, barcode, supplier_id, purchase_price, reorder_threshold)
                SELECT name, generic_name, company, price, expiry_date, active, barcode, (ABS(RANDOM()) % 10) + 1, purchase_price, reorder_threshold 
                FROM source.medicines 
                ORDER BY RANDOM() 
                LIMIT (SELECT COUNT(*) / 2 FROM source.medicines);`, function(err) {
                    if (err) console.error('Import Error:', err.message);
                    else console.log(`Imported ${this.changes} medicines.`);
                    db.run(`DETACH DATABASE source;`, () => seedRemainingData());
                });
    } else {
        console.warn('Source medimanage.db not found. Falling back to small set.');
        fallbackMedicines();
        seedRemainingData();
    }
});

function fallbackMedicines() {
    for (let i = 1; i <= 50; i++) {
        db.run(`INSERT INTO medicines (name, price, active) VALUES ('Fake Med ${i}', 10.0, 1)`);
    }
}

function seedRemainingData() {
    console.log('5. Seeding auxiliary data (Customers, Attendance)...');
    db.serialize(() => {
        db.run("BEGIN TRANSACTION;");
        for (let i = 1; i <= 20; i++) {
            db.run(`INSERT INTO customers (name, phone, current_balance) VALUES ('Customer ${i}', '555-00${i}', 0.0)`, (err) => {
                if (err) console.error('Customer Seed Error:', err.message);
            });
        }
        for(let i=0; i<30; i++) {
            const d = new Date(); d.setDate(d.getDate() - i);
            const dateStr = d.toISOString().substring(0, 10);
            db.run(`INSERT INTO employee_attendance (user_id, check_in_time, check_out_time, date, total_hours) VALUES (1, '${dateStr} 09:00:00', '${dateStr} 18:00:00', '${dateStr}', 9.0)`, (err) => {
                if (err) console.error('Attendance Seed Error:', err.message);
            });
        }
        db.run("COMMIT;", (err) => {
            if (err) console.error('Auxiliary Commit Error:', err.message);
            else {
                console.log('Auxiliary data seeded.');
                seedSampleTransactions();
            }
        });
    });
}

function seedSampleTransactions() {
    console.log('6. Seeding transactions (Stock, Bills)...');
    db.all("SELECT medicine_id FROM medicines LIMIT 500", (err, rows) => {
        if (err || !rows) { finish(); return; }
        const medIds = rows.map(r => r.medicine_id);
        
        db.serialize(() => {
            db.run("BEGIN TRANSACTION;");
            medIds.forEach(id => {
                if (Math.random() > 0.4) {
                    db.run(`INSERT INTO stock (medicine_id, quantity) VALUES (${id}, ${Math.floor(Math.random()*100)+5})`, (err) => {
                        if (err) console.error('Stock Seed Error:', err.message);
                    });
                }
            });

            for (let i = 1; i <= 30; i++) {
                const billId = i;
                db.run(`INSERT INTO bills (customer_id, user_id, total_amount, bill_date) VALUES (1, 1, 50.0, date('now','-${i} days'))`, (err) => {
                    if (err) console.error('Bill Seed Error:', err.message);
                });
                const m1 = medIds[Math.floor(Math.random()*medIds.length)];
                const m2 = medIds[Math.floor(Math.random()*medIds.length)];
                db.run(`INSERT INTO bill_items (bill_id, medicine_id, quantity, price, total) VALUES (${billId}, ${m1}, 1, 25.0, 25.0)`, (err) => {
                    if (err) console.error('Bill Item 1 Seed Error:', err.message);
                });
                db.run(`INSERT INTO bill_items (bill_id, medicine_id, quantity, price, total) VALUES (${billId}, ${m2}, 1, 25.0, 25.0)`, (err) => {
                    if (err) console.error('Bill Item 2 Seed Error:', err.message);
                });
            }
            db.run("COMMIT;", (err) => {
                if (err) console.error('Transaction Commit Error:', err.message);
                else {
                    // Re-enable FKs and clean up
                    db.run("PRAGMA foreign_keys = ON;");
                    finish();
                }
            });
        });
    });
}

function finish() {
    console.log('7. Optimizing database...');
    db.run("VACUUM;", () => {
        db.close(() => {
            console.log('Successfully generated base_medimanage.db with 50% production data.');
        });
    });
}
