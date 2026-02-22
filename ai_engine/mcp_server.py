"""
MediManage MCP Server
=====================
Exposes the MediManage pharmacy management system as MCP tools
that any AI host (Claude, Gemini, VS Code Copilot) can call.

Transport: STDIO (launched as a subprocess by the AI host)
Framework: FastMCP (mcp[cli])
Database:  Direct SQLite connection to medimanage.db

Usage:
  # Test with MCP Inspector
  mcp dev mcp_server.py

  # Install in Claude Desktop / Gemini CLI
  mcp install mcp_server.py
"""

import os
import sqlite3
import json
import logging
from datetime import datetime, timedelta
from contextlib import contextmanager
from typing import Optional

from mcp.server.fastmcp import FastMCP
from logging_setup import configure_structured_logging

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

_HERE = os.path.dirname(os.path.abspath(__file__))
_PROJECT_ROOT = os.path.dirname(_HERE)

# Database path — same SQLite file the Java app uses
DB_PATH = os.path.join(_PROJECT_ROOT, "medimanage.db")

# AI Engine server (Flask on port 5000)
AI_ENGINE_URL = "http://127.0.0.1:5000"

# Configure structured logging
configure_structured_logging(force=True)
logger = logging.getLogger("medimanage-mcp")

# ---------------------------------------------------------------------------
# FastMCP Server
# ---------------------------------------------------------------------------

mcp = FastMCP(
    "MediManage",
    instructions=(
        "MediManage Pharmacy Management -- search medicines, track inventory, "
        "manage customers, view sales, and query the local AI engine."
    ),
    host="127.0.0.1",
    port=5001,
)

# ---------------------------------------------------------------------------
# Database helpers
# ---------------------------------------------------------------------------

@contextmanager
def get_db():
    """Yield a read-only SQLite connection with Row factory."""
    conn = sqlite3.connect(f"file:{DB_PATH}?mode=ro", uri=True)
    conn.row_factory = sqlite3.Row
    try:
        yield conn
    finally:
        conn.close()


@contextmanager
def get_db_rw():
    """Yield a read-write SQLite connection for mutations."""
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    try:
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def rows_to_dicts(rows):
    """Convert sqlite3.Row objects to plain dicts."""
    return [dict(r) for r in rows]


def _table_exists(conn: sqlite3.Connection, table_name: str) -> bool:
    row = conn.execute(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name = ?",
        (table_name,),
    ).fetchone()
    return row is not None


def _table_columns(conn: sqlite3.Connection, table_name: str) -> set[str]:
    if not _table_exists(conn, table_name):
        return set()
    rows = conn.execute(f"PRAGMA table_info({table_name})").fetchall()
    return {r["name"] for r in rows}


