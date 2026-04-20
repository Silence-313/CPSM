#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import json
import time
import sys
import os
import re
import random
import subprocess
from datetime import datetime
import requests
import pymysql
from py2neo import Graph, Node, Relationship

# ========== 配置区 ==========
MYSQL_CONFIG = {
    'host': 'localhost',
    'user': 'root',
    'password': 'naonao050301',
    'database': 'cpms',
    'charset': 'utf8mb4'
}

NEO4J_URI = "bolt://localhost:7687"
NEO4J_AUTH = ("neo4j", "naonao050301")

COOKIE_FILE = "spider/weibo_config.json"
MAX_DEPTH = 5
PAGE_SIZE = 20

visited_mids = set()
_config_cache = None

# 全局 Session，模拟浏览器会话
session = requests.Session()

# ========== Cookie 管理 ==========

def refresh_cookie():
    print("🔄 正在打开浏览器，请手动扫码登录微博...")
    script_path = os.path.join(os.path.dirname(__file__), "cookie_helper.py")
    python_exe = sys.executable
    try:
        result = subprocess.run([python_exe, script_path], check=True)
        if result.returncode == 0:
            print("✅ Cookie 刷新成功")
            global _config_cache
            _config_cache = None
            return True
        else:
            print("❌ Cookie 刷新失败")
            return False
    except Exception as e:
        print(f"❌ 调用刷新脚本异常: {e}")
        return False

def load_config():
    global _config_cache
    if _config_cache is not None:
        return _config_cache
    try:
        with open(COOKIE_FILE, 'r', encoding='utf-8') as f:
            _config_cache = json.load(f)
        return _config_cache
    except:
        return {}

def get_cookie():
    config = load_config()
    return config.get('cookie', '')

def get_xsrf():
    config = load_config()
    return config.get('xsrf', '')

def check_and_refresh_cookie():
    if not os.path.exists(COOKIE_FILE):
        print("⚠️ Cookie 文件不存在，准备刷新...")
        refresh_cookie()
        return

    mtime = os.path.getmtime(COOKIE_FILE)
    age_hours = (time.time() - mtime) / 3600
    if age_hours > 6:
        print(f"⚠️ Cookie 已存在 {age_hours:.1f} 小时，准备刷新...")
        refresh_cookie()
    else:
        print(f"✅ Cookie 文件较新 ({age_hours:.1f} 小时前)，继续使用")

# ========== 反反爬请求头构建 ==========

def build_headers(referer='https://weibo.com/'):
    """构建尽可能真实的请求头"""
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

# ========== 微博 API 请求 ==========

def fetch_post_detail(mid):
    print(f"🔍 获取根节点详细信息: mid={mid}")
    url = f"https://weibo.com/ajax/statuses/show?id={mid}"
    headers = build_headers()
    try:
        resp = session.get(url, headers=headers, timeout=10)
        if resp.status_code == 200:
            data = resp.json()
            screen_name = data.get('user', {}).get('screen_name', '源头微博')
            content = data.get('text_raw', '') or data.get('text', '')
            repost_num = data.get('reposts_count', 0)
            created_at = data.get('created_at', '')
            post_time = parse_weibo_time(created_at)
            print(f"✅ 根节点: {screen_name}，转发数: {repost_num}")
            return screen_name, content[:500], repost_num, post_time
        else:
            print(f"❌ 获取根节点失败，状态码：{resp.status_code}")
    except Exception as e:
        print(f"❌ 获取根节点详情异常: {e}")
    return '源头微博', '[原博内容获取失败]', 0, datetime.now()

def fetch_repost_list(mid, page=1):
    url = f"https://weibo.com/ajax/statuses/repostTimeline?id={mid}&page={page}&count={PAGE_SIZE}"
    print(f"🌐 请求第 {page} 页转发列表: mid={mid}")

    # 随机延时，模拟人类浏览
    time.sleep(random.uniform(1.5, 3.0))

    headers = build_headers()
    try:
        resp = session.get(url, headers=headers, timeout=10)
        if resp.status_code == 200:
            data = resp.json()
            # 若返回登录提示，尝试刷新 Cookie 并重试一次
            if isinstance(data, dict) and data.get('empty_tip') and '登录' in data['empty_tip']:
                print(f"⚠️ 接口要求登录: {data['empty_tip']}")
                if refresh_cookie():
                    headers["Cookie"] = get_cookie()
                    headers["X-XSRF-TOKEN"] = get_xsrf()
                    time.sleep(2)
                    resp = session.get(url, headers=headers, timeout=10)
                    if resp.status_code == 200:
                        data = resp.json()
            preview = json.dumps(data, ensure_ascii=False)[:300]
            print(f"🔎 原始响应预览: {preview}")
            return data
        else:
            print(f"❌ 请求失败，状态码：{resp.status_code}")
    except Exception as e:
        print(f"❌ 请求异常：{e}")
    return None

def parse_weibo_time(time_str):
    if not time_str:
        return datetime.now()
    try:
        return datetime.strptime(time_str, "%a %b %d %H:%M:%S %z %Y")
    except:
        try:
            return datetime.strptime(time_str, "%Y-%m-%d %H:%M:%S")
        except:
            print(f"⚠️ 时间解析失败: {time_str}")
            return datetime.now()

