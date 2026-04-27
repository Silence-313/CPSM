#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
微博热搜榜单爬虫
使用 Cookie 认证，调用微博 Ajax API 获取实时热搜榜
Java 后端通过 subprocess 调用此脚本，读取 stdout 的 JSON 输出
"""

import json
import time
import sys
import os
import random
import requests

COOKIE_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "weibo_config.json")

_config_cache = None
session = requests.Session()


def load_config():
    global _config_cache
    if _config_cache is not None:
        return _config_cache
    try:
        with open(COOKIE_FILE, 'r', encoding='utf-8') as f:
            _config_cache = json.load(f)
        return _config_cache
    except Exception:
        return {}


def get_cookie():
    return load_config().get('cookie', '')


def get_xsrf():
    return load_config().get('xsrf', '')


def build_headers(referer='https://weibo.com/'):
    return {
        "Accept": "application/json, text/plain, */*",
        "Accept-Encoding": "gzip, deflate, br",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
        "Connection": "keep-alive",
        "Host": "weibo.com",
        "Referer": referer,
        "Sec-Fetch-Dest": "empty",
        "Sec-Fetch-Mode": "cors",
        "Sec-Fetch-Site": "same-origin",
        "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "X-Requested-With": "XMLHttpRequest",
        "X-XSRF-TOKEN": get_xsrf(),
        "Cookie": get_cookie()
    }


def fetch_hot_search():
    """获取微博实时热搜榜"""
    url = "https://weibo.com/ajax/side/hotSearch"
    headers = build_headers()

    time.sleep(random.uniform(0.5, 1.5))

    try:
        resp = session.get(url, headers=headers, timeout=15)
        if resp.status_code == 200:
            data = resp.json()
            if data.get('ok') == 1 and 'data' in data:
                realtime = data['data'].get('realtime', [])
                return realtime
            else:
                print(f"⚠️ 热搜API返回异常: {json.dumps(data, ensure_ascii=False)[:200]}", file=sys.stderr)
                return []
        else:
            print(f"❌ 请求热搜失败，状态码: {resp.status_code}", file=sys.stderr)
            return []
    except Exception as e:
        print(f"❌ 请求热搜异常: {e}", file=sys.stderr)
        return []


def format_output(realtime_items):
    """将热搜数据格式化为 Java 端易解析的 JSON 结构"""
    result = []
    for i, item in enumerate(realtime_items):
        word = item.get('word', '').strip()
        # 去掉首尾的 # 号
        word = word.strip('#')
        if not word:
            continue

        result.append({
            "rank": i + 1,
            "word": word,
            "num": item.get('num', 0),
            "category": item.get('category', ''),
            "word_scheme": item.get('word_scheme', ''),
        })
    return result


def main():
    if not os.path.exists(COOKIE_FILE):
        print(json.dumps({"error": "Cookie 文件不存在，请先运行 cookie_helper.py 登录微博", "data": []}, ensure_ascii=False))
        sys.exit(0)

    config = load_config()
    age_hours = (time.time() - os.path.getmtime(COOKIE_FILE)) / 3600
    if age_hours > 6:
        print(f"⚠️ Cookie 已存在 {age_hours:.1f} 小时，可能需要刷新", file=sys.stderr)

    realtime = fetch_hot_search()
    items = format_output(realtime)
    print(json.dumps({"data": items, "count": len(items), "update_time": config.get('update_time', '')}, ensure_ascii=False))


if __name__ == "__main__":
    main()