def _resolve_prescription_schema(conn: sqlite3.Connection) -> dict[str, str]:
    """Resolve prescription table compatibility across legacy and current schemas."""
    cols = _table_columns(conn, "prescriptions")
    if not cols:
        raise RuntimeError("prescriptions table not found.")

    if "prescription_id" in cols:
        id_col = "prescription_id"
    elif "id" in cols:
        id_col = "id"
    else:
        raise RuntimeError("No prescription identifier column found.")

    date_col = None
    for candidate in ("prescribed_date", "created_at", "prescription_date"):
        if candidate in cols:
            date_col = candidate
            break
    if not date_col:
        raise RuntimeError("No prescription date column found.")

    customer_name_expr = "COALESCE(p.customer_name, '')" if "customer_name" in cols else "''"
    medicines_expr = "COALESCE(p.medicines_text, '')" if "medicines_text" in cols else "''"
    status_expr = "COALESCE(p.status, 'N/A')" if "status" in cols else "'N/A'"
    notes_expr = "COALESCE(p.notes, '')" if "notes" in cols else "''"
    ai_validation_expr = "COALESCE(p.ai_validation, 'Not validated')" if "ai_validation" in cols else "'Not validated'"

    customer_cols = _table_columns(conn, "customers")
    customer_pk = "customer_id" if "customer_id" in customer_cols else ("id" if "id" in customer_cols else None)
    if customer_pk and "customer_id" in cols and "phone" in customer_cols:
        phone_expr = (
            f"(SELECT c.phone FROM customers c "
            f"WHERE CAST(c.{customer_pk} AS TEXT) = CAST(p.customer_id AS TEXT) LIMIT 1)"
        )
    else:
        phone_expr = "'N/A'"

    doctor_cols = _table_columns(conn, "doctors")
    doctor_lookup_expr = None
    if "doctor_id" in cols and "id" in doctor_cols and "name" in doctor_cols:
        doctor_lookup_expr = (
            "(SELECT d.name FROM doctors d "
            "WHERE CAST(d.id AS TEXT) = CAST(p.doctor_id AS TEXT) LIMIT 1)"
        )

    if "doctor_name" in cols and doctor_lookup_expr:
        doctor_name_expr = f"COALESCE(p.doctor_name, {doctor_lookup_expr}, 'N/A')"
    elif "doctor_name" in cols:
        doctor_name_expr = "COALESCE(p.doctor_name, 'N/A')"
    elif doctor_lookup_expr:
        doctor_name_expr = f"COALESCE({doctor_lookup_expr}, 'N/A')"
    else:
        doctor_name_expr = "'N/A'"

    return {
        "id_col": id_col,
        "date_col": date_col,
        "customer_name_expr": customer_name_expr,
        "doctor_name_expr": doctor_name_expr,
        "medicines_expr": medicines_expr,
        "status_expr": status_expr,
        "notes_expr": notes_expr,
        "ai_validation_expr": ai_validation_expr,
        "phone_expr": phone_expr,
    }


def fmt_table(rows, max_rows: int = 50) -> str:
    """Format a list of dicts as a readable markdown table string."""
    if not rows:
        return "_No results found._"
    keys = list(rows[0].keys())
    truncated = rows[:max_rows]
    lines = ["| " + " | ".join(keys) + " |"]
    lines.append("| " + " | ".join(["---"] * len(keys)) + " |")
    for r in truncated:
        vals = [str(r.get(k, "")) for k in keys]
        lines.append("| " + " | ".join(vals) + " |")
    if len(rows) > max_rows:
        lines.append(f"\n_...showing {max_rows} of {len(rows)} results._")
    return "\n".join(lines)


# =========================================================================
#  RESOURCES
# =========================================================================

@mcp.resource("medimanage://schema")
def get_schema() -> str:
    """Full database schema (tables and columns) for MediManage."""
    with get_db() as conn:
        cur = conn.cursor()
        cur.execute("SELECT sql FROM sqlite_master WHERE type='table' ORDER BY name")
        stmts = [r[0] for r in cur.fetchall() if r[0]]
    return "\n\n".join(stmts)


@mcp.resource("medimanage://inventory/summary")
def inventory_summary_resource() -> str:
    """Current inventory KPI snapshot."""
    return json.dumps(_get_inventory_summary(), indent=2)


@mcp.resource("medimanage://sales/today")
def today_sales_resource() -> str:
    """Today's sales summary."""
    today = datetime.now().strftime("%Y-%m-%d")
    return json.dumps(_get_sales_for_date(today), indent=2)


# =========================================================================
#  TOOLS — Inventory
# =========================================================================

@mcp.tool()
def search_medicines(
    query: str,
    limit: int = 20,
) -> str:
    """Search medicines by name, generic name, or company.

    Args:
        query: Search term (matches name, generic_name, or company)
        limit: Maximum results to return (default 20)
    """
    with get_db() as conn:
        cur = conn.execute(
            """
            SELECT m.medicine_id, m.name, m.generic_name, m.company,
                   m.price, m.expiry_date,
                   COALESCE(s.quantity, 0) AS stock
            FROM medicines m
            LEFT JOIN stock s ON m.medicine_id = s.medicine_id
            WHERE m.name LIKE ? OR m.generic_name LIKE ? OR m.company LIKE ?
            ORDER BY m.name
            LIMIT ?
            """,
            (f"%{query}%", f"%{query}%", f"%{query}%", limit),
        )
        return fmt_table(rows_to_dicts(cur.fetchall()))


