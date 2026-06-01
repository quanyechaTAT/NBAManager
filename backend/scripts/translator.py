#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
翻译服务模块
使用mimo-v2.5模型进行文本翻译
API KEY从环境变量读取，确保安全
支持单条翻译、批量翻译、新闻文章翻译
"""

import os
import sys
import json
import ssl
import time
import urllib.request
import urllib.error

# 从环境变量读取配置（API KEY不会硬编码在代码中）
MIMO_API_KEY = os.environ.get('MIMO_API_KEY', '')
MIMO_BASE_URL = os.environ.get('MIMO_BASE_URL', 'https://token-plan-cn.xiaomimimo.com/v1')

# 代理配置
PROXY_HOST = os.environ.get('NBA_PROXY_HOST', '127.0.0.1')
PROXY_PORT = os.environ.get('NBA_PROXY_PORT', '7890')

# 是否使用代理（默认不使用，因为mimo API可能不需要代理）
USE_PROXY = os.environ.get('USE_PROXY', 'false').lower() == 'true'

# 是否禁用SSL验证（仅用于测试，生产环境应设置为false）
DISABLE_SSL_VERIFY = os.environ.get('DISABLE_SSL_VERIFY', 'false').lower() == 'true'

# 翻译缓存（避免重复翻译相同文本）
_translation_cache = {}

# 上次API调用时间（用于限流）
_last_api_call = 0.0
_MIN_CALL_INTERVAL = 0.5  # 最小调用间隔（秒）

# 系统提示词（优化翻译质量）
SYSTEM_PROMPT = """你是一个专业的NBA体育新闻翻译专家。请将英文NBA新闻翻译为中文。

翻译规则：
1. 球员姓名使用标准中文译名（如：LeBron James → 勒布朗·詹姆斯，Stephen Curry → 斯蒂芬·库里）
2. 球队名称使用标准中文名（如：Lakers → 湖人，Warriors → 勇士，Celtics → 凯尔特人）
3. NBA专业术语使用中文习惯表达（如：triple-double → 三双，buzzer-beater → 压哨球，MVP → 最有价值球员）
4. 保持新闻的简洁和专业风格
5. 分数格式保持 "比分 比分" 的形式
6. 只返回翻译结果，不要添加任何解释或注释"""


def _get_opener():
    """获取URL opener（根据代理配置）"""
    if USE_PROXY:
        proxy_url = f'http://{PROXY_HOST}:{PROXY_PORT}'
        proxy_handler = urllib.request.ProxyHandler({
            'https': proxy_url,
            'http': proxy_url,
        })
        opener = urllib.request.build_opener(proxy_handler)
    else:
        opener = urllib.request.build_opener()

    if DISABLE_SSL_VERIFY:
        ssl_context = ssl.create_default_context()
        ssl_context.check_hostname = False
        ssl_context.verify_mode = ssl.CERT_NONE
        https_handler = urllib.request.HTTPSHandler(context=ssl_context)
        opener.add_handler(https_handler)

    return opener


def _rate_limit():
    """API调用限流"""
    global _last_api_call
    now = time.time()
    elapsed = now - _last_api_call
    if elapsed < _MIN_CALL_INTERVAL:
        time.sleep(_MIN_CALL_INTERVAL - elapsed)
    _last_api_call = time.time()


def translate_text(text, source_lang='en', target_lang='zh', max_retries=2):
    """
    使用mimo-v2.5模型翻译文本

    Args:
        text: 要翻译的文本
        source_lang: 源语言，默认英语
        target_lang: 目标语言，默认中文
        max_retries: 最大重试次数

    Returns:
        翻译后的文本，失败时返回原文
    """
    if not text or not text.strip():
        return text

    # 检查缓存
    cache_key = text.strip()
    if cache_key in _translation_cache:
        return _translation_cache[cache_key]

    if not MIMO_API_KEY:
        print("警告: MIMO_API_KEY未设置，跳过翻译。请设置环境变量 MIMO_API_KEY", file=sys.stderr)
        return text

    for attempt in range(max_retries + 1):
        try:
            _rate_limit()

            url = f"{MIMO_BASE_URL}/chat/completions"

            prompt = f"请将以下{source_lang}文本翻译为{target_lang}：\n\n{text}"

            payload = {
                "model": "mimo-v2.5",
                "messages": [
                    {"role": "system", "content": SYSTEM_PROMPT},
                    {"role": "user", "content": prompt}
                ],
                "temperature": 0.2,
                "max_tokens": 4000
            }

            data = json.dumps(payload).encode('utf-8')

            req = urllib.request.Request(url, data=data, headers={
                'Content-Type': 'application/json',
                'Authorization': f'Bearer {MIMO_API_KEY}',
                'User-Agent': 'NBAManager/1.0'
            })

            opener = _get_opener()
            response = opener.open(req, timeout=45)
            result = json.loads(response.read().decode('utf-8'))

            if 'choices' in result and len(result['choices']) > 0:
                translated = result['choices'][0]['message']['content'].strip()
                # 缓存结果
                _translation_cache[cache_key] = translated
                return translated

            return text

        except Exception as e:
            if attempt < max_retries:
                wait_time = 2 ** attempt
                print(f"翻译尝试 {attempt + 1} 失败，{wait_time}秒后重试: {e}", file=sys.stderr)
                time.sleep(wait_time)
            else:
                print(f"翻译失败: {e}", file=sys.stderr)
                return text

    return text


def translate_batch(texts, source_lang='en', target_lang='zh'):
    """
    批量翻译文本列表（合并为单次API调用，节省请求次数）

    Args:
        texts: 文本列表
        source_lang: 源语言
        target_lang: 目标语言

    Returns:
        翻译后的文本列表
    """
    if not texts:
        return []

    # 过滤空文本，记录位置
    results = [''] * len(texts)
    to_translate = []
    indices = []

    for i, text in enumerate(texts):
        if not text or not text.strip():
            results[i] = text
            continue
        # 检查缓存
        cache_key = text.strip()
        if cache_key in _translation_cache:
            results[i] = _translation_cache[cache_key]
        else:
            to_translate.append(text)
            indices.append(i)

    if not to_translate:
        return results

    if not MIMO_API_KEY:
        print("警告: MIMO_API_KEY未设置，跳过批量翻译", file=sys.stderr)
        return texts

    # 将多个文本合并为一次请求
    numbered_texts = []
    for idx, text in enumerate(to_translate):
        numbered_texts.append(f"[{idx + 1}] {text}")

    combined = "\n\n".join(numbered_texts)
    prompt = f"""请将以下{source_lang}文本逐条翻译为{target_lang}。
