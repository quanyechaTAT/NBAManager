#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""用curl调用MIMO API翻译新闻（绕过Python urllib SSL问题）"""

import json
import os
import subprocess
import sys

API = 'http://localhost:8080/api'
MIMO_KEY = os.environ.get('MIMO_API_KEY', '')


def curl_json(method, url, data=None, headers=None):
    """用curl发JSON请求"""
    cmd = ['curl', '-s', '-k', '--max-time', '60', '-X', method, url]
    if headers:
        for k, v in headers.items():
            cmd += ['-H', f'{k}: {v}']
    if data is not None:
        cmd += ['-d', json.dumps(data, ensure_ascii=False)]
    result = subprocess.run(cmd, capture_output=True, timeout=70)
    out = result.stdout.decode('utf-8', errors='replace')
    return json.loads(out) if out else None


def translate(text):
    """用MIMO API翻译"""
    if not text or len(text) < 5:
        return text
    payload = {
        'model': 'mimo-v2.5',
        'messages': [
            {'role': 'system', 'content': 'NBA体育新闻翻译专家。球员用中文名，球队用中文名，术语用中文。只返回翻译。'},
            {'role': 'user', 'content': f'翻译为中文：{text}'}
        ],
        'temperature': 0.2,
        'max_tokens': 4000
    }
    try:
        result = curl_json('POST', 'https://token-plan-cn.xiaomimimo.com/v1/chat/completions', payload, {
            'Content-Type': 'application/json',
            'Authorization': f'Bearer {MIMO_KEY}'
        })
        if result and 'choices' in result and result['choices']:
            return result['choices'][0]['message']['content'].strip()
    except Exception as e:
        print(f'  translate error: {e}', file=sys.stderr)
    return text


def main():
    # Login
    login = curl_json('POST', f'{API}/auth/login',
                      {'username': os.environ.get('ADMIN_USER', 'admin'), 'password': os.environ.get('ADMIN_PASS', 'admin')},
                      {'Content-Type': 'application/json'})
    if not login or 'token' not in login:
        print('Login failed')
        sys.exit(1)
    token = login['token']
    auth = {'Authorization': f'Bearer {token}'}

    # Get all news
    resp = curl_json('GET', f'{API}/news?page=0&size=100', headers=auth)
    news_list = resp.get('content', [])
    print(f'Found {len(news_list)} news')

    ok = 0
    for i, news in enumerate(news_list):
        nid = news['id']
        title = news.get('title', '')

        # Build full payload
        payload = {
            'title': title,
            'summary': news.get('summary', ''),
            'content': news.get('content', ''),
            'homeTeam': news.get('homeTeam') or '待定',
            'awayTeam': news.get('awayTeam') or '待定',
            'homeScore': news.get('homeScore'),
            'awayScore': news.get('awayScore'),
            'gameStartTime': news.get('gameStartTime'),
            'gameEndTime': news.get('gameEndTime'),
            'status': news.get('status', 'FINISHED'),
            'nbaGameId': news.get('nbaGameId'),
            'category': news.get('category', 'general'),
            'sourceUrl': news.get('sourceUrl'),
            'imageUrl': news.get('imageUrl'),
        }

        # Translate
        changed = False
        for field in ['title', 'summary', 'content']:
            val = payload.get(field, '')
            if not val:
                continue
            translated = translate(val)
            if translated and translated != val:
                payload[field] = translated
                changed = True

        if changed:
            try:
                curl_json('PUT', f'{API}/news/{nid}', payload,
                          {'Content-Type': 'application/json', **auth})
                ok += 1
                # Write result to file to avoid encoding issues
                with open('translate_log.txt', 'a', encoding='utf-8') as f:
                    f.write(f'[{i+1}] #{nid}: {payload["title"][:60]}\n')
                print(f'[{i+1}] OK #{nid}')
            except Exception as e:
                print(f'[{i+1}] FAIL #{nid}: {e}')
        else:
            print(f'[{i+1}] SKIP #{nid}')

    print(f'Done! Updated {ok}/{len(news_list)}')


if __name__ == '__main__':
    main()