@mcp.tool()
def get_low_stock(threshold: int = 10) -> str:
    """List medicines below a minimum stock threshold.

    Args:
        threshold: Stock level below which a medicine is "low" (default 10)
    """
    with get_db() as conn:
        cur = conn.execute(
            """
            SELECT m.medicine_id, m.name, m.generic_name,
                   COALESCE(s.quantity, 0) AS stock, m.price
            FROM medicines m
            LEFT JOIN stock s ON m.medicine_id = s.medicine_id
            WHERE COALESCE(s.quantity, 0) < ?
            ORDER BY stock ASC
            LIMIT 50
            """,
            (threshold,),
        )
        return fmt_table(rows_to_dicts(cur.fetchall()))


@mcp.tool()
def get_expiring_soon(days: int = 30) -> str:
    """List medicines expiring within N days.

    Args:
        days: Number of days to look ahead (default 30)
    """
    cutoff = (datetime.now() + timedelta(days=days)).strftime("%Y-%m-%d")
    today = datetime.now().strftime("%Y-%m-%d")
    with get_db() as conn:
        cur = conn.execute(
            """
            SELECT m.medicine_id, m.name, m.generic_name,
                   m.expiry_date, COALESCE(s.quantity, 0) AS stock
            FROM medicines m
            LEFT JOIN stock s ON m.medicine_id = s.medicine_id
            WHERE m.expiry_date BETWEEN ? AND ?
                  AND COALESCE(s.quantity, 0) > 0
            ORDER BY m.expiry_date ASC
            LIMIT 50
            """,
            (today, cutoff),
        )
        return fmt_table(rows_to_dicts(cur.fetchall()))


@mcp.tool()
def update_stock(medicine_id: int, quantity_change: int) -> str:
    """Add or subtract stock for a medicine.

    Args:
        medicine_id: ID of the medicine
        quantity_change: Positive to add, negative to subtract
    """
    with get_db_rw() as conn:
        cur = conn.execute(
            "SELECT quantity FROM stock WHERE medicine_id = ?", (medicine_id,)
        )
        row = cur.fetchone()
        if row is None:
            if quantity_change < 0:
                return f"Error: No stock record for medicine_id={medicine_id}."
            conn.execute(
                "INSERT INTO stock (medicine_id, quantity) VALUES (?, ?)",
                (medicine_id, quantity_change),
            )
            new_qty = quantity_change
        else:
            new_qty = row["quantity"] + quantity_change
            if new_qty < 0:
                return f"Error: Cannot reduce stock below 0. Current: {row['quantity']}, change: {quantity_change}."
            conn.execute(
                "UPDATE stock SET quantity = ? WHERE medicine_id = ?",
                (new_qty, medicine_id),
            )
        # Fetch medicine name for confirmation
        med = conn.execute(
            "SELECT name FROM medicines WHERE medicine_id = ?", (medicine_id,)
        ).fetchone()
        name = med["name"] if med else f"ID#{medicine_id}"
        return f"✅ Stock updated: **{name}** → {new_qty} units (change: {quantity_change:+d})"


@mcp.tool()
def add_medicine(
    name: str,
    generic_name: str,
    company: str,
    price: float,
    expiry_date: str,
    initial_stock: int = 0,
) -> str:
    """Add a new medicine to the catalog.

    Args:
        name: Brand name of the medicine
        generic_name: Generic / salt name
        company: Manufacturer name
        price: Selling price
        expiry_date: Expiry date in YYYY-MM-DD format
        initial_stock: Starting stock quantity (default 0)
    """
    with get_db_rw() as conn:
        cur = conn.execute(
            """
            INSERT INTO medicines (name, generic_name, company, price, expiry_date)
            VALUES (?, ?, ?, ?, ?)
            """,
            (name, generic_name, company, price, expiry_date),
        )
        med_id = cur.lastrowid
        if initial_stock > 0:
            conn.execute(
                "INSERT INTO stock (medicine_id, quantity) VALUES (?, ?)",
                (med_id, initial_stock),
            )
    return (
        f"✅ Added medicine: **{name}** (ID: {med_id})\n"
        f"  Generic: {generic_name}, Company: {company}\n"
        f"  Price: ₹{price:.2f}, Expiry: {expiry_date}\n"
        f"  Initial stock: {initial_stock}"
    )


