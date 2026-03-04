"""
db_connector.py — Universal database connector for MediManage AI Engine.

Supports both SQLite and PostgreSQL, auto-detected from environment variables
set by the Java application at startup.

Environment Variables (set by Java → MediManageApplication.java):
  MEDIMANAGE_DB_BACKEND   = "sqlite" or "postgresql" (default: sqlite)
  MEDIMANAGE_DB_PATH      = path to SQLite file (default: ../medimanage.db)
  MEDIMANAGE_PG_HOST      = PostgreSQL host (default: localhost)
  MEDIMANAGE_PG_PORT      = PostgreSQL port (default: 5432)
  MEDIMANAGE_PG_DATABASE  = PostgreSQL database name (default: medimanage)
  MEDIMANAGE_PG_USER      = PostgreSQL username (default: postgres)
  MEDIMANAGE_PG_PASSWORD  = PostgreSQL password (default: "")
"""

import os
import logging
from contextlib import contextmanager

logger = logging.getLogger(__name__)

_HERE = os.path.dirname(os.path.abspath(__file__))


def _get_backend():
    """Return 'postgresql' or 'sqlite' based on environment."""
    return os.environ.get("MEDIMANAGE_DB_BACKEND", "sqlite").lower()


def _get_sqlite_path():
    """Return the SQLite database path."""
    default = os.path.join(os.path.dirname(_HERE), "medimanage.db")
    return os.environ.get("MEDIMANAGE_DB_PATH", default)


def _get_pg_config():
    """Return PostgreSQL connection parameters as a dict."""
    return {
        "host": os.environ.get("MEDIMANAGE_PG_HOST", "localhost"),
        "port": int(os.environ.get("MEDIMANAGE_PG_PORT", "5432")),
        "dbname": os.environ.get("MEDIMANAGE_PG_DATABASE", "medimanage"),
        "user": os.environ.get("MEDIMANAGE_PG_USER", "postgres"),
        "password": os.environ.get("MEDIMANAGE_PG_PASSWORD", ""),
    }


def get_backend_info():
    """Return a dict describing the active database backend."""
    backend = _get_backend()
    if backend == "postgresql":
        pg = _get_pg_config()
        return {
            "backend": "postgresql",
            "host": pg["host"],
            "port": pg["port"],
            "database": pg["dbname"],
            "user": pg["user"],
        }
    else:
        return {
            "backend": "sqlite",
            "path": _get_sqlite_path(),
        }


@contextmanager
def get_readonly_connection():
    """Yield a read-only database connection (auto-closes on exit).

    Works with both SQLite and PostgreSQL.
    For SQLite: uses file URI with mode=ro.
    For PostgreSQL: uses psycopg2 with autocommit (read-only intent).
    """
    backend = _get_backend()
    conn = None
    try:
        if backend == "postgresql":
            import psycopg2
            import psycopg2.extras
            pg = _get_pg_config()
            conn = psycopg2.connect(**pg)
            conn.set_session(readonly=True, autocommit=True)
            yield conn
        else:
            import sqlite3
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
    """Yield a read-write database connection (auto-commits/closes on exit).

    Works with both SQLite and PostgreSQL.
    """
    backend = _get_backend()
    conn = None
    try:
        if backend == "postgresql":
            import psycopg2
            pg = _get_pg_config()
            conn = psycopg2.connect(**pg)
            yield conn
            conn.commit()
        else:
            import sqlite3
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
    """Convert cursor results to a list of plain dicts.

    Works with both sqlite3.Row and psycopg2 cursor.
    """
    backend = _get_backend()
    if backend == "postgresql":
        columns = [desc[0] for desc in cursor.description] if cursor.description else []
        return [dict(zip(columns, row)) for row in cursor.fetchall()]
    else:
        return [dict(row) for row in cursor.fetchall()]


def get_schema_sql():
    """Return the database schema as SQL text.

    For SQLite: reads from sqlite_master.
    For PostgreSQL: reads from information_schema.
    """
    backend = _get_backend()
    with get_readonly_connection() as conn:
        cur = conn.cursor()
        if backend == "postgresql":
            cur.execute("""
                SELECT table_name, column_name, data_type, is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public'
                ORDER BY table_name, ordinal_position
            """)
            rows = fetchall_as_dicts(cur)
            # Group by table
            tables = {}
            for row in rows:
                t = row["table_name"]
                if t not in tables:
                    tables[t] = []
                nullable = "NULL" if row["is_nullable"] == "YES" else "NOT NULL"
                tables[t].append(f"  {row['column_name']} {row['data_type']} {nullable}")
            parts = []
            for table, cols in tables.items():
                parts.append(f"CREATE TABLE {table} (\n" + ",\n".join(cols) + "\n);")
            return "\n\n".join(parts)
        else:
            cur.execute("SELECT sql FROM sqlite_master WHERE type='table' ORDER BY name")
            return "\n\n".join(row[0] for row in cur.fetchall() if row[0])


def table_exists(table_name):
    """Check whether a table exists in the database."""
    backend = _get_backend()
    with get_readonly_connection() as conn:
        cur = conn.cursor()
        if backend == "postgresql":
            cur.execute(
                "SELECT 1 FROM information_schema.tables "
                "WHERE table_schema='public' AND table_name=%s",
                (table_name,)
            )
        else:
            cur.execute(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
                (table_name,)
            )
        return cur.fetchone() is not None


def table_columns(table_name):
    """Return the set of column names for a table."""
    backend = _get_backend()
    with get_readonly_connection() as conn:
        cur = conn.cursor()
        if backend == "postgresql":
            cur.execute(
                "SELECT column_name FROM information_schema.columns "
                "WHERE table_schema='public' AND table_name=%s",
                (table_name,)
            )
            return {row[0] for row in cur.fetchall()}
        else:
            cur.execute(f"PRAGMA table_info({table_name})")
            return {row[1] for row in cur.fetchall()}


def placeholder():
    """Return the SQL parameter placeholder for the current backend.

    SQLite uses '?', PostgreSQL uses '%s'.
    """
    return "%s" if _get_backend() == "postgresql" else "?"


logger.info(f"DB connector initialized: backend={_get_backend()}")
