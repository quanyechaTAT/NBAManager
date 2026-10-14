from __future__ import annotations

from contextlib import contextmanager
import json
import queue
import threading
from typing import Iterator

import pymysql
from discovery_app.config import Settings


class Database:
    """Bounded shared pool. Every borrower returns a rolled-back, clean connection."""
    def __init__(self, settings: Settings, size: int = 8):
        self.settings, self.size = settings, size
        self.pool = queue.LifoQueue(size)
        self.lock, self.count = threading.Lock(), 0

    def _connect(self):
        s = self.settings
        return pymysql.connect(host=s.db_host, port=s.db_port, user=s.db_user, password=s.db_password,
                               database=s.db_name, charset='utf8mb4', autocommit=False,
                               cursorclass=pymysql.cursors.DictCursor, connect_timeout=3,
                               read_timeout=10, write_timeout=10, init_command="SET time_zone='+00:00'")

    @contextmanager
    def connection(self) -> Iterator:
        try: connection = self.pool.get_nowait()
        except queue.Empty:
            with self.lock:
                if self.count < self.size:
                    connection = self._connect()
                    self.count += 1
                else: connection = None
            if connection is None: connection = self.pool.get(timeout=3)
        try:
            connection.ping(reconnect=True)
            yield connection
        finally:
            try:
                connection.rollback()
                self.pool.put_nowait(connection)
            except (pymysql.MySQLError, queue.Full):
                connection.close()
                with self.lock: self.count -= 1

    def rows(self, sql: str, args=()) -> list[dict]:
        with self.connection() as connection, connection.cursor() as cursor:
            cursor.execute(sql, args)
            return list(cursor.fetchall())

    def execute(self, sql: str, args=()) -> int:
        with self.connection() as connection, connection.cursor() as cursor:
            count = cursor.execute(sql, args)
            connection.commit()
            return count

    def control(self, key: str, default=None):
        rows = self.rows('SELECT value_json FROM discovery_control WHERE control_key=%s', (key,))
        return json.loads(rows[0]['value_json']) if rows else default

    def set_control(self, key: str, value):
        self.execute('INSERT INTO discovery_control(control_key,value_json,updated_at) VALUES(%s,%s,UTC_TIMESTAMP(6)) '
                     'ON DUPLICATE KEY UPDATE value_json=VALUES(value_json),updated_at=VALUES(updated_at)',
                     (key, json.dumps(value, ensure_ascii=False)))
