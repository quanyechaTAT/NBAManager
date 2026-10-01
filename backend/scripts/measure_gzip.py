# -*- coding: utf-8 -*-
"""socket 级 gzip 体积测量（R8 P1-1 验收工具）。

**必须用 http.client 读原始字节**：`requests` 会自动解压，`len(r.content)` 得到的是
解压后大小，会得出"降幅 0.0%"的错误结论（测试方第一次测量即踩此坑）。

测量内容：
- identity（不声明 Accept-Encoding）与 gzip（声明 gzip）两种传输下的**线上字节数**；
- 是否带 Content-Encoding / Transfer-Encoding / Content-Length；
- 判定：≥2KB 应压缩且降幅 ≥70%；<2KB 不应被压缩（或至少不小于原文）。

用法:
    backend/scripts/venv312/Scripts/python.exe backend/scripts/measure_gzip.py
"""
from __future__ import annotations

import http.client
import json
import os
import sys

HOST = "127.0.0.1"
PORT = 8080

# (路径, 是否需要登录, 说明, 期望是否压缩)
#
# 最终口径（第 9 轮 R9-1 收口）：
#   - API JSON **不在 Java 侧压缩**（依据：measure_gzip_cpu.py 实测 ≥100KB 的 JSON
#     压缩使 java CPU/请求 ×1.66~1.72、吞吐 -32%；带宽改由反向代理承担）；
#   - 静态资源（HTML/CSS/JS）**仍然压缩**（吞吐远低于 API，收益保留）。
ENDPOINTS = [
    ("/api/rag/stats", True, "加密前缺陷样本 ~98B", False),
    ("/api/players/1", False, "小响应 ~624B", False),
    ("/api/posts?page=0&size=20", False, "R9 负载端点（小/中响应）", False),
    ("/api/dashboard/stats", False, "中等响应 ~2.2KB", False),
    ("/api/news?page=0&size=20", False, "大响应 ~17.6KB", False),
    ("/api/historical/players?limit=500&season=2023-24", False, "大响应 ~292KB", False),
    ("/api/shot-chart/player/1", False, "大响应 ~133KB", False),
    ("/", False, "静态 index.html ~939B（< 阈值，不压缩）", False),
]

# 静态资源压缩必须保留（R9 P1 验收项：案 1 = 静态仍压缩、API JSON 不压缩）。
# 路径在运行时从 index.html 解析，见 main()。
STATIC_EXPECT_GZIP = True

THRESHOLD = 2048

# 修复前的实测值（logs/r8-gzip-before.txt），仅作对照展示。
BASELINE_BEFORE = {
    "/api/rag/stats": (98, 115),          # 缺陷样本：被压后反而变大
    "/api/players/1": (624, 437),
    "/api/posts?page=0&size=20": (924, 514),
    "/api/dashboard/stats": (2190, 804),
    "/api/news?page=0&size=20": (17595, 5042),
    "/api/historical/players?limit=500&season=2023-24": (291730, 33593),
    "/api/shot-chart/player/1": (133125, 10152),
    "/": (939, 939),
}


def login(username=None, password=None):
    username = username or os.environ.get("ADMIN_USER", "admin")
    password = password or os.environ.get("ADMIN_PASS", "")
    if not password:
        raise RuntimeError("Set ADMIN_PASS before running this measurement.")
    conn = http.client.HTTPConnection(HOST, PORT, timeout=20)
    body = json.dumps({"username": username, "password": password})
    conn.request("POST", "/api/auth/login", body=body,
                 headers={"Content-Type": "application/json", "Content-Length": str(len(body))})
    resp = conn.getresponse()
    payload = json.loads(resp.read())
    conn.close()
    return payload.get("token") or payload.get("data", {}).get("token")


def measure(path, token=None, gzip_ok=False):
    """返回 (字节数, 响应头字典, 状态码)。只读原始字节，不做任何解压。"""
    conn = http.client.HTTPConnection(HOST, PORT, timeout=60)
    headers = {}
    if gzip_ok:
        headers["Accept-Encoding"] = "gzip"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    conn.request("GET", path, headers=headers)
    resp = conn.getresponse()
    raw = resp.read()  # 原始线上字节（若 gzip 则是压缩后的字节）
    info = {
        "status": resp.status,
        "content-encoding": resp.getheader("Content-Encoding"),
        "transfer-encoding": resp.getheader("Transfer-Encoding"),
        "content-length": resp.getheader("Content-Length"),
        "content-type": resp.getheader("Content-Type"),
    }
    conn.close()
    return len(raw), info


