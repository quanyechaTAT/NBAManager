# -*- coding: utf-8 -*-
"""gzip 的**服务端** CPU 成本 A/B 测量（决定"大 JSON 是否继续在 Java 侧压缩"）。

## 为什么要单独测这个
第 9 轮 R9-1 的 A/B 是"同一负载下改客户端 Accept-Encoding"：
gzip → 2×1250 RPS 出现 8% 客户端超时；identity → 0 超时。
据此判定"gzip 压缩 CPU 把 java 推过饱和点"，并把 application/json 移出压缩面。

但这个 A/B 有一个**未排除的混淆变量**：换掉 Accept-Encoding 同时改变了
**客户端**的成本（压缩响应要解压；2×1250 RPS × 292KB 的解压量相当可观）。
若瓶颈其实在发压端，那么"identity 更快"并不能推出"java 被压缩 CPU 拖垮"。

本脚本直接测**服务端**：只统计 java 进程的 CPU 时间（GetProcessTimes），
对比两种模式下"每请求 java CPU 毫秒数"。客户端解压成本不会计入 java。

## 判定口径
- 若 gzip 的 java CPU/请求 与 identity 接近（差异 < ~30%）⇒ 压缩不是服务端瓶颈，
  可保留大响应压缩（保住 72%–92% 带宽收益），并据实修正 R9 的归因；
- 若显著更高（成倍）⇒ 确认 R9 归因，应按端点/体积收窄或关闭大 JSON 压缩。

用法:
    backend/scripts/venv312/Scripts/python.exe backend/scripts/measure_gzip_cpu.py <java_pid>
"""
from __future__ import annotations

import asyncio
import ctypes
import ctypes.wintypes as wintypes
import json
import os
import statistics
import sys
import time

import aiohttp

BASE = "http://127.0.0.1:8080"
CONCURRENCY = 32
DURATION = 15.0
WARMUP = 3.0

ENDPOINTS = [
    ("/api/historical/players?limit=500&season=2023-24", "历史榜单 292KB"),
    ("/api/news?page=0&size=20", "新闻列表 17.5KB"),
    ("/api/shot-chart/player/1", "投篮热区 133KB"),
]

# ---------------------------------------------------------------- Windows CPU 读取
_k32 = ctypes.WinDLL("kernel32", use_last_error=True)
_PROCESS_QUERY_LIMITED_INFORMATION = 0x1000
_k32.OpenProcess.restype = wintypes.HANDLE
_k32.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
_k32.GetProcessTimes.argtypes = [
    wintypes.HANDLE,
    ctypes.POINTER(wintypes.FILETIME), ctypes.POINTER(wintypes.FILETIME),
    ctypes.POINTER(wintypes.FILETIME), ctypes.POINTER(wintypes.FILETIME),
]
_k32.CloseHandle.argtypes = [wintypes.HANDLE]


def _filetime_to_seconds(ft: wintypes.FILETIME) -> float:
    return (ft.dwHighDateTime << 32 | ft.dwLowDateTime) / 1e7


def process_cpu_seconds(pid: int) -> float:
    """进程累计 CPU（user+kernel）秒数。"""
    handle = _k32.OpenProcess(_PROCESS_QUERY_LIMITED_INFORMATION, False, pid)
    if not handle:
        raise OSError(f"OpenProcess failed for pid={pid}, err={ctypes.get_last_error()}")
    try:
        creation, exit_t, kernel, user = (
            wintypes.FILETIME(), wintypes.FILETIME(), wintypes.FILETIME(), wintypes.FILETIME())
        if not _k32.GetProcessTimes(handle, ctypes.byref(creation), ctypes.byref(exit_t),
                                    ctypes.byref(kernel), ctypes.byref(user)):
            raise OSError(f"GetProcessTimes failed, err={ctypes.get_last_error()}")
        return _filetime_to_seconds(kernel) + _filetime_to_seconds(user)
    finally:
        _k32.CloseHandle(handle)


# ---------------------------------------------------------------- 负载
def login():
    import requests
    password = os.environ.get("ADMIN_PASS", "")
    if not password:
        raise RuntimeError("Set ADMIN_PASS before running this measurement.")
    r = requests.post(f"{BASE}/api/auth/login",
                      json={"username": os.environ.get("ADMIN_USER", "admin"),
                            "password": password}, timeout=20)
    r.raise_for_status()
    return r.json()["token"]