每条文本前有编号标记（如[1]、[2]），请保持相同的编号格式返回翻译结果。
保持编号和翻译的对应关系，不要遗漏任何一条。

{combined}"""

    try:
        _rate_limit()

        url = f"{MIMO_BASE_URL}/chat/completions"

        payload = {
            "model": "mimo-v2.5",
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": prompt}
            ],
            "temperature": 0.2,
            "max_tokens": 8000
        }

        data = json.dumps(payload).encode('utf-8')

        req = urllib.request.Request(url, data=data, headers={
            'Content-Type': 'application/json',
            'Authorization': f'Bearer {MIMO_API_KEY}',
            'User-Agent': 'NBAManager/1.0'
        })

        opener = _get_opener()
        response = opener.open(req, timeout=90)
        result = json.loads(response.read().decode('utf-8'))

        if 'choices' in result and len(result['choices']) > 0:
            translated_block = result['choices'][0]['message']['content'].strip()
            # 解析编号格式的翻译结果
            translated_items = _parse_numbered_translations(translated_block, len(to_translate))

            for i, (orig, trans) in enumerate(zip(to_translate, translated_items)):
                if trans:
                    results[indices[i]] = trans
                    _translation_cache[orig.strip()] = trans
                else:
                    results[indices[i]] = orig
        else:
            # 解析失败，使用原文
            for i in indices:
                results[i] = texts[i]

    except Exception as e:
        print(f"批量翻译失败: {e}", file=sys.stderr)
        # 回退：逐条翻译
        for i, text in enumerate(to_translate):
            results[indices[i]] = translate_text(text, source_lang, target_lang)

    return results


def _parse_numbered_translations(text, expected_count):
    """
    解析带编号的翻译结果

    Args:
        text: 带编号的翻译文本
        expected_count: 期望的条目数

    Returns:
        翻译结果列表
    """
    import re
    results = []

    # 尝试按编号分割 [1], [2], etc.
    pattern = r'\[(\d+)\]\s*'
    parts = re.split(pattern, text)

    # parts 格式: ['', '1', '翻译1', '2', '翻译2', ...]
    translation_map = {}
    for i in range(1, len(parts) - 1, 2):
        try:
            num = int(parts[i])
            content = parts[i + 1].strip()
            translation_map[num] = content
        except (ValueError, IndexError):
            continue

    # 按顺序提取
    for i in range(1, expected_count + 1):
        results.append(translation_map.get(i, ''))

    # 如果解析失败，尝试按换行分割
    if len([r for r in results if r]) < expected_count * 0.5:
        lines = [line.strip() for line in text.split('\n') if line.strip()]
        if len(lines) >= expected_count:
            results = lines[:expected_count]

    return results


def translate_news_article(article):
    """
    翻译新闻文章（批量翻译标题、描述、内容）

    Args:
        article: 新闻文章字典，包含headline, description, content等字段

    Returns:
        翻译后的文章字典
    """
    if not article:
        return article

    translated = article.copy()

    # 收集需要翻译的字段
    fields_to_translate = []
    field_names = []

    for field in ['headline', 'description', 'content']:
        value = translated.get(field, '')
        if value and value.strip():
            fields_to_translate.append(value)
            field_names.append(field)

    if not fields_to_translate:
        return translated

    # 批量翻译
    translated_values = translate_batch(fields_to_translate)

    # 写回结果
    for field_name, value in zip(field_names, translated_values):
        translated[field_name] = value

    return translated


def main():
    """测试翻译功能"""
    if len(sys.argv) < 2:
        print("用法: python translator.py <text_to_translate>")
        print("示例: python translator.py 'Victor Wembanyama leads Spurs to victory'")
        print("")
        print("批量翻译: python translator.py --batch 'text1' 'text2' 'text3'")
        print("")
        print("环境变量配置:")
        print("  MIMO_API_KEY - API密钥（必需）")
        print("  MIMO_BASE_URL - API地址（默认: https://token-plan-cn.xiaomimimo.com/v1）")
        print("  USE_PROXY - 是否使用代理（默认: false）")
        print("  NBA_PROXY_HOST - 代理主机（默认: 127.0.0.1）")
        print("  NBA_PROXY_PORT - 代理端口（默认: 7890）")
        sys.exit(1)

    if sys.argv[1] == '--batch':
        # 批量翻译模式
        texts = sys.argv[2:]
        if not texts:
            print("请提供要翻译的文本")
            sys.exit(1)
        print(f"批量翻译 {len(texts)} 条文本...")
        results = translate_batch(texts)
        for i, (orig, trans) in enumerate(zip(texts, results)):
            print(f"\n[{i + 1}] 原文: {orig}")
            print(f"    译文: {trans}")
    else:
        # 单条翻译模式
        text = sys.argv[1]
        print(f"原文: {text}")
        translated = translate_text(text)
        print(f"译文: {translated}")


if __name__ == '__main__':
    main()