@mcp.tool()
def get_inventory_summary() -> str:
    """Get inventory KPIs: total items, total value, low stock count, expiring count."""
    return json.dumps(_get_inventory_summary(), indent=2)


def _get_inventory_summary() -> dict:
    today = datetime.now().strftime("%Y-%m-%d")
    cutoff_30 = (datetime.now() + timedelta(days=30)).strftime("%Y-%m-%d")
    with get_db() as conn:
        total = conn.execute("SELECT COUNT(*) FROM medicines").fetchone()[0]
        total_stock = conn.execute(
            "SELECT COALESCE(SUM(s.quantity), 0) FROM stock s"
        ).fetchone()[0]
        total_value = conn.execute(
            """
            SELECT COALESCE(SUM(m.price * s.quantity), 0)
            FROM medicines m JOIN stock s ON m.medicine_id = s.medicine_id
            """
        ).fetchone()[0]
        low_stock = conn.execute(
            """
            SELECT COUNT(*) FROM medicines m
            LEFT JOIN stock s ON m.medicine_id = s.medicine_id
            WHERE COALESCE(s.quantity, 0) < 10
            """
        ).fetchone()[0]
        out_of_stock = conn.execute(
            """
            SELECT COUNT(*) FROM medicines m
            LEFT JOIN stock s ON m.medicine_id = s.medicine_id
            WHERE COALESCE(s.quantity, 0) = 0
            """
        ).fetchone()[0]
        expiring_30 = conn.execute(
            """
            SELECT COUNT(*) FROM medicines m
            JOIN stock s ON m.medicine_id = s.medicine_id
            WHERE m.expiry_date BETWEEN ? AND ? AND s.quantity > 0
            """,
            (today, cutoff_30),
        ).fetchone()[0]
    return {
        "total_medicines": total,
        "total_stock_units": total_stock,
        "total_inventory_value": round(total_value, 2),
        "low_stock_count": low_stock,
        "out_of_stock_count": out_of_stock,
        "expiring_within_30_days": expiring_30,
    }


# =========================================================================
#  TOOLS — Customers
# =========================================================================

@mcp.tool()
def search_customers(query: str, limit: int = 20) -> str:
    """Search customers by name or phone number.

    Args:
        query: Search term (matches name or phone)
        limit: Maximum results (default 20)
    """
    with get_db() as conn:
        cur = conn.execute(
            """
            SELECT customer_id, name, phone, email,
                   COALESCE(current_balance, 0) AS credit_balance
            FROM customers
            WHERE name LIKE ? OR phone LIKE ?
            ORDER BY name
            LIMIT ?
            """,
            (f"%{query}%", f"%{query}%", limit),
        )
        return fmt_table(rows_to_dicts(cur.fetchall()))


@mcp.tool()
def get_customer_balance(customer_id: int) -> str:
    """Get a customer's outstanding credit (udhar) balance.

    Args:
        customer_id: Customer ID to look up
    """
    with get_db() as conn:
        row = conn.execute(
            """
            SELECT c.customer_id, c.name, c.phone,
                   COALESCE(c.current_balance, 0) AS credit_balance
            FROM customers c
            WHERE c.customer_id = ?
            """,
            (customer_id,),
        ).fetchone()
        if not row:
            return f"Customer with ID {customer_id} not found."
        d = dict(row)
        return (
            f"**{d['name']}** (ID: {d['customer_id']})\n"
            f"Phone: {d['phone']}\n"
            f"Outstanding balance: ₹{d['credit_balance']:.2f}"
        )


@mcp.tool()
def list_top_debtors(limit: int = 10) -> str:
    """List customers with the highest unpaid credit balances.

    Args:
        limit: Number of top debtors to return (default 10)
    """
    with get_db() as conn:
        cur = conn.execute(
            """
            SELECT customer_id, name, phone,
                   COALESCE(current_balance, 0) AS credit_balance
            FROM customers
            WHERE COALESCE(current_balance, 0) > 0
            ORDER BY credit_balance DESC
            LIMIT ?
            """,
            (limit,),
        )
        return fmt_table(rows_to_dicts(cur.fetchall()))


# =========================================================================
#  TOOLS — Billing & Finance
# =========================================================================

