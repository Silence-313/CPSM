#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
微博内容爬虫 — 获取微博正文详情 + 评论列表
使用 Cookie 认证（替代权限不足的官方 API statuses/show 和 comments/show）
Java 后端通过 subprocess 调用，读取 stdout 的 JSON 输出

用法:
  python weibo_content_spider.py status <mid>          # 获取微博正文详情
  python weibo_content_spider.py comments <mid> [page] [count]  # 获取评论列表（默认第1页，每页100条）
  python weibo_content_spider.py repostTimeline <mid> [page] [count]  # 获取转发列表（默认第1页，每页20条）
"""

import json
import time
import sys
import os
import re
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


def fetch_status(mid):
    """获取微博正文详情（通过 Ajax API）"""
    url = f"https://weibo.com/ajax/statuses/show?id={mid}"
    headers = build_headers(f"https://weibo.com/{mid}")
    time.sleep(random.uniform(0.5, 1.5))

    try:
        resp = session.get(url, headers=headers, timeout=15)
        if resp.status_code == 200:
            data = resp.json()
            if isinstance(data, dict) and 'user' in data:
                return {
                    "success": True,
                    "mid": str(data.get('id', mid)),
                    "text": data.get('text_raw', '') or data.get('text', ''),
                    "text_length": len(data.get('text_raw', '') or data.get('text', '')),
                    "user": {
                        "id": str(data.get('user', {}).get('id', '')),
                        "screen_name": data.get('user', {}).get('screen_name', ''),
                    },
                    "reposts_count": data.get('reposts_count', 0),
                    "comments_count": data.get('comments_count', 0),
                    "attitudes_count": data.get('attitudes_count', 0),
                    "created_at": data.get('created_at', ''),
                }
            else:
                return {"success": False, "error": "响应结构异常: " + json.dumps(data, ensure_ascii=False)[:200]}
        else:
            return {"success": False, "error": f"HTTP {resp.status_code}"}
    except Exception as e:
        return {"success": False, "error": str(e)}


def fetch_comments(mid, page=1, count=20):
    """获取评论列表（通过 Ajax API）"""
    # 微博的评论 Ajax API
    url = f"https://weibo.com/ajax/statuses/buildComments"
    params = {
        "is_reload": "1",
        "id": mid,
        "is_show_bulletin": "2",
        "is_mix": "0",
        "count": count,
        "page": page,
        "fetch_level": "0",
    }

    referer = f"https://weibo.com/{mid}"
    headers = build_headers(referer)
    time.sleep(random.uniform(1.0, 2.5))

    try:
        resp = session.get(url, headers=headers, params=params, timeout=15)
        if resp.status_code == 200:
            data = resp.json()
            if isinstance(data, dict):
                comments_data = data.get('data', [])
                max_page = data.get('max_page', 0) if isinstance(data.get('max_page'), int) else data.get('max_page', 0)

                # 如果 data 为 None 或空
                if comments_data is None:
                    comments_data = []

                items = []
                for item in comments_data:
                    user = item.get('user', {})
                    items.append({
                        "id": str(item.get('id', '')),
                        "text_raw": item.get('text_raw', '') or item.get('text', ''),
                        "text": item.get('text', ''),
                        "user": {
                            "id": str(user.get('id', '')),
                            "screen_name": user.get('screen_name', ''),
                        },
                        "created_at": item.get('created_at', ''),
                        "like_count": item.get('like_count', 0),
                        "total_number": item.get('total_number', 0),
                    })

                return {
                    "success": True,
                    "data": items,
                    "count": len(items),
                    "page": page,
                    "max_page": max_page,
                    "total_number": data.get('total_number', 0),
                }
            else:
                return {"success": False, "error": "响应不是JSON对象"}
        else:
            return {"success": False, "error": f"HTTP {resp.status_code}"}
    except Exception as e:
        return {"success": False, "error": str(e)}


def extract_mid(input_val):
    """从 URL 或文本中提取微博 MID"""
    if not input_val:
        return input_val
    url_match = re.search(r'weibo\.com/\d+/([a-zA-Z0-9]+)', input_val)
    if url_match:
        return url_match.group(1)
    num_match = re.search(r'(\d{9,16})', input_val.strip())
    if num_match:
        return num_match.group(1)
    return input_val.strip()


def fetch_repost_timeline(mid, page=1, count=20):
    """获取转发列表（通过 Ajax API）"""
    url = f"https://weibo.com/ajax/statuses/repostTimeline"
    params = {
        "id": mid,
        "page": page,
        "count": count,
    }
    referer = f"https://weibo.com/{mid}"
    headers = build_headers(referer)
    time.sleep(random.uniform(1.0, 2.5))

    try:
        resp = session.get(url, headers=headers, params=params, timeout=15)
        if resp.status_code == 200:
            data = resp.json()
            if isinstance(data, dict):
                if data.get('empty_tip') and '登录' in data['empty_tip']:
                    return {"success": False, "error": f"Cookie 登录态失效: {data['empty_tip']}"}
                reposts = data.get('data', [])
                if reposts is None:
                    reposts = []
                items = []
                for item in reposts:
                    user = item.get('user', {})
                    items.append({
                        "id": str(item.get('id', '')),
                        "text_raw": item.get('text_raw', '') or item.get('text', ''),
                        "text": item.get('text', ''),
                        "user": {
                            "id": str(user.get('id', '')),
                            "screen_name": user.get('screen_name', ''),
                        },
                        "reposts_count": item.get('reposts_count', 0),
                        "comments_count": item.get('comments_count', 0),
                        "attitudes_count": item.get('attitudes_count', 0),
                        "created_at": item.get('created_at', ''),
                    })
                return {
                    "success": True,
                    "data": items,
                    "count": len(items),
                    "page": page,
                    "total_number": data.get('total_number', 0),
                }
            else:
                return {"success": False, "error": "响应不是JSON对象"}
        else:
            return {"success": False, "error": f"HTTP {resp.status_code}"}
    except Exception as e:
        return {"success": False, "error": str(e)}


def main():
    if len(sys.argv) < 2:
        print(json.dumps({"success": False, "error": "缺少参数，用法: weibo_content_spider.py <status|comments|repostTimeline> <mid> [page] [count]"}, ensure_ascii=False))
        sys.exit(1)

    if not os.path.exists(COOKIE_FILE):
        print(json.dumps({"success": False, "error": "Cookie 文件不存在，请先运行 cookie_helper.py 登录微博"}, ensure_ascii=False))
        sys.exit(0)

    action = sys.argv[1]

    if action == "status":
        if len(sys.argv) < 3:
            print(json.dumps({"success": False, "error": "缺少 mid 参数"}, ensure_ascii=False))
            sys.exit(1)
        mid = extract_mid(sys.argv[2])
        result = fetch_status(mid)
        print(json.dumps(result, ensure_ascii=False))

    elif action == "comments":
        if len(sys.argv) < 3:
            print(json.dumps({"success": False, "error": "缺少 mid 参数"}, ensure_ascii=False))
            sys.exit(1)
        mid = extract_mid(sys.argv[2])
        page = int(sys.argv[3]) if len(sys.argv) > 3 else 1
        count = int(sys.argv[4]) if len(sys.argv) > 4 else 100
        result = fetch_comments(mid, page, count)
        print(json.dumps(result, ensure_ascii=False))

    elif action == "repostTimeline":
        if len(sys.argv) < 3:
            print(json.dumps({"success": False, "error": "缺少 mid 参数"}, ensure_ascii=False))
            sys.exit(1)
        mid = extract_mid(sys.argv[2])
        page = int(sys.argv[3]) if len(sys.argv) > 3 else 1
        count = int(sys.argv[4]) if len(sys.argv) > 4 else 20
        result = fetch_repost_timeline(mid, page, count)
        print(json.dumps(result, ensure_ascii=False))

    else:
        print(json.dumps({"success": False, "error": f"未知操作: {action}，支持: status, comments, repostTimeline"}, ensure_ascii=False))


if __name__ == "__main__":
    main()