def sentiment_analyze(text):
    try:
        resp = requests.post(
            "http://localhost:8080/api/analysis/sentiment/score",
            json={"text": text},
            timeout=5
        )
        if resp.ok:
            score = resp.json().get("score", 0)
            if score > 0.3: return 1
            elif score < -0.3: return -1
    except:
        pass
    return 0

# ========== 数据库操作 ==========

def save_to_mysql(conn, data):
    sql = """
    INSERT INTO weibo_repost_tree 
    (mid, parent_mid, root_mid, uid, screen_name, content, repost_num, attitude, post_time)
    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
    ON DUPLICATE KEY UPDATE repost_num=VALUES(repost_num), attitude=VALUES(attitude)
    """
    with conn.cursor() as cursor:
        cursor.execute(sql, (
            data['mid'], data['parent_mid'], data['root_mid'],
            data['uid'], data['screen_name'], data['content'][:500],
            data['repost_num'], data['attitude'], data['post_time']
        ))
    conn.commit()
    print(f"  💾 MySQL 保存成功: {data['mid']}")

def save_to_neo4j(graph, data):
    node = Node("Post",
                mid=data['mid'],
                rootMid=data['root_mid'],
                screenName=data['screen_name'],
                content=data['content'][:200],
                repostCount=data['repost_num'],
                sentiment=data['attitude'],
                postTime=data['post_time'].isoformat())
    graph.merge(node, "Post", "mid")
    print(f"  🗂️  Neo4j 节点已合并: {data['mid']}")

    if data['parent_mid']:
        parent_node = graph.nodes.match("Post", mid=data['parent_mid']).first()
        if parent_node:
            rel = Relationship(node, "REPOSTED_FROM", parent_node)
            graph.merge(rel)
            print(f"  🔗 关系已建立: {data['mid']} -[:REPOSTED_FROM]-> {data['parent_mid']}")

# ========== 递归抓取 ==========

def crawl_repost_tree(graph, mysql_conn, current_mid, root_mid, parent_mid=None, depth=0):
    if depth >= MAX_DEPTH or current_mid in visited_mids:
        return
    visited_mids.add(current_mid)

    print(f"\n📥 正在抓取 mid={current_mid}，深度={depth}，累计节点={len(visited_mids)}")

    for page in range(1, 3):
        json_data = fetch_repost_list(current_mid, page)
        if not json_data:
            break

        data_list = json_data.get('data', [])
        if not isinstance(data_list, list) or len(data_list) == 0:
            print(f"  ⏹️ 第 {page} 页无数据")
            break

        print(f"  📄 第 {page} 页获取到 {len(data_list)} 条转发")

        for item in data_list:
            mid = str(item.get('id'))
            if mid in visited_mids:
                continue

            user = item.get('user', {})
            screen_name = user.get('screen_name', '')
            uid = str(user.get('id', ''))
            content = item.get('text_raw', '')
            repost_num = item.get('reposts_count', 0)
            created_at = item.get('created_at', '')
            post_time = parse_weibo_time(created_at)
            attitude = sentiment_analyze(content)

            data = {
                'mid': mid,
                'parent_mid': parent_mid,
                'root_mid': root_mid,
                'uid': uid,
                'screen_name': screen_name,
                'content': content,
                'repost_num': repost_num,
                'attitude': attitude,
                'post_time': post_time
            }

            print(f"  👤 处理节点: {screen_name} (MID: {mid})")
            save_to_mysql(mysql_conn, data)
            save_to_neo4j(graph, data)

            print(f"  🔁 准备递归抓取子节点: {mid}")
            crawl_repost_tree(graph, mysql_conn, mid, root_mid, mid, depth + 1)

        if len(data_list) < PAGE_SIZE:
            break
        time.sleep(random.uniform(1.0, 2.0))

# ========== 主函数 ==========

def main():
    if len(sys.argv) < 2:
        print("❌ 请传入微博 MID")
        sys.exit(1)

    target_mid = sys.argv[1]
    mid_match = re.search(r'(\d{9,16})', target_mid)
    if mid_match:
        target_mid = mid_match.group(1)

    print(f"\n🚀 开始构建溯源树，根节点 MID = {target_mid}")
    print("=" * 50)

    mysql_conn = pymysql.connect(**MYSQL_CONFIG)
    graph = Graph(NEO4J_URI, auth=NEO4J_AUTH)

    try:
        screen_name, content, repost_num, post_time = fetch_post_detail(target_mid)

        root_data = {
            'mid': target_mid,
            'parent_mid': None,
            'root_mid': target_mid,
            'uid': '',
            'screen_name': screen_name,
            'content': content,
            'repost_num': repost_num,
            'attitude': sentiment_analyze(content),
            'post_time': post_time
        }
        print(f"🌱 保存根节点: {screen_name}")
        save_to_mysql(mysql_conn, root_data)
        save_to_neo4j(graph, root_data)

        crawl_repost_tree(graph, mysql_conn, target_mid, target_mid, parent_mid=None, depth=1)

        print("\n" + "=" * 50)
        print(f"✅ 溯源树构建完成！总节点数: {len(visited_mids)}")
    except Exception as e:
        print(f"❌ 抓取过程出错：{e}")
        import traceback
        traceback.print_exc()
    finally:
        mysql_conn.close()

if __name__ == "__main__":
    check_and_refresh_cookie()
    main()