@mcp.tool()
def get_daily_sales(date: Optional[str] = None) -> str:
    """Get sales summary for a specific date (defaults to today).

    Args:
        date: Date in YYYY-MM-DD format (default: today)
    """
    if date is None:
        date = datetime.now().strftime("%Y-%m-%d")
    return json.dumps(_get_sales_for_date(date), indent=2)


def _get_sales_for_date(date: str) -> dict:
    with get_db() as conn:
        row = conn.execute(
            """
            SELECT COUNT(*) AS num_bills,
                   COALESCE(SUM(total_amount), 0) AS total_revenue
            FROM bills
            WHERE DATE(bill_date) = ?
            """,
            (date,),
        ).fetchone()
        # Payment mode breakdown
        modes = conn.execute(
            """
            SELECT COALESCE(payment_mode, 'Unknown') AS mode,
                   COUNT(*) AS count,
                   COALESCE(SUM(total_amount), 0) AS amount
            FROM bills
            WHERE DATE(bill_date) = ?
            GROUP BY payment_mode
            """,
            (date,),
        ).fetchall()
    return {
        "date": date,
        "num_bills": row["num_bills"],
        "total_revenue": round(row["total_revenue"], 2),
        "payment_breakdown": rows_to_dicts(modes),
    }


@mcp.tool()
def get_recent_bills(limit: int = 10) -> str:
    """Get the most recent bills with basic details.

    Args:
        limit: Number of recent bills to return (default 10)
    """
    with get_db() as conn:
        cur = conn.execute(
            """
            SELECT b.bill_id, b.bill_date, b.total_amount,
                   b.payment_mode,
                   COALESCE(c.name, 'Walk-in') AS customer
            FROM bills b
            LEFT JOIN customers c ON b.customer_id = c.customer_id
            ORDER BY b.bill_date DESC, b.bill_id DESC
            LIMIT ?
            """,
            (limit,),
        )
        return fmt_table(rows_to_dicts(cur.fetchall()))


@mcp.tool()
def get_expenses(
    start_date: Optional[str] = None,
    end_date: Optional[str] = None,
    category: Optional[str] = None,
) -> str:
    """Get expenses filtered by date range and/or category.

    Args:
        start_date: Start date YYYY-MM-DD (default: 30 days ago)
        end_date: End date YYYY-MM-DD (default: today)
        category: Optional category filter (e.g., Rent, Salaries)
    """
    if end_date is None:
        end_date = datetime.now().strftime("%Y-%m-%d")
    if start_date is None:
        start_date = (datetime.now() - timedelta(days=30)).strftime("%Y-%m-%d")

    query = """
        SELECT expense_id, category, description, amount, date
        FROM expenses
        WHERE DATE(date) BETWEEN ? AND ?
    """
    params: list = [start_date, end_date]
    if category:
        query += " AND category LIKE ?"
        params.append(f"%{category}%")
    query += " ORDER BY date DESC LIMIT 50"

    with get_db() as conn:
        cur = conn.execute(query, params)
        rows = rows_to_dicts(cur.fetchall())
        total = sum(r.get("amount", 0) for r in rows)
    result = fmt_table(rows)
    result += f"\n\n**Total expenses**: ₹{total:,.2f}"
    return result


@mcp.tool()
def get_profit_summary(
    start_date: Optional[str] = None,
    end_date: Optional[str] = None,
) -> str:
    """Get profit/loss summary: gross revenue, expenses, net profit.

    Args:
        start_date: Start date YYYY-MM-DD (default: current month start)
        end_date: End date YYYY-MM-DD (default: today)
    """
    if end_date is None:
        end_date = datetime.now().strftime("%Y-%m-%d")
    if start_date is None:
        start_date = datetime.now().replace(day=1).strftime("%Y-%m-%d")

    with get_db() as conn:
        rev = conn.execute(
            "SELECT COALESCE(SUM(total_amount), 0) FROM bills WHERE DATE(bill_date) BETWEEN ? AND ?",
            (start_date, end_date),
        ).fetchone()[0]
        exp = conn.execute(
            "SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE DATE(date) BETWEEN ? AND ?",
            (start_date, end_date),
        ).fetchone()[0]

    net_profit = rev - exp
    return (
        f"## Profit Summary ({start_date} → {end_date})\n\n"
        f"| Metric | Amount |\n"
        f"|--------|--------|\n"
        f"| Revenue | ₹{rev:,.2f} |\n"
        f"| Expenses | -₹{exp:,.2f} |\n"
        f"| **Net Profit** | **₹{net_profit:,.2f}** |"
    )


