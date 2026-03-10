"""
db_connector.py — Universal database connector for MediManage AI Engine.

Supports SQLite. 

Environment Variables (set by Java → MediManageApplication.java):
  MEDIMANAGE_DB_PATH      = path to SQLite file (default: ../medimanage.db)
"""

import os
import logging
from contextlib import contextmanager
import sqlite3

logger = logging.getLogger(__name__)

_HERE = os.path.dirname(os.path.abspath(__file__))


def _get_sqlite_path():
    """Return the SQLite database path."""
    default = os.path.join(os.path.dirname(_HERE), "medimanage.db")
    return os.environ.get("MEDIMANAGE_DB_PATH", default)


def get_backend_info():
    """Return a dict describing the active database backend."""
    return {
        "backend": "sqlite",
        "path": _get_sqlite_path(),
    }


@contextmanager
def get_readonly_connection():
    """Yield a read-only database connection (auto-closes on exit)."""
    conn = None
    try:
        db_path = _get_sqlite_path()
        if not os.path.isfile(db_path):
            raise FileNotFoundError(f"SQLite database not found: {db_path}")
        conn = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
        conn.row_factory = sqlite3.Row
        yield conn
    finally:
        if conn:
            conn.close()


@contextmanager
def get_readwrite_connection():
    """Yield a read-write database connection (auto-commits/closes on exit)."""
    conn = None
    try:
        db_path = _get_sqlite_path()
        conn = sqlite3.connect(db_path)
        conn.row_factory = sqlite3.Row
        yield conn
        conn.commit()
    except Exception:
        if conn:
            conn.rollback()
        raise
    finally:
        if conn:
            conn.close()


def fetchall_as_dicts(cursor):
    """Convert cursor results to a list of plain dicts."""
    return [dict(row) for row in cursor.fetchall()]


def get_schema_sql():
    """Return the database schema as SQL text."""
    with get_readonly_connection() as conn:
        cur = conn.cursor()
        cur.execute("SELECT sql FROM sqlite_master WHERE type='table' ORDER BY name")
        return "\n\n".join(row[0] for row in cur.fetchall() if row[0])


def table_exists(table_name):
    """Check whether a table exists in the database."""
    with get_readonly_connection() as conn:
        cur = conn.cursor()
        cur.execute(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
            (table_name,)
        )
        return cur.fetchone() is not None


def table_columns(table_name):
    """Return the set of column names for a table."""
    with get_readonly_connection() as conn:
        cur = conn.cursor()
        cur.execute(f"PRAGMA table_info({table_name})")
        return {row[1] for row in cur.fetchall()}


def placeholder():
    """Return the SQL parameter placeholder for the current backend."""
    return "?"


logger.info(f"DB connector initialized: backend=sqlite")
