#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
赛事资讯刷新脚本
功能：
  1. 翻译现有新闻数据为中文（translate 模式）
  2. 清空所有新闻并重新获取+翻译（rebuild 模式）

用法：
  python refresh_news.py translate   # 翻译现有数据
  python refresh_news.py rebuild     # 清空并重新获取
  python refresh_news.py             # 默认 rebuild

环境变量：
  ADMIN_USER  管理员用户名（默认 admin）
  ADMIN_PASS  管理员密码（默认 admin123）
  API_BASE    后端地址（默认 http://localhost:8080/api）
"""

import json
import os
import sys
import time
import urllib.request
import urllib.error

# 确保UTF-8输出
if sys.platform == 'win32':
    import io
    try:
        if hasattr(sys.stdout, 'buffer') and not isinstance(sys.stdout, io.TextIOWrapper):
            sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
        if hasattr(sys.stderr, 'buffer') and not isinstance(sys.stderr, io.TextIOWrapper):
            sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')
    except (AttributeError, ValueError):
        pass

sys.path.insert(0, os.path.dirname(__file__))

from nba_data_fetcher import (
    translate_with_mapping,
    needs_translation,
    fetch_nba_news,
    TEAM_NAME_MAP,
    TEAM_SHORT_MAP,
)

try:
    from translator import translate_text, translate_batch
    HAS_API_TRANSLATOR = True
except ImportError:
    HAS_API_TRANSLATOR = False

API_BASE = os.environ.get('API_BASE', 'http://localhost:8080/api')
AUTH_TOKEN = None


# ==================== 认证 ====================

def login(username=None, password=None):
    """登录获取JWT token"""
    global AUTH_TOKEN
    username = username or os.environ.get('ADMIN_USER', 'admin')
    password = password or os.environ.get('ADMIN_PASS', '')

    url = f"{API_BASE}/auth/login"
    data = json.dumps({'username': username, 'password': password}).encode('utf-8')
    req = urllib.request.Request(url, data=data, headers={'Content-Type': 'application/json'})

    try:
        # 使用自定义opener避免全局代理干扰
        opener = urllib.request.build_opener()
        resp = opener.open(req, timeout=15)
        result = json.loads(resp.read().decode('utf-8'))
        AUTH_TOKEN = result.get('token', '')
        user = result.get('username', '')
        role = result.get('role', '')
        print(f"登录成功: {user} ({role})")
        return True
    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8', errors='replace')
        print(f"登录失败 [{e.code}]: {body[:200]}", file=sys.stderr)
        return False
    except Exception as e:
        print(f"登录失败: {e}", file=sys.stderr)
        return False


# ==================== API ====================

def api_request(method, path, data=None):
    """发送带认证的API请求（不使用代理）"""
    global AUTH_TOKEN
    url = f"{API_BASE}{path}"
    headers = {'Content-Type': 'application/json'}
    if AUTH_TOKEN:
        headers['Authorization'] = f'Bearer {AUTH_TOKEN}'

    try:
        if data is not None:
            body = json.dumps(data).encode('utf-8')
            req = urllib.request.Request(url, data=body, headers=headers)
        else:
            req = urllib.request.Request(url, headers=headers)

        req.get_method = lambda: method
        # 不使用代理访问本地API
        opener = urllib.request.build_opener()
        response = opener.open(req, timeout=30)
        raw = response.read().decode('utf-8')
        return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8', errors='replace')
        print(f"  API错误 [{e.code}] {method} {path}: {body[:200]}", file=sys.stderr)
        return None
    except Exception as e:
        print(f"  请求失败 {method} {path}: {e}", file=sys.stderr)
        return None


def fetch_all_news(page=0, size=200):
    """获取所有新闻"""
    result = api_request('GET', f'/news?page={page}&size={size}')
    if result and 'content' in result:
        return result['content']
    return []


def update_news_item(news_id, data):
    """更新新闻"""
    return api_request('PUT', f'/news/{news_id}', data)


def delete_all_news():
    """清空所有新闻"""
    result = api_request('DELETE', '/news/all')
    if result and 'deleted' in result:
        return result['deleted']
    # 回退逐条删除
    news_list = fetch_all_news()
    if not news_list:
        return 0
    deleted = 0
    for news in news_list:
        if api_request('DELETE', f'/news/{news.get("id")}'):
            deleted += 1
    return deleted


# ==================== 翻译 ====================

def translate_field(text, use_api=True):
    """翻译字段：先本地映射，再API翻译"""
    if not text or not text.strip():
        return text
    translated = translate_with_mapping(text)
    if use_api and HAS_API_TRANSLATOR and needs_translation(translated):
        try:
            api_result = translate_text(translated)
            if api_result and api_result != translated:
                translated = api_result
        except Exception as e:
            print(f"  API翻译失败: {e}", file=sys.stderr)
    return translated


def translate_news_item(news):
    """翻译一条新闻的所有字段"""
    updates = {}
    changed = False

    for field in ['title', 'summary', 'content']:
        original = news.get(field, '')
        if not original or not original.strip():
            continue
        translated = translate_field(original)
        if translated != original:
            updates[field] = translated
            changed = True

    for field in ['homeTeam', 'awayTeam']:
        val = news.get(field, '')
        if not val:
            continue
        cn = TEAM_NAME_MAP.get(val, TEAM_SHORT_MAP.get(val, ''))
        if cn and cn != val:
            updates[field] = cn
            changed = True

    return updates if changed else None


# ==================== 主逻辑 ====================

def do_translate():
    """翻译现有新闻"""
    print("=" * 50)
    print("Mode: translate (翻译现有数据)")
    print("=" * 50)

    news_list = fetch_all_news()
    if not news_list:
        print("没有找到新闻数据")
        return

    print(f"获取到 {len(news_list)} 条新闻\n")

    ok = skip = fail = 0
    for i, news in enumerate(news_list):
        nid = news.get('id')
        title = news.get('title', '')
        print(f"[{i+1}/{len(news_list)}] #{nid} {title[:40]}...")

        updates = translate_news_item(news)
        if updates:
            result = update_news_item(nid, updates)
            if result:
                ok += 1
                for k, v in updates.items():
                    print(f"  -> {k}: {str(v)[:60]}")
            else:
                fail += 1
                print(f"  !! 更新失败")
        else:
            skip += 1
            print(f"  -- 跳过(已是中文)")

    print(f"\n完成: 成功 {ok}, 跳过 {skip}, 失败 {fail}")


def do_rebuild():
    """清空并重新获取"""
    print("=" * 50)
    print("Mode: rebuild (清空并重新获取)")
    print("=" * 50)

    # 1. 清空
    print("\n[1/3] 清空现有新闻...")
    deleted = delete_all_news()
    print(f"  已删除 {deleted} 条")

    # 2. 从ESPN获取+翻译（仅本地映射，不调用API翻译）
    print("\n[2/3] 从ESPN获取最新新闻...")
    import nba_data_fetcher as _fetcher
    _orig_has = _fetcher.HAS_TRANSLATOR
    _fetcher.HAS_TRANSLATOR = False
    fresh = fetch_nba_news(limit=20)
    _fetcher.HAS_TRANSLATOR = _orig_has
    if not fresh:
        print("  获取失败，请检查网络")
        return
    print(f"  获取到 {len(fresh)} 条")

    # 3. 保存
    print("\n[3/3] 保存到数据库...")
    saved = fail = 0
    now = time.strftime('%Y-%m-%dT%H:%M:%S')

    for i, news in enumerate(fresh):
        headline = news.get('headline', '')
        desc = news.get('description', '')
        content = news.get('content', '')

        print(f"  [{i+1}/{len(fresh)}] {headline[:50]}")

        payload = {
            'title': headline[:120],
            'summary': (desc or headline)[:300],
            'content': content or desc or headline,
            'homeTeam': news.get('homeTeam') or '待定',
            'awayTeam': news.get('awayTeam') or '待定',
            'homeScore': None,
            'awayScore': None,
            'gameStartTime': now,
            'gameEndTime': now,
            'status': 'FINISHED',
            'nbaGameId': news.get('nbaGameId') or None,
            'category': news.get('category', 'general'),
            'sourceUrl': news.get('sourceUrl') or None,
            'imageUrl': news.get('imageUrl') or None,
        }

        result = api_request('POST', '/news', payload)
        if result:
            saved += 1
        else:
            fail += 1

    print(f"\n完成: 保存 {saved}, 失败 {fail}")


def load_env():
    """加载.env文件并刷新翻译模块配置"""
    env_path = os.path.join(os.path.dirname(__file__), '..', '..', '.env')
    if os.path.exists(env_path):
        with open(env_path, 'r', encoding='utf-8') as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith('#') and '=' in line:
                    k, v = line.split('=', 1)
                    os.environ[k.strip()] = v.strip()

    # 默认禁用代理（MIMO API直连）
    os.environ.setdefault('USE_PROXY', 'false')
    os.environ.setdefault('DISABLE_SSL_VERIFY', 'true')

    # 刷新翻译模块的配置
    import translator as _t
    _t.MIMO_API_KEY = os.environ.get('MIMO_API_KEY', '')
    _t.MIMO_BASE_URL = os.environ.get('MIMO_BASE_URL', _t.MIMO_BASE_URL)
    _t.USE_PROXY = os.environ.get('USE_PROXY', 'false').lower() == 'true'
    _t.DISABLE_SSL_VERIFY = os.environ.get('DISABLE_SSL_VERIFY', 'false').lower() == 'true'


def main():
    load_env()

    mode = sys.argv[1] if len(sys.argv) > 1 else 'rebuild'

    if mode not in ('translate', 'rebuild'):
        print(f"未知模式: {mode}")
        print("用法: python refresh_news.py [translate|rebuild]")
        sys.exit(1)

    # 登录
    print("正在登录...")
    if not login():
        print("登录失败，请检查 ADMIN_USER / ADMIN_PASS 环境变量")
        sys.exit(1)

    if mode == 'translate':
        do_translate()
    else:
        do_rebuild()


if __name__ == '__main__':
    main()