# =========================================================================
#  TOOLS — Prescriptions
# =========================================================================

@mcp.tool()
def search_prescriptions(query: str, limit: int = 20) -> str:
    """Search prescriptions by patient name or medicine.

    Args:
        query: Search term (patient name or medicine text)
        limit: Maximum results (default 20)
    """
    with get_db() as conn:
        schema = _resolve_prescription_schema(conn)
        cur = conn.execute(
            f"""
            SELECT CAST(p.{schema['id_col']} AS TEXT) AS prescription_id,
                   {schema['customer_name_expr']} AS patient,
                   p.{schema['date_col']} AS prescribed_date,
                   {schema['status_expr']} AS status,
                   {schema['medicines_expr']} AS medicines_text
            FROM prescriptions p
            WHERE {schema['customer_name_expr']} LIKE ?
               OR {schema['doctor_name_expr']} LIKE ?
               OR {schema['medicines_expr']} LIKE ?
            ORDER BY p.{schema['date_col']} DESC, p.{schema['id_col']} DESC
            LIMIT ?
            """,
            (f"%{query}%", f"%{query}%", f"%{query}%", limit),
        )
        return fmt_table(rows_to_dicts(cur.fetchall()))


@mcp.tool()
def get_prescription_details(prescription_id: str) -> str:
    """Get full details of a specific prescription.

    Args:
        prescription_id: Prescription ID to look up (supports numeric or text IDs)
    """
    with get_db() as conn:
        schema = _resolve_prescription_schema(conn)
        row = conn.execute(
            f"""
            SELECT CAST(p.{schema['id_col']} AS TEXT) AS prescription_id,
                   {schema['customer_name_expr']} AS patient,
                   {schema['phone_expr']} AS phone,
                   {schema['doctor_name_expr']} AS doctor,
                   p.{schema['date_col']} AS prescribed_date,
                   {schema['status_expr']} AS status,
                   {schema['medicines_expr']} AS medicines_text,
                   {schema['ai_validation_expr']} AS ai_validation,
                   {schema['notes_expr']} AS notes
            FROM prescriptions p
            WHERE CAST(p.{schema['id_col']} AS TEXT) = CAST(? AS TEXT)
            """,
            (prescription_id,),
        ).fetchone()
        if not row:
            return f"Prescription #{prescription_id} not found."
        d = dict(row)
        return (
            f"## Prescription #{d['prescription_id']}\n\n"
            f"- **Patient**: {d.get('patient', 'N/A')} ({d.get('phone', 'N/A')})\n"
            f"- **Doctor**: {d.get('doctor', 'N/A')}\n"
            f"- **Date**: {d.get('prescribed_date', 'N/A')}\n"
            f"- **Status**: {d.get('status', 'N/A')}\n\n"
            f"### Medicines\n{d.get('medicines_text', 'None')}\n\n"
            f"### AI Validation\n{d.get('ai_validation', 'Not validated')}\n\n"
            f"### Notes\n{d.get('notes', 'None')}"
        )


# =========================================================================
#  TOOLS — AI Engine
# =========================================================================

@mcp.tool()
def ai_chat(prompt: str) -> str:
    """Send a prompt to the local LLM for general-purpose inference.

    Args:
        prompt: Text prompt to send to the AI model
    """
    import urllib.request
    import urllib.error

    try:
        data = json.dumps({"prompt": prompt}).encode()
        req = urllib.request.Request(
            f"{AI_ENGINE_URL}/chat",
            data=data,
            headers={"Content-Type": "application/json"},
        )
        with urllib.request.urlopen(req, timeout=120) as resp:
            result = json.loads(resp.read())
        return result.get("response", str(result))
    except urllib.error.URLError:
        return "Error: AI Engine is not running. Start it with `python server.py`."
    except Exception as e:
        return f"Error: {e}"


