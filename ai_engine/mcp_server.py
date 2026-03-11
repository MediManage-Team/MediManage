import os
from contextlib import contextmanager

from app.db import connector as db_connector  # type: ignore
from app.mcp import server as _server  # type: ignore

DB_PATH = db_connector.get_backend_info()["path"]


@contextmanager
def _db_override():
    previous = os.environ.get("MEDIMANAGE_DB_PATH")
    os.environ["MEDIMANAGE_DB_PATH"] = DB_PATH
    try:
        yield
    finally:
        if previous is None:
            os.environ.pop("MEDIMANAGE_DB_PATH", None)
        else:
            os.environ["MEDIMANAGE_DB_PATH"] = previous


def search_prescriptions(query: str, limit: int = 20) -> str:
    with _db_override():
        return _server.search_prescriptions(query, limit=limit)


def get_prescription_details(prescription_id: str) -> str:
    with _db_override():
        return _server.get_prescription_details(prescription_id)


def start():
    with _db_override():
        return _server.start()


def __getattr__(name: str):
    return getattr(_server, name)


if __name__ == "__main__":
    start()
