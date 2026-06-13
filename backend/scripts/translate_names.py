#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
批量翻译球员名
输入: JSON数组 ["name1", "name2", ...]
输出: JSON对象 {"name1": "中文1", "name2": "中文2", ...}
"""

import json
import os
import sys

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

os.environ.setdefault('DISABLE_SSL_VERIFY', 'true')
os.environ.setdefault('USE_PROXY', 'false')

from nba_data_fetcher import PLAYER_CN_MAP

try:
    from translator import translate_batch
    HAS_TRANSLATOR = True
except ImportError:
    HAS_TRANSLATOR = False


def translate_names(names):
    """翻译球员名列表"""
    result = {}
    unmapped = []

    for name in names:
        if not name:
            continue
        cn = PLAYER_CN_MAP.get(name, '')
        if cn:
            result[name] = cn
        else:
            unmapped.append(name)

    # 用API翻译未映射的
    if unmapped and HAS_TRANSLATOR:
        try:
            translated = translate_batch(unmapped)
            for en, cn in zip(unmapped, translated):
                if cn and cn != en:
                    result[en] = cn
        except Exception as e:
            print(f"翻译失败: {e}", file=sys.stderr)

    return result


def main():
    if len(sys.argv) > 1:
        arg = sys.argv[1]
        # 检查是否是文件路径
        if os.path.isfile(arg):
            with open(arg, 'r', encoding='utf-8') as f:
                names = json.load(f)
        else:
            # 作为命令行参数列表
            names = sys.argv[1:]
    else:
        # 从stdin读取JSON
        raw = sys.stdin.read().strip()
        if raw:
            names = json.loads(raw)
        else:
            names = []

    if not names:
        print('[]')
        return

    result = translate_names(names)
    # 输出JSON数组（保持输入顺序）
    output = [result.get(name, '') for name in names]
    print(json.dumps(output, ensure_ascii=False))


if __name__ == '__main__':
    main()