async def load(path: str, encoding: str, token: str, duration: float):
    """固定并发跑 duration 秒，返回 (请求数, 延迟列表, 状态计数)。"""
    headers = {"Accept-Encoding": encoding, "Authorization": f"Bearer {token}"}
    lat: list[float] = []
    statuses: dict[int, int] = {}
    deadline = time.perf_counter() + duration
    lock = asyncio.Lock()

    async with aiohttp.ClientSession(
            connector=aiohttp.TCPConnector(limit=0),
            timeout=aiohttp.ClientTimeout(total=60)) as session:

        async def worker():
            while time.perf_counter() < deadline:
                t0 = time.perf_counter()
                try:
                    async with session.get(f"{BASE}{path}", headers=headers) as r:
                        await r.read()
                        dt = (time.perf_counter() - t0) * 1000
                        async with lock:
                            lat.append(dt)
                            statuses[r.status] = statuses.get(r.status, 0) + 1
                except Exception:  # noqa: BLE001
                    async with lock:
                        statuses[-1] = statuses.get(-1, 0) + 1

        await asyncio.gather(*[worker() for _ in range(CONCURRENCY)])
    return lat, statuses


def stats(lat: list[float]):
    if not lat:
        return (0, 0, 0)
    s = sorted(lat)
    return (statistics.median(s), s[min(len(s) - 1, int(len(s) * 0.95))], max(s))


async def main():
    if len(sys.argv) < 2:
        print("usage: measure_gzip_cpu.py <java_pid>")
        return 2
    pid = int(sys.argv[1])
    token = login()
    print(f"java pid={pid}  并发={CONCURRENCY}  每格 {DURATION}s（预热 {WARMUP}s）")
    print(f"{'端点':<34} {'模式':<9} {'请求数':>7} {'RPS':>8} {'p50':>7} {'p95':>8} "
          f"{'javaCPU秒':>10} {'CPUms/req':>10}")
    print("-" * 108)

    rows = []
    for path, label in ENDPOINTS:
        for encoding in ("identity", "gzip"):
            await load(path, encoding, token, WARMUP)          # 预热（填充缓存/连接/JIT）
            cpu0 = process_cpu_seconds(pid)
            t0 = time.perf_counter()
            lat, statuses = await load(path, encoding, token, DURATION)
            dur = time.perf_counter() - t0
            cpu1 = process_cpu_seconds(pid)

            cpu = cpu1 - cpu0
            n = len(lat)
            p50, p95, pmax = stats(lat)
            rps = n / dur if dur else 0
            cpu_ms_per_req = (cpu * 1000 / n) if n else float("nan")
            print(f"{label:<34} {encoding:<9} {n:>7} {rps:>8.0f} {p50:>7.1f} {p95:>8.1f} "
                  f"{cpu:>10.2f} {cpu_ms_per_req:>10.3f}")
            rows.append({"path": path, "label": label, "encoding": encoding, "n": n,
                         "rps": round(rps, 1), "p50": round(p50, 1), "p95": round(p95, 1),
                         "java_cpu_s": round(cpu, 3), "cpu_ms_per_req": round(cpu_ms_per_req, 4),
                         "statuses": {str(k): v for k, v in statuses.items()}})

    print("-" * 108)
    for path, label in ENDPOINTS:
        ident = next(r for r in rows if r["path"] == path and r["encoding"] == "identity")
        gz = next(r for r in rows if r["path"] == path and r["encoding"] == "gzip")
        ratio = gz["cpu_ms_per_req"] / ident["cpu_ms_per_req"] if ident["cpu_ms_per_req"] else 0
        print(f"{label:<34} gzip/identity java CPU 比 = {ratio:.2f}x  "
              f"({ident['cpu_ms_per_req']:.3f} → {gz['cpu_ms_per_req']:.3f} ms/req)")

    with open("logs/gzip_cpu_ab.json", "w", encoding="utf-8") as fh:
        json.dump(rows, fh, ensure_ascii=False, indent=2)
    print("\n已写入 logs/gzip_cpu_ab.json")
    return 0


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
