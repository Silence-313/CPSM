#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import json
import time
from playwright.sync_api import sync_playwright

def fetch_weibo_cookies():
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=False)
        context = browser.new_context()
        page = context.new_page()

        print("正在打开微博登录页...")
        page.goto("https://weibo.com/login.php")
        print("请在浏览器中完成登录。脚本正在监测登录状态...")

        max_retries = 120
        found = False

        for _ in range(max_retries):
            cookies = context.cookies()
            sub_cookie = next((c for c in cookies if c['name'] == 'SUB'), None)

            if sub_cookie:
                print("检测到登录凭证 (SUB 字段已生成)！")
                # 等待 2 秒确保所有 Cookie 完全写入
                # time.sleep(5)
                cookies = context.cookies()

                # 提取关键 Cookie
                cookie_str = "; ".join([f"{c['name']}={c['value']}" for c in cookies])
                xsrf = next((c['value'] for c in cookies if c['name'] == 'XSRF-TOKEN'), '')
                sub = sub_cookie['value']
                subp = next((c['value'] for c in cookies if c['name'] == 'SUBP'), '')

                result = {
                    "cookie": cookie_str,
                    "xsrf": xsrf,
                    "sub": sub,
                    "subp": subp,
                    "update_time": time.strftime("%Y-%m-%d %H:%M:%S")
                }

                with open("spider/weibo_config.json", "w", encoding='utf-8') as f:
                    json.dump(result, f, indent=2, ensure_ascii=False)

                print("=" * 30)
                print("✅ Cookie 保存成功！")
                print("=" * 30)
                # print("Cookie 已保存，10 秒后自动关闭浏览器...")
                # time.sleep(10)
                found = True
                break

            time.sleep(1)

        if not found:
            print("❌ 登录超时，未检测到有效 Cookie。")

        browser.close()

if __name__ == "__main__":
    fetch_weibo_cookies()