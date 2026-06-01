#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
更新数据库中的新闻数据
1. 翻译未翻译的内容（本地映射 + API翻译）
2. 补充nbaGameId
3. 修正球队名为中文

用法: python update_news.py
"""

import json
import os
import re
import sys
import urllib.request

# 确保UTF-8输出
if sys.platform == 'win32':
    import io
    try:
        if hasattr(sys.stdout, 'buffer'):
            sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
        if hasattr(sys.stderr, 'buffer'):
            sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')
    except (AttributeError, ValueError):
        pass

sys.path.insert(0, os.path.dirname(__file__))

from nba_data_fetcher import (
    translate_with_mapping,
    needs_translation,
    TEAM_NAME_MAP,
    TEAM_SHORT_MAP,
    PLAYER_CN_MAP,
)

try:
    from translator import translate_text
    HAS_API_TRANSLATOR = True
except ImportError:
    HAS_API_TRANSLATOR = False

# API配置
BASE_URL = os.environ.get('API_BASE', 'http://localhost:8080/api')


def fetch_news(page=0, size=100):
    """获取新闻列表"""
    url = f"{BASE_URL}/news?page={page}&size={size}"
    try:
        response = urllib.request.urlopen(url, timeout=10)
        return json.loads(response.read().decode('utf-8'))
    except Exception as e:
        print(f"获取新闻失败: {e}")
        return None


def update_news_item(news_id, data):
    """更新新闻"""
    url = f"{BASE_URL}/news/{news_id}"
    try:
        req = urllib.request.Request(url, data=json.dumps(data).encode('utf-8'), headers={
            'Content-Type': 'application/json'
        })
        req.get_method = lambda: 'PUT'
        response = urllib.request.urlopen(req, timeout=10)
        return json.loads(response.read().decode('utf-8'))
    except Exception as e:
        print(f"更新新闻 {news_id} 失败: {e}")
        return None


def translate_field(text):
    """翻译字段：先本地映射，再API翻译"""
    if not text or not text.strip():
        return text

    translated = translate_with_mapping(text)

    if HAS_API_TRANSLATOR and needs_translation(translated):
        try:
            api_result = translate_text(translated)
            if api_result and api_result != translated:
                translated = api_result
        except Exception as e:
            print(f"  API翻译失败: {e}")

    return translated


def main():
    print("开始更新新闻数据...")
    print(f"API地址: {BASE_URL}")
    print(f"API翻译: {'可用' if HAS_API_TRANSLATOR else '不可用'}\n")

    result = fetch_news(page=0, size=200)
    if not result:
        print("获取新闻失败")
        return

    news_list = result.get('content', [])
    print(f"获取到 {len(news_list)} 条新闻\n")

    updated_count = 0
    skipped_count = 0

    for i, news in enumerate(news_list):
        news_id = news.get('id')
        title = news.get('title', '')
        summary = news.get('summary', '')
        content = news.get('content', '')
        home_team = news.get('homeTeam', '')
        away_team = news.get('awayTeam', '')
        nba_game_id = news.get('nbaGameId', '')
        source_url = news.get('sourceUrl', '')

        updates = {}
        needs_update = False

        # 1. 翻译标题
        new_title = translate_field(title)
        if new_title != title:
            updates['title'] = new_title[:120]
            needs_update = True

        # 2. 翻译摘要
        new_summary = translate_field(summary)
        if new_summary != summary:
            updates['summary'] = new_summary[:300]
            needs_update = True

        # 3. 翻译内容
        new_content = translate_field(content)
        if new_content != content:
            updates['content'] = new_content
            needs_update = True

        # 4. 修正球队名
        for field_name, field_val in [('homeTeam', home_team), ('awayTeam', away_team)]:
            if field_val:
                cn = TEAM_NAME_MAP.get(field_val, TEAM_SHORT_MAP.get(field_val, ''))
                if cn and cn != field_val:
                    updates[field_name] = cn
                    needs_update = True

        # 5. 补充nbaGameId
        if not nba_game_id and source_url:
            if 'gameId=' in source_url:
                game_match = re.search(r'gameId=(\d+)', source_url)
                if game_match:
                    updates['nbaGameId'] = game_match.group(1)
                    needs_update = True
            elif '/events/' in source_url:
                event_match = re.search(r'/events/(\d+)', source_url)
                if event_match:
                    updates['nbaGameId'] = event_match.group(1)
                    needs_update = True

        if needs_update:
            result = update_news_item(news_id, updates)
            if result:
                updated_count += 1
                print(f"[{i + 1}] ✅ #{news_id} {title[:40]}")
                for k, v in updates.items():
                    print(f"     {k}: {str(v)[:60]}")
            else:
                print(f"[{i + 1}] ❌ #{news_id} 更新失败")
        else:
            skipped_count += 1
            print(f"[{i + 1}] ⏭️ #{news_id} 无需更新")

    print(f"\n更新完成: 更新 {updated_count}, 跳过 {skipped_count}")


if __name__ == '__main__':
    main()
