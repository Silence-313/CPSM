<div align="center">

# CPMS — 名人热度监测与舆情分析系统

Celebrity Popularity Monitoring & Public Opinion Analysis System

</div>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk" alt="Java 17">
  <img src="https://img.shields.io/badge/Spring_Boot-3.4.5-6DB33F?logo=springboot" alt="Spring Boot">
  <img src="https://img.shields.io/badge/Vue-3.x-4FC08D?logo=vuedotjs" alt="Vue 3">
  <img src="https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql" alt="MySQL">
  <img src="https://img.shields.io/badge/Neo4j-5.x-4581C3?logo=neo4j" alt="Neo4j">
  <img src="https://img.shields.io/badge/Elasticsearch-7.17-005571?logo=elasticsearch" alt="ES">
  <img src="https://img.shields.io/badge/Python-3.x-3776AB?logo=python" alt="Python">
  <img src="https://img.shields.io/badge/license-MIT-green" alt="License">
</p>

## 简介

CPMS 是一个基于微博平台的综合舆情分析系统，集成**热搜监控、评论情感分析、AI 沙盘推演、转发传播溯源、红蓝攻防对抗**五大业务模块，配套 DevOps 监控大屏和个人中心。

系统采用 API 优先 + 爬虫降级的双源数据策略，后端集成 MySQL、Neo4j 图数据库和 Elasticsearch 全文检索，前端使用 Vue 3 + ECharts 构建 10 个可视化页面，支持 6 个 LLM 并发调用进行 AI 分析。

## 功能模块

| 模块 | 路径 | 功能 |
|---|---|---|
| 用户认证 | `/api/user` | 注册、登录、忘记密码，Session 会话隔离 |
| 热搜监测 | `/api/weibo` | Python 爬虫实时抓取，匹配用户关注的明星/关键词 |
| 评论分析 | `/api/analysis` | Ansj 中文分词 + 自定义情感词典 + ES 词云，支持分页深度抓取 |
| AI 沙盘 | `/api/simulation` | 6 LLM 并行预测 + DeepSeek 裁判终审，模拟舆情走向 |
| 传播溯源 | `/api/trace` | MySQL + Neo4j 双重存储转发树，ECharts 力导向图可视化 |
| 攻防实验室 | `/api/simulation/stress-test` | 4 角色多智能体对抗（黑粉/路人/粉丝/公关）+ AI 风险裁决 |
| 监控大屏 | `/api/monitor` | JVM / CPU / QPS / DB 实时指标，3s 轮询刷新 |
| 个人中心 | `/api/profile` | 数据统计、贡献热力图、活动动态、API Key、主题切换 |

## 界面

| 页面 | 文件 | 说明 |
|---|---|---|
| 登录 | `login.html` | 登录、注册、忘记密码 |
| 功能中枢 | `portal.html` | 导航主页，含系统健康实时预览 |
| 热搜监测看板 | `index.html` | 热搜榜单 + 明星/关键词匹配管理 |
| 博文风向分析 | `analysis.html` | 评论输入 → 情感分析 → 饼图 + 词云 + 历史记录 |
| 舆情沙盘模拟室 | `simulation.html` | 事件输入 → 6 LLM 推演 + Markdown 结果渲染 |
| 蝴蝶效应·传播溯源 | `trace.html` | 转发链力导向图 + 时间轴滑动 + AI 节点分析 |
| 舆情攻防实验室 | `lab.html` | 多角色评论生成 + 风险评分仪表盘 |
| 系统健康态势感知 | `dashboard.html` | DevOps 大屏，深色赛博朋克风格 |
| 个人中心 | `profile.html` | 资料设置、6 款头像、模块使用图表、活动时间线 |
| 帮助 | `help.html` | 微博 ID 获取指南 |

## 全局主题

支持**暗黑赛博朋克**和**明亮专业行政**双主题，通过 CSS 变量 + JavaScript API 实现。所有页面统一适配，ECharts 图表自动跟随更新，偏好保存在 `localStorage`。个人中心提供一键切换。

## 技术栈

