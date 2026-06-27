from __future__ import annotations

from contextlib import contextmanager

from rag_app.config import RAGSettings


class Database:
    def __init__(self, settings: RAGSettings):
        self.settings = settings

    @contextmanager
    def cursor(self):
        import mysql.connector

        conn = mysql.connector.connect(
            host=self.settings.db_host,
            port=self.settings.db_port,
            user=self.settings.db_user,
            password=self.settings.db_password,
            database=self.settings.db_name,
            charset="utf8mb4",
        )
        cur = conn.cursor(dictionary=True)
        try:
            yield cur
        finally:
            cur.close()
            conn.close()
