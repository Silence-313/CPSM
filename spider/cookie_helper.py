import json
import time
from playwright.sync_api import sync_playwright

def fetch_weibo_cookies():
    with sync_playwright() as p:
        # 启动浏览器
        browser = p.chromium.launch(headless=False)
        context = browser.new_context()
        page = context.new_page()

        print("正在打开微博登录页...")
        page.goto("https://weibo.com/login.php")

        print("请在浏览器中完成登录。脚本正在监测登录状态...")

        # 轮询检测 Cookie 中是否包含关键字段 SUB
        max_retries = 120  # 最多等待 120 秒
        found = False

        for i in range(max_retries):
            cookies = context.cookies()
            # SUB 是微博最核心的登录 Session 字段
            sub_cookie = next((c for c in cookies if c['name'] == 'SUB'), None)

            if sub_cookie:
                print(f"检测到登录凭证 (SUB 字段已生成)！")

                # 格式化数据
                cookie_str = "; ".join([f"{c['name']}={c['value']}" for c in cookies])
                xsrf = next((c['value'] for c in cookies if c['name'] == 'XSRF-TOKEN'), "")

                result = {
                    "cookie": cookie_str,
                    "xsrf": xsrf,
                    "update_time": time.strftime("%Y-%m-%d %H:%M:%S")
                }

                # 保存到本地文件
                with open("weibo_config.json", "w") as f:
                    json.dump(result, f)

                print("="*30)
                print("抓取成功！配置文件已更新。")
                print("="*30)
                found = True
                break

            time.sleep(1) # 每秒检测一次

        if not found:
            print("登录超时，未检测到有效 Cookie。")

        browser.close()

if __name__ == "__main__":
    fetch_weibo_cookies()