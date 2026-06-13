#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
交易详情翻译脚本
使用MIMO Agent总结并翻译NBA交易详情
输入: JSON数组 [{"id": 1, "details": "Acquired G Chris Paul from..."}]
输出: JSON数组 [{"id": 1, "detailsCn": "球队从...获得球员..."}]
"""

import json
import os
import sys
import time

# 确保UTF-8输出
if sys.platform == 'win32':
    import io
    try:
        if hasattr(sys.stdout, 'buffer') and not isinstance(sys.stdout, io.TextIOWrapper):
            sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    except (AttributeError, ValueError):
        pass

sys.path.insert(0, os.path.dirname(__file__))

# 加载.env
env_path = os.path.join(os.path.dirname(__file__), '..', '..', '.env')
if os.path.exists(env_path):
    with open(env_path, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith('#') and '=' in line:
                k, v = line.split('=', 1)
                os.environ.setdefault(k.strip(), v.strip())

from translator import translate_text

# 从 nba_data_fetcher.py 导入映射表，避免重复维护
from nba_data_fetcher import TEAM_NAME_MAP, PLAYER_CN_MAP

def translate_trade_details(trades):
    """翻译交易详情列表"""
    result = []

    for trade in trades:
        trade_id = trade.get('id', 0)
        details = trade.get('details', '')
        details_en = trade.get('detailsEn', details)

        if not details:
            result.append({'id': trade_id, 'detailsCn': ''})
            continue

        # 使用本地映射替换已知的球员和球队名
        cn_details = details_en
        for en_name, cn_name in PLAYER_CN_MAP.items():
            cn_details = cn_details.replace(en_name, cn_name)
        for en_name, cn_name in TEAM_NAME_MAP.items():
            cn_details = cn_details.replace(en_name, cn_name)

        # 检查是否需要API翻译（还有英文内容）
        import re
        has_english = bool(re.search(r'[a-zA-Z]{3,}', cn_details))

        if has_english:
            # 调用MIMO API进行翻译和总结
            prompt = f"""你是一个专业的NBA交易分析师。请将以下NBA交易/球员变动信息翻译并总结为中文。

要求：
1. 必须保留所有关键信息，不能省略任何球员名字、球队名字或选秀权
2. 交易球员时：明确说明哪支球队获得了哪位球员，从哪支球队获得
3. 签约球员时：明确说明哪支球队签约了哪位球员
4. 涉及选秀权时：必须提及具体的选秀权信息（年份、轮次）
5. 如果是多方交易，必须列出所有参与的球队和球员
6. 语言简洁但完整，不要遗漏任何细节

原始内容：
{details_en}

请直接输出翻译后的中文总结，不要添加额外说明："""

            translated = translate_text(prompt, max_retries=2)
            if translated and translated != prompt:
                cn_details = translated
            else:
                # API失败，使用本地翻译结果
                cn_details = f"交易详情：{cn_details[:200]}"

        result.append({'id': trade_id, 'detailsCn': cn_details})

    return result


def main():
    if len(sys.argv) > 1:
        # 从文件读取
        file_path = sys.argv[1]
        with open(file_path, 'r', encoding='utf-8') as f:
            trades = json.load(f)
    else:
        # 从stdin读取
        raw = sys.stdin.read().strip()
        if raw:
            trades = json.loads(raw)
        else:
            trades = []

    if not trades:
        print('[]')
        return

    result = translate_trade_details(trades)
    print(json.dumps(result, ensure_ascii=False))


if __name__ == '__main__':
    main()
