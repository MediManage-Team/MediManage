import logging
import re

from app.db.connector import fetchall_as_dicts, get_readonly_connection, placeholder  # type: ignore

logger = logging.getLogger(__name__)

cancel_flag = False


def search_pharmacy_db(query: str) -> str:
    """
    Query the MediManage database for pharmacy-related data.
    Returns formatted context text or an empty string when nothing relevant is found.
    """
    q = (query or "").lower()
    results: list[str] = []
    ph = placeholder()

    try:
        with get_readonly_connection() as conn:
            cur = conn.cursor()

            med_keywords = [
                "medicine", "tablet", "capsule", "syrup", "drug", "stock", "inventory",
                "available", "paracetamol", "amoxicillin", "how many", "quantity", "check",
            ]
            if any(keyword in q for keyword in med_keywords):
                stop_words = {
                    "how", "many", "check", "show", "list", "get", "find", "me", "the", "a", "an",
                    "of", "in", "is", "are", "do", "we", "have", "what", "tablets", "tablet",
                    "capsules", "medicine", "medicines", "drug", "drugs", "stock", "inventory",
                    "available", "our",
                }
                words = re.findall(r"[a-zA-Z]+", q)
                search_terms = [word for word in words if word not in stop_words and len(word) > 2]

                if search_terms:
                    like_clauses = " OR ".join([f"m.name LIKE {ph} OR m.generic_name LIKE {ph}" for _ in search_terms])
                    params: list[str] = []
                    for term in search_terms:
                        params.extend([f"%{term}%", f"%{term}%"])
                    cur.execute(
                        f"""
                        SELECT m.name, m.generic_name, m.company,
                               m.price, m.expiry_date,
                               COALESCE(s.quantity, 0) AS stock_qty
                        FROM medicines m
                        LEFT JOIN stock s ON m.medicine_id = s.medicine_id
                        WHERE {like_clauses}
                        LIMIT 20
                        """,
                        params,
                    )
                else:
                    cur.execute(
                        """
                        SELECT m.name, m.generic_name, m.company,
                               m.price, m.expiry_date,
                               COALESCE(s.quantity, 0) AS stock_qty
                        FROM medicines m
                        LEFT JOIN stock s ON m.medicine_id = s.medicine_id
                        LIMIT 20
                        """
                    )

                rows = fetchall_as_dicts(cur)
                if rows:
                    results.append(f"[Inventory Data - {len(rows)} medicines found]:")
                    for row in rows:
                        results.append(
                            f"  - {row['name']} ({row.get('generic_name') or 'N/A'}) | "
                            f"Company: {row.get('company') or 'N/A'} | "
                            f"Price: {row.get('price')} | "
                            f"Stock: {row.get('stock_qty')} | "
                            f"Expiry: {row.get('expiry_date') or 'N/A'}"
                        )

            if any(keyword in q for keyword in ["low stock", "reorder", "running out", "shortage", "out of stock"]):
                cur.execute(
                    """
                    SELECT m.name, m.generic_name,
                           COALESCE(s.quantity, 0) AS stock_qty
                    FROM medicines m
                    LEFT JOIN stock s ON m.medicine_id = s.medicine_id
                    WHERE COALESCE(s.quantity, 0) < 10
                    ORDER BY stock_qty ASC
                    LIMIT 20
                    """
                )
                rows = fetchall_as_dicts(cur)
                if rows:
                    results.append(f"[Low Stock Alert - {len(rows)} medicines below 10 units]:")
                    for row in rows:
                        results.append(
                            f"  - {row['name']} ({row.get('generic_name') or 'N/A'}) | "
                            f"Stock: {row.get('stock_qty')}"
                        )

            if any(keyword in q for keyword in ["expir", "expired", "expiring", "shelf life", "validity"]):
                if ph == "%s":
                    cur.execute(
                        """
                        SELECT m.name, m.expiry_date,
                               COALESCE(s.quantity, 0) AS stock_qty
                        FROM medicines m
                        LEFT JOIN stock s ON m.medicine_id = s.medicine_id
                        WHERE m.expiry_date IS NOT NULL
                          AND m.expiry_date <= CURRENT_DATE + INTERVAL '90 days'
                        ORDER BY m.expiry_date ASC
                        LIMIT 20
                        """
                    )
                else:
                    cur.execute(
                        """
                        SELECT m.name, m.expiry_date,
                               COALESCE(s.quantity, 0) AS stock_qty
                        FROM medicines m
                        LEFT JOIN stock s ON m.medicine_id = s.medicine_id
                        WHERE m.expiry_date IS NOT NULL
                          AND m.expiry_date <= date('now', '+90 days')
                        ORDER BY m.expiry_date ASC
                        LIMIT 20
                        """
                    )
                rows = fetchall_as_dicts(cur)
                if rows:
                    results.append(f"[Expiring Soon - {len(rows)} medicines within 90 days]:")
                    for row in rows:
                        results.append(
                            f"  - {row['name']} | Expiry: {row.get('expiry_date')} | "
                            f"Stock: {row.get('stock_qty')}"
                        )

            if any(keyword in q for keyword in ["customer", "balance", "debt", "debtor", "owe", "credit"]):
                cur.execute(
                    """
                    SELECT name, phone, current_balance
                    FROM customers
                    WHERE current_balance > 0
                    ORDER BY current_balance DESC
                    LIMIT 15
                    """
                )
                rows = fetchall_as_dicts(cur)
                if rows:
                    results.append(f"[Customer Balances - {len(rows)} with outstanding debt]:")
                    for row in rows:
                        results.append(
                            f"  - {row['name']} | Phone: {row.get('phone') or 'N/A'} | "
                            f"Balance: {row.get('current_balance')}"
                        )

            if any(keyword in q for keyword in ["sale", "sales", "revenue", "bill", "today", "income", "profit", "earning"]):
                today_sql = "CURRENT_DATE" if ph == "%s" else "date('now')"
                month_sql = "CURRENT_DATE - INTERVAL '30 days'" if ph == "%s" else "date('now', '-30 days')"

                cur.execute(
                    f"""
                    SELECT COUNT(*) AS bill_count,
                           COALESCE(SUM(total_amount), 0) AS total_revenue
                    FROM bills
                    WHERE date(bill_date) = {today_sql}
                    """
                )
                rows = fetchall_as_dicts(cur)
                if rows and rows[0]:
                    row = rows[0]
                    results.append(
                        f"[Today's Sales]: {row.get('bill_count')} bills, "
                        f"Total Revenue: {row.get('total_revenue')}"
                    )

                cur.execute(
                    f"""
                    SELECT COUNT(*) AS bill_count,
                           COALESCE(SUM(total_amount), 0) AS total_revenue
                    FROM bills
                    WHERE bill_date >= {month_sql}
                    """
                )
                rows = fetchall_as_dicts(cur)
                if rows and rows[0]:
                    row = rows[0]
                    results.append(
                        f"[Last 30 Days]: {row.get('bill_count')} bills, "
                        f"Total Revenue: {row.get('total_revenue')}"
                    )

    except Exception as exc:
        logger.error("Pharmacy DB query failed: %s", exc)
        return ""

    if results:
        logger.info("Pharmacy DB returned %s lines of context", len(results))
        return "\n".join(results)
    return ""
