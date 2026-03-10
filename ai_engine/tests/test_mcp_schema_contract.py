import os
import sqlite3
import sys
import tempfile
import types
import unittest
from importlib import import_module
from pathlib import Path
from textwrap import dedent


def _install_mcp_stub() -> None:
    """Install a lightweight MCP stub so mcp_server can be imported in tests."""
    if "mcp.server.fastmcp" in sys.modules:
        return

    class _FastMCP:
        def __init__(self, *args, **kwargs):
            pass

        def resource(self, *_args, **_kwargs):
            return lambda f: f

        def tool(self, *_args, **_kwargs):
            return lambda f: f

        def prompt(self, *_args, **_kwargs):
            return lambda f: f

        def run(self, *args, **kwargs):
            return None

    mcp_pkg = types.ModuleType("mcp")
    server_pkg = types.ModuleType("mcp.server")
    fastmcp_pkg = types.ModuleType("mcp.server.fastmcp")
    fastmcp_pkg.FastMCP = _FastMCP

    sys.modules["mcp"] = mcp_pkg
    sys.modules["mcp.server"] = server_pkg
    sys.modules["mcp.server.fastmcp"] = fastmcp_pkg


class MCPSchemaContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        _install_mcp_stub()
        project_root = Path(__file__).resolve().parents[2]
        ai_engine_path = project_root / "ai_engine"
        sys.path.insert(0, str(ai_engine_path))
        cls.mcp_server = import_module("mcp_server")
        cls.original_db_path = cls.mcp_server.DB_PATH

    @classmethod
    def tearDownClass(cls):
        cls.mcp_server.DB_PATH = cls.original_db_path

    def setUp(self):
        self._temp_dbs = []

    def tearDown(self):
        for db_path in self._temp_dbs:
            try:
                os.remove(db_path)
            except OSError:
                pass

    def _create_temp_db(self, schema_sql: str) -> str:
        fd, db_path = tempfile.mkstemp(suffix=".db")
        os.close(fd)
        self._temp_dbs.append(db_path)

        conn = sqlite3.connect(db_path)
        conn.executescript(schema_sql)
        conn.commit()
        conn.close()
        return db_path

    def _exercise_prescription_tools(self, schema_sql: str, search_term: str, lookup_id: str):
        db_path = self._create_temp_db(schema_sql)
        self.mcp_server.DB_PATH = db_path

        search_output = self.mcp_server.search_prescriptions(search_term, limit=5)
        detail_output = self.mcp_server.get_prescription_details(lookup_id)
        return search_output, detail_output

    def test_prescription_tools_with_current_schema(self):
        schema_sql = dedent(
            """
            CREATE TABLE customers (
                customer_id INTEGER PRIMARY KEY,
                name TEXT,
                phone TEXT
            );
            CREATE TABLE prescriptions (
                prescription_id INTEGER PRIMARY KEY,
                customer_id INTEGER,
                customer_name TEXT,
                doctor_name TEXT,
                status TEXT,
                prescribed_date TEXT,
                medicines_text TEXT,
                ai_validation TEXT,
                notes TEXT
            );
            INSERT INTO customers(customer_id, name, phone)
            VALUES (1, 'Asha', '9999999999');
            INSERT INTO prescriptions(
                prescription_id, customer_id, customer_name, doctor_name,
                status, prescribed_date, medicines_text, ai_validation, notes
            ) VALUES (
                101, 1, 'Asha', 'Dr Rao',
                'PENDING', '2026-02-20', 'Paracetamol', 'OK', 'Take after food'
            );
            """
        )

        search_output, detail_output = self._exercise_prescription_tools(schema_sql, "Asha", "101")
        self.assertIn("101", search_output)
        self.assertIn("Paracetamol", search_output)
        self.assertIn("Prescription #101", detail_output)
        self.assertIn("Dr Rao", detail_output)
        self.assertIn("Take after food", detail_output)

    def test_prescription_tools_with_legacy_schema(self):
        schema_sql = dedent(
            """
            CREATE TABLE customers (
                customer_id INTEGER PRIMARY KEY,
                name TEXT,
                phone TEXT
            );
            CREATE TABLE doctors (
                id TEXT PRIMARY KEY,
                name TEXT
            );
            CREATE TABLE prescriptions (
                id TEXT PRIMARY KEY,
                customer_id TEXT,
                doctor_id TEXT,
                created_at TEXT,
                prescription_date TEXT,
                customer_name TEXT,
                doctor_name TEXT,
                status TEXT,
                medicines_text TEXT,
                ai_validation TEXT,
                notes TEXT
            );
            INSERT INTO customers(customer_id, name, phone)
            VALUES (2, 'Bala', '8888888888');
            INSERT INTO doctors(id, name)
            VALUES ('D1', 'Dr Iyer');
            INSERT INTO prescriptions(
                id, customer_id, doctor_id, created_at, prescription_date,
                customer_name, doctor_name, status, medicines_text, ai_validation, notes
            ) VALUES (
                'RX-1', '2', 'D1', '2026-02-21', '2026-02-21',
                'Bala', NULL, 'VERIFIED', 'Amoxicillin', 'Checked', 'Legacy note'
            );
            """
        )

        search_output, detail_output = self._exercise_prescription_tools(schema_sql, "Bala", "RX-1")
        self.assertIn("RX-1", search_output)
        self.assertIn("Amoxicillin", search_output)
        self.assertIn("Prescription #RX-1", detail_output)
        self.assertIn("Dr Iyer", detail_output)
        self.assertIn("Legacy note", detail_output)


if __name__ == "__main__":
    unittest.main()
