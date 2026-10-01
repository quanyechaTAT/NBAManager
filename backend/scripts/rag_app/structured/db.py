from __future__ import annotations

import threading
from contextlib import contextmanager

from rag_app.config import RAGSettings


class Database:
    """结构化数据源（第 8 轮 P2-1：MySQL 连接池化）。

    此前每次 cursor() 都新建 MySQL 连接（第 1 轮遗留：每查询一次握手 + 认证）。
    现用 mysql.connector 自带连接池（pool_size=4，惰性初始化 + 双重检查锁），
    conn.close() 语义变为"归还池中"而非断开。池不可用（如驱动缺池支持）时
    回退到直连——查询路径不能因为池化改造而不可用。
    """

    _POOL_SIZE = 4

    def __init__(self, settings: RAGSettings):
        self.settings = settings
        self._pool = None
        self._pool_lock = threading.Lock()

    def _get_pool(self):
        if self._pool is None:
            with self._pool_lock:
                if self._pool is None:
                    import mysql.connector

                    try:
                        self._pool = mysql.connector.pooling.MySQLConnectionPool(
                            pool_name="rag_structured_pool",
                            pool_size=self._POOL_SIZE,
                            pool_reset_session=True,
                            host=self.settings.db_host,
                            port=self.settings.db_port,
                            user=self.settings.db_user,
                            password=self.settings.db_password,
                            database=self.settings.db_name,
                            charset="utf8mb4",
                        )
                    except Exception:
                        # 池创建失败（如驱动版本不支持）：退回直连模式
                        self._pool = False
        return self._pool

    @contextmanager
    def cursor(self):
        import mysql.connector

        pool = self._get_pool()
        if pool:
            conn = pool.get_connection()
        else:
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