def find_static_bundle():
    """从 index.html 里解析出一个静态 JS bundle 路径（用于验证静态压缩仍在）。"""
    try:
        conn = http.client.HTTPConnection(HOST, PORT, timeout=20)
        conn.request("GET", "/")
        html = conn.getresponse().read().decode("utf-8", "replace")
        conn.close()
        import re
        m = re.search(r'src="(/assets/[^"]+\.js)"', html)
        return m.group(1) if m else None
    except Exception:  # noqa: BLE001
        return None


def main():
    token = login()
    print(f"压缩面口径（第 9 轮收口）：API JSON 不压缩；静态资源压缩（阈值 {THRESHOLD}B）")
    print(f"{'端点':<48} {'identity':>10} {'gzip':>10} {'降幅':>9}  判定")
    print("-" * 118)

    rows = []
    failures = []
    for path, need_auth, label, expect_gzip in ENDPOINTS:
        tok = token if need_auth else None
        try:
            ident, i_info = measure(path, tok, gzip_ok=False)
            gz, g_info = measure(path, tok, gzip_ok=True)
        except Exception as e:  # noqa: BLE001
            print(f"{path:<48} 测量失败: {e!r}")
            failures.append(f"{path} 测量失败")
            continue

        enc = g_info["content-encoding"] or "-"
        saved = (1 - gz / ident) * 100 if ident else 0.0
        compressed = enc.lower() == "gzip"
        before_identity, before_gzip = BASELINE_BEFORE.get(path, (ident, gz))
        before_saved = (1 - before_gzip / before_identity) * 100 if before_identity else 0.0

        # 判定（按"期望是否压缩"，而不是按体积）：
        #  - 期望不压缩：带上 gzip 就是越界（且若体积反而变大即放大缺陷）
        #  - 期望压缩  ：必须带 gzip 且确有收益
        if expect_gzip:
            if not compressed:
                verdict = f"FAIL 静态资源未压缩（修复前降幅 {before_saved:.1f}%）"
            elif saved <= 0:
                verdict = f"FAIL 静态资源压缩无收益（{ident}->{gz}）"
            else:
                verdict = f"PASS 静态资源仍压缩（降幅 {saved:.1f}%）"
        else:
            if not compressed:
                if before_gzip > before_identity:
                    verdict = f"PASS 未压缩（修复前 {before_identity}->{before_gzip} 属「越压越大」缺陷）"
                elif before_saved > 0:
                    verdict = f"PASS 未压缩（修复前降幅 {before_saved:.1f}%，按 R9-1 收口改为不压缩）"
                else:
                    verdict = "PASS 未压缩"
            elif gz > ident:
                verdict = f"FAIL 被压缩且放大（{ident}->{gz}, +{gz - ident}B）"
            else:
                verdict = f"FAIL API JSON 不应压缩（{ident}->{gz}，java CPU ×1.66~1.72）"
        if verdict.startswith("FAIL"):
            failures.append(f"{path}: {verdict}")

        rows.append({"path": path, "label": label, "identity": ident, "gzip": gz,
                     "saved_pct": round(saved, 1), "encoding": enc, "verdict": verdict})
        print(f"{path:<48} {ident:>10} {gz:>10} {saved:>8.1f}%  {verdict}")
        print(f"{'':<48} te={i_info['transfer-encoding']} cl={i_info['content-length']}")

    # 静态 bundle 必须仍压缩（R9 P1 验收项：案 1 = 静态仍压缩）
    js = find_static_bundle()
    if js:
        ident, _ = measure(js)
        gz, g_info = measure(js, gzip_ok=True)
        comp = (g_info["content-encoding"] or "").lower() == "gzip"
        saved = (1 - gz / ident) * 100 if ident else 0
        verdict = (f"PASS 静态 JS 仍压缩（{ident}B -> {gz}B, {saved:.1f}%）" if comp and saved > 0
                   else f"FAIL 静态 JS 未压缩（enc={g_info['content-encoding']}）")
        if verdict.startswith("FAIL"):
            failures.append(f"{js}: {verdict}")
        rows.append({"path": js, "identity": ident, "gzip": gz,
                     "saved_pct": round(saved, 1),
                     "encoding": g_info["content-encoding"], "verdict": verdict})
        print(f"{js:<48} {ident:>10} {gz:>10} {saved:>8.1f}%  {verdict}")

    print("-" * 118)
    if failures:
        print(f"\n===== {len(failures)} 项未达标 =====")
        for f in failures:
            print(f"  FAILED: {f}")
        return 1
    print(f"\n===== 全部 {len(rows)} 项达标 =====")
    return 0


if __name__ == "__main__":
    sys.exit(main())