@mcp.tool()
def ai_rag_query(prompt: str) -> str:
    """Ask a business question with auto-fetched inventory context (RAG).

    The tool automatically gathers current inventory data (low stock,
    expiring medicines, today's sales) and sends it alongside your
    prompt for context-aware answers.

    Args:
        prompt: Business question about your pharmacy
    """
    import urllib.request
    import urllib.error

    # Build context from real DB data
    summary = _get_inventory_summary()
    sales = _get_sales_for_date(datetime.now().strftime("%Y-%m-%d"))

    with get_db() as conn:
        low = conn.execute(
            """
            SELECT m.name, COALESCE(s.quantity, 0) AS stock
            FROM medicines m
            LEFT JOIN stock s ON m.medicine_id = s.medicine_id
            WHERE COALESCE(s.quantity, 0) < 10
            ORDER BY stock ASC LIMIT 15
            """,
        ).fetchall()

    context = (
        f"Inventory Summary: {json.dumps(summary)}\n"
        f"Today's Sales: {json.dumps(sales)}\n"
        f"Low Stock Items: {json.dumps(rows_to_dicts(low))}\n"
    )

    try:
        data = json.dumps({"prompt": prompt, "context": context}).encode()
        req = urllib.request.Request(
            f"{AI_ENGINE_URL}/chat/rag",
            data=data,
            headers={"Content-Type": "application/json"},
        )
        with urllib.request.urlopen(req, timeout=120) as resp:
            result = json.loads(resp.read())
        return result.get("response", str(result))
    except urllib.error.URLError:
        return "Error: AI Engine is not running. Start it with `python server.py`."
    except Exception as e:
        return f"Error: {e}"


@mcp.tool()
def get_hardware_info() -> str:
    """Get current hardware detection results and NPU/GPU status."""
    import urllib.request
    import urllib.error

    try:
        req = urllib.request.Request(f"{AI_ENGINE_URL}/hardware")
        with urllib.request.urlopen(req, timeout=10) as resp:
            hw = json.loads(resp.read())

        req2 = urllib.request.Request(f"{AI_ENGINE_URL}/npu_info")
        with urllib.request.urlopen(req2, timeout=10) as resp2:
            npu = json.loads(resp2.read())

        return json.dumps({"hardware": hw, "npu": npu}, indent=2)
    except urllib.error.URLError:
        return "Error: AI Engine is not running."
    except Exception as e:
        return f"Error: {e}"


# =========================================================================
#  PROMPTS
# =========================================================================

@mcp.prompt()
def pharmacy_assistant() -> str:
    """System prompt for acting as a MediManage pharmacy assistant."""
    return (
        "You are an AI pharmacy assistant for MediManage. "
        "You have direct access to the pharmacy database through MCP tools. "
        "When the user asks about inventory, sales, customers, or prescriptions, "
        "use the appropriate tools to fetch real data. "
        "Always provide specific numbers and actionable recommendations. "
        "Format currency in Indian Rupees (₹)."
    )


@mcp.prompt()
def daily_report() -> str:
    """Generate a comprehensive daily pharmacy report."""
    return (
        "Generate a comprehensive daily report for the pharmacy. Include:\n"
        "1. Today's sales summary (use get_daily_sales)\n"
        "2. Inventory alerts — low stock and expiring items (use get_low_stock, get_expiring_soon)\n"
        "3. Top debtors (use list_top_debtors)\n"
        "4. Profit summary for the current month (use get_profit_summary)\n"
        "Format the report professionally with sections and tables."
    )


# =========================================================================
#  Entry point
# =========================================================================

if __name__ == "__main__":
    import sys

    if "--stdio" in sys.argv:
        # STDIO mode for Claude Desktop / direct subprocess launch
        mcp.run(transport="stdio")
    else:
        # SSE mode — runs as HTTP server on port 5001 alongside Flask (5000)
        # Connect from any MCP client via: http://127.0.0.1:5001/sse
        print("[MCP] Server starting on http://127.0.0.1:5001 (SSE transport)")
        print(f"   Tools: 18 | Resources: 3 | Prompts: 2")
        print(f"   AI Engine: {AI_ENGINE_URL}")
        mcp.run(transport="sse")