| 层级 | 技术 |
|---|---|
| 后端框架 | Java 17, Spring Boot 3.4.5 |
| 持久层 | MyBatis (注解式 SQL), Spring Data Neo4j, Flyway (V1–V5) |
| 数据存储 | MySQL 8.0, Neo4j 5.x, Elasticsearch 7.17 |
| 前端 | Vue 3 CDN (Composition API), ECharts 5.5, 原生 HTML/CSS/JS |
| 爬虫 | Python 3, requests + Playwright, Anaconda 环境 |
| AI | 通义千问 / DeepSeek / SiliconFlow / 豆包 / 混元 / Kimi (OpenAI 兼容 API) |
| 构建 | Maven, Git |

## 项目结构

```
├── src/main/java/com/example/demo/
│   ├── config/          # Config classes (LLM, Neo4j, infra auto-start)
│   ├── controller/      # REST controllers (8 modules)
│   ├── service/         # Service interfaces + implementations
│   ├── mapper/          # MyBatis mappers (annotation-based SQL)
│   ├── entity/          # Entity classes
│   └── repository/      # Neo4j repositories
├── src/main/resources/
│   ├── db/migration/    # Flyway migrations (V1–V5)
│   └── static/          # 10 pages + CSS/JS
├── spider/              # Python crawling scripts
├── docker/neo4j/        # Neo4j Docker Compose
├── start-infra.sh       # Infrastructure start script
└── CLAUDE.md            # Claude Code guidance
```

## 快速开始

### 环境要求

- JDK 17+, Maven 3.8+
- MySQL 8.0 — 创建空数据库 `cpms`，Flyway 自动建表
- Neo4j — Docker 启动 `docker compose -f docker/neo4j/docker-compose.yml up -d`
- Elasticsearch 7.17 — 需安装 `ik` 分词插件
- Python 3 — Anaconda 环境 `weibo_spider`，安装 `requests`, `pymysql`, `py2neo`, `playwright`

### 1. 配置

```bash
# 编辑配置文件，将所有 your-xxx 占位符替换为你的真实密钥
vim src/main/resources/application.properties
```

需要配置：
- MySQL 连接（用户名、密码）
- Neo4j 连接（密码）
- 6 个 LLM 的 API Key（Qwen / DeepSeek / SiliconFlow / Hunyuan / Doubao / Kimi）
- 微博开放平台 App Key / App Secret / Access Token
- Python 解释器路径

> **安全提示：** 建议将真实密钥放在 `application-local.properties` 中（已加入 `.gitignore`），Spring Boot 会自动加载并覆盖主配置。

### 2. 获取微博 Cookie（用于爬虫降级）

```bash
python spider/cookie_helper.py
```

### 3. 启动

```bash
# 启动基础设施（Neo4j + ES Docker 容器）
./start-infra.sh

# 启动应用
./mvnw spring-boot:run
```

访问 `http://localhost:8080`。

## 数据获取策略

| 数据源 | 认证方式 | 优先级 | 用途 |
|---|---|---|---|
| 微博开放平台 API | `access_token` | 主 | 评论、正文、转发链 |
| Python 爬虫 (Ajax) | Cookie | 降级 | 热搜（始终）、评论/转发（API 不可用时自动切换） |

## 多 LLM 架构

```
用户输入事件
  ├── Qwen ──────────┐
  ├── DeepSeek ──────┤
  ├── SiliconFlow ───┼── 并行预测 ──→ DeepSeek 裁判终审 ──→ 风险评分
  ├── Doubao ────────┤
  ├── Hunyuan ───────┤
  └── Kimi ──────────┘
```

所有模型通过 `OpenAICompatibleService` 统一调用，遵循 OpenAI Chat Completions API 规范。

## 入侵检测与安全

本项目已排除所有敏感信息（API 密钥、数据库密码、OAuth 令牌、Cookie 会话）。提交历史中的密钥已在推送前清除。请勿在 Issues 或 PR 中分享真实的 API 密钥。

## License

MIT License

## 开发日志

详见 [WorkLog.md](WorkLog.md)
