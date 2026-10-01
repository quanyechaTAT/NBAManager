# -*- coding: utf-8 -*-
"""补翻 game_news 中翻译失败（title 与原文相同）的新闻，直连 DB 更新。

由 TranslationSyncWorker/TranslationRetryService 定时调用作为翻译失败兜底；
也可手动执行。用法: venv python translate_pending_news.py [--dry-run]
前置: .env 提供 DEEPSEEK_API_KEY / DEEPSEEK_API_BASE / DB_*
"""
import io
import os
import sys
import time

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
env = {}
for line in io.open(os.path.join(ROOT, '.env'), encoding='utf-8'):
    line = line.strip()
    if line and not line.startswith('#') and '=' in line:
        k, v = line.split('=', 1)
        env[k] = v
os.environ.setdefault('DEEPSEEK_API_KEY', env.get('DEEPSEEK_API_KEY', ''))
os.environ.setdefault('DEEPSEEK_API_BASE', env.get('DEEPSEEK_API_BASE', 'https://api.deepseek.com'))

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from nba_data_fetcher import translate_with_mapping, needs_translation  # noqa: E402
import translator  # noqa: E402
import mysql.connector  # noqa: E402

DRY = '--dry-run' in sys.argv

conn = mysql.connector.connect(
    host=env.get('DB_HOST', 'localhost'), port=int(env.get('DB_PORT', 3306)),
    user=env['DB_USERNAME'], password=env['DB_PASSWORD'],
    database='nba_manager', charset='utf8mb4')
cur = conn.cursor()
cur.execute("SELECT id, title, summary, content, title_en FROM game_news "
            "WHERE title_en IS NOT NULL AND title = title_en")
rows = cur.fetchall()
print(f"待补翻新闻: {len(rows)} 条 (dry_run={DRY})")

ok = fail = 0
for idx, (nid, title, summary, content, title_en) in enumerate(rows):
    print(f"[{idx+1}/{len(rows)}] #{nid} {str(title)[:48]}")
    payload = {'headline': translate_with_mapping(title or '')}
    if summary:
        payload['description'] = translate_with_mapping(summary)
    if content:
        payload['content'] = content
    try:
        translated = {}
        for key, text in payload.items():
            if text and needs_translation(text):
                translated[key] = translator.translate_text(text)
            else:
                translated[key] = text
        new_title = translated.get('headline') or title
        new_summary = translated.get('description') or summary or ''
        new_content = translated.get('content') or content or ''
        if not any('\u4e00' <= c <= '\u9fff' for c in new_title):
            print("   !! 翻译仍非中文，跳过写入")
            fail += 1
            continue
        print(f"   -> {new_title[:52]}")
        if not DRY:
            cur.execute("UPDATE game_news SET title=%s, summary=%s, content=%s WHERE id=%s",
                        (new_title, new_summary, new_content, nid))
            conn.commit()
        ok += 1
    except Exception as exc:
        print(f"   !! 失败: {exc}")
        fail += 1
    time.sleep(0.3)

print(f"\n完成: 成功 {ok}, 失败 {fail}")
conn.close()
