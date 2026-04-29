#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import json
import time
import os
from playwright.sync_api import sync_playwright

OUTPUT_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "weibo_config.json")


def fetch_weibo_cookies():
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=False)
        context = browser.new_context()
        page = context.new_page()

        # 直接访问 weibo.com，未登录会自动重定向到 passport 登录页
        page.goto("https://weibo.com", wait_until="domcontentloaded")
        print("请在浏览器中完成扫码登录...")
        print("脚本正在监测登录状态...")

        max_wait = 180
        found = False

        for _ in range(max_wait):
            time.sleep(1)
            url = page.url
            cookies = context.cookies()

            # 登录成功标志：URL 已从 passport/login 跳回 weibo.com 主站
            logged_in = "weibo.com" in url and "passport" not in url and "login" not in url

            if not logged_in:
                continue

            sub = next((c['value'] for c in cookies if c['name'] == 'SUB'), None)
            xsrf = next((c['value'] for c in cookies if c['name'] == 'XSRF-TOKEN'), None)

            if not sub or not xsrf:
                continue

            print(f"登录成功！页面跳转至: {url}")
            print("等待 Cookie 写入完成（5秒）...")
            time.sleep(5)

            # 重新读取，只取 weibo 域名的 cookie
            cookies = context.cookies()
            wb = [c for c in cookies if 'weibo' in c.get('domain', '')]

            cookie_str = "; ".join(f"{c['name']}={c['value']}" for c in wb)
            sub = next((c['value'] for c in wb if c['name'] == 'SUB'), '')
            xsrf = next((c['value'] for c in wb if c['name'] == 'XSRF-TOKEN'), '')
            subp = next((c['value'] for c in wb if c['name'] == 'SUBP'), '')

            result = {
                "cookie": cookie_str,
                "xsrf": xsrf,
                "sub": sub,
                "subp": subp,
                "update_time": time.strftime("%Y-%m-%d %H:%M:%S")
            }

            with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
                json.dump(result, f, indent=2, ensure_ascii=False)

            print("=" * 40)
            print(f"Cookie 已保存: {OUTPUT_FILE}")
            print(f"字段数: {len(wb)}  更新时间: {result['update_time']}")
            print("=" * 40)
            found = True
            break

        if not found:
            print("=" * 40)
            print("登录超时（3分钟），未检测到有效 Cookie。")
            print(f"当前页面: {page.url}")
            names = [c['name'] for c in context.cookies()]
            print(f"已有 Cookie: {names if names else '无'}")
            print("请检查:")
            print("  1. 是否在 3 分钟内完成了扫码？")
            print("  2. 扫码后页面是否跳回了 weibo.com？")
            print("=" * 40)

        browser.close()


if __name__ == "__main__":
    fetch_weibo_cookies()