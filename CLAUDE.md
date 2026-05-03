# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build/Run

```bash
./mvnw clean install        # Build
./mvnw spring-boot:run      # Run (or run DemoApplication.java directly in IDE)
./mvnw package              # Package fat JAR
```

No tests beyond a context-load test in `DemoApplicationTests.java`.

## Architecture

Spring Boot 3.4.5 monolith with a static HTML frontend (Vue 3 CDN + ECharts). All REST APIs live under `/api/`.

**Layers:** Controller → Service (interface + impl) → MyBatis Mapper (annotation-based SQL). No XML mappers despite `mybatis.mapper-locations` being set.

**Eight feature modules:**

| Path prefix | Module | Key dependency |
|---|---|---|
| `/api/user` | User auth (login/reg/forget) | MySQL `sys_user` |
| `/api/weibo` | Hot search monitoring | Python spider `spider/hot_search_spider.py` (Weibo Ajax API, cookie auth), matched against user's monitored stars/keywords |
| `/api/analysis` | Comment sentiment analysis | Official API `comments/show` + `statuses/show` (primary), fallback to `spider/weibo_content_spider.py` (cookie auth); max 50 pages; Ansj tokenizer + custom sentiment dicts + ES for word cloud |
| `/api/simulation` | AI sandbox + Red-Blue stress test | `/api/simulation/start` (6 LLMs predict, DeepSeek judge); `/api/simulation/stress-test` (4 agents + judge, multi-agent attack simulation) |
| `/api/trace` | Repost trace | Official API `statuses/show` + `repost_timeline`, stores tree in MySQL + Neo4j graph |
| `/api/monitor` | DevOps dashboard | Actuator metrics + JVM real data + simulated crawler/DB/API stats, 3s polling |
| `/api/profile` | Profile hub | Stats aggregation, module usage, contribution heatmap, activity feed, API key management |

**User data isolation:** All data queries are scoped by `userId` from `HttpSession`. Login required for most operations. `/api/monitor/dashboard-data` is the only endpoint that does not require login.

**Dual database:**
- **MySQL** (MyBatis): relational data — users, stars, keywords, comments, simulation sessions, activity logs, app config
- **Neo4j** (Spring Data Neo4j): `PostNode` entity mapped to label `Post` + `REPOSTED_FROM` relationships for repost propagation trees

**Flyway** manages MySQL schema with 5 migrations in `src/main/resources/db/migration/` (V1–V5). V5 adds `sys_user.avatar`, `user_activity_log`, `app_config` tables.

**Multi-LLM setup:** Each AI provider has its own `@ConfigurationProperties` config class (QwenConfig, DeepSeekConfig, etc.). `OpenAICompatibleService` is the unified gateway — all models use OpenAI-compatible chat completions API. Methods include: `callQwen`, `callDeepSeek`, `callSiliconflow`, `callDoubao`, `callHunyuan`, `callKimi`, `callSimulationModel`, and `callWithSystemPrompt(systemPrompt, userMessage)` for multi-agent scenarios.

**Simulation config** is loaded into `SimulationModelConfig` which holds a `List<ModelProperties>` and a `JudgeProperties`, all bound from `simulation.models[*]` and `simulation.judge.*` in application.properties.

## Actuator / Monitoring

`spring-boot-starter-actuator` + `micrometer-registry-prometheus` are included. Actuator endpoints exposed at `/actuator/` (health, metrics, info, env). Health details and components are always shown.

## Weibo data acquisition — dual-source strategy

The project uses two parallel auth mechanisms to maximize data availability:

| Source | Auth | Used for |
|---|---|---|
| Official API (`WeiboOpenApiService`) | `access_token` query param | Primary: comments, statuses, repost_timeline |
| Python spiders (`spider/*.py`) | Cookie (`weibo_config.json`) | Hot search (always), comment analysis (fallback), repost trace (standalone) |

**Hot search (`/api/weibo`):** Always uses Python spider `spider/hot_search_spider.py` → Weibo Ajax `weibo.com/ajax/side/hotSearch`. Invoked via subprocess from `WeiboServiceImpl.fetchTrendsAsHotItems()`. Official API `trends/hourly` is not used (code=20112 permission denied).

**Comment analysis (`/api/analysis`):** Implements API-first-then-spider-fallback in `CommentAnalysisServiceImpl`:
1. `fetchWeiboTitle(mid)`: official `statuses/show` → fallback `spider/weibo_content_spider.py status`
2. Comments loop (max 50 pages, 20/page — the Weibo Ajax API caps at 20 per page regardless of count param): official `comments/show` → on first failure, switches to `spider/weibo_content_spider.py comments` for all remaining pages. Any-page switching supported (not just page 1).
3. Loop stops when page returns 0 comments (empty data array), not based on per-page count.
4. Spider endpoints: `weibo.com/ajax/statuses/show` + `weibo.com/ajax/statuses/buildComments`
5. ES tokenization failures are caught per-comment (won't block DB writes). Each `commentMapper.insert()` has independent try-catch with logging.

**MID parsing:** All modules use `WeiboIdParser.parseMid()` for unified MID extraction — supports mobile URLs (`m.weibo.cn/status/{mid}`, `m.weibo.cn/detail/{mid}`), desktop URLs with Base62 decoding (`weibo.com/{uid}/{mid}`), and plain numeric MIDs.

**Repost trace (`/api/trace`):** `TraceService` implements API-first-then-spider-fallback (same pattern as comment analysis):
1. `TraceController.buildTrace(mid)`: parses MID via `WeiboIdParser.parseMid()`, triggers `@Async asyncBuildTraceTree`, returns `{success: boolean, mid, message}` (success is boolean, not string).
2. `crawlAndBuildTree(mid)`: Falls back to `spider/weibo_content_spider.py status` when official `statuses/show` fails. Always saves root node first (even if 0 reposts), then recurses for children.
3. Repost recursion: official `statuses/repost_timeline` → on first failure, switches to `spider/weibo_content_spider.py repostTimeline` for all remaining pages + recursive calls. Dual-source: official API returns `reposts` array, spider wrapper normalizes `data` → `reposts`.
4. MID resolution: uses `status.path("mid")` from API response (real numeric MID) for subsequent calls — short MIDs like `QCVaxBM5m` don't work with repost timeline API.
5. Standalone Python script `spider/repost_crawler.py` also available for cookie-based tracing with Neo4j/MySQL persistence. **Critical:** level-1 nodes use `parent_mid=target_mid` (not `None`), so they correctly link back to root.
6. Neo4j query `findAllNodesByRootMid` returns `MATCH (p:Post {rootMid: $rootMid}) RETURN p`. Parent-child relationships for ECharts links are queried from MySQL `weibo_repost_tree.parent_mid` via `WeiboRepostTreeMapper.getParentMappings()`, NOT from Neo4j relationship mapping (to avoid 3-column query mapping issues).
7. Duplicate `REPOSTED_FROM` relationships are prevented: Java `savePostNode` checks `getParent() == null` before setting, Python `repost_crawler.py` uses `graph.match_one()` before creating.
8. **Frontend (`trace.html` + `trace.js`):** Polling loop (2s interval, max 60 polls) queries `/api/trace/status/{mid}` and `/api/trace/tree/{mid}`. ECharts force-directed graph with `symbolSize: (val) => Math.max(20, Math.sqrt(val) * 8 + 15)` — minimum 20px so 0-repost nodes remain visible. Timeline slider hidden via `v-if` when no valid `postTime` data. Node click triggers `/api/trace/analyze-node` for AI analysis via SiliconFlow.
9. **CSS:** trace.html loads `common.css` then `trace.css`. `trace.css` MUST redefine `.nav-actions`, `.nav-btn`, `.btn-back` (matching other pages) since common.css defaults produce near-invisible nav buttons on the `#f4f7f9` background.

## Red-Blue Stress Test Lab

`StressTestService` orchestrates a multi-agent simulation (`POST /api/simulation/stress-test`):
1. Four AI agents called in parallel via `CompletableFuture.allOf` (60s timeout), each with a distinct System Prompt:
   - **激进黑粉**: attacks text, finds moral/logical flaws
   - **理智路人**: objective, immune to PR tactics
   - **忠实粉丝**: blindly defends, provokes backlash ("招黑")
   - **公关专家**: evaluates risk from communication perspective, outputs keywords as JSON array
2. A 5th AI instance (裁决官) summarizes all comments → 0-100 risk score, risk level, 3 vulnerable keywords, verdict.
3. Fallback mechanism: if any AI call fails, `generateFallbackComment()` produces a Chinese-language fallback reply based on role.
4. All agent calls use `OpenAICompatibleService.callWithSystemPrompt()` targeting SiliconFlow API.
5. Frontend `lab.html`: three-column layout — text input (left), staggered comment feed (middle), ECharts risk gauge + keywords + expert advice (right). Comments appear one-by-one with 600ms staggered delay and slide-in animation.

## DevOps Dashboard

`SystemMonitorController` (`GET /api/monitor/dashboard-data`, no login required) returns aggregated system health data:
- **system**: JVM memory (real from `Runtime`), CPU load (real from `OperatingSystemMXBean`), uptime (real from `RuntimeMXBean`)
- **crawler**: Cookie TTL (calculated from `weibo_config.json` file age), today's crawl count (time-based simulation), block count
- **database**: MySQL latency (simulated 1.5-4.5ms), Neo4j connected status + latency (simulated + 5% failure rate)
- **api**: QPS + HTTP status distribution (200/4xx/5xx), time-based simulation
- **history**: 60 data points (one per second) with time/qps/cpu/memory for line charts

Frontend `dashboard.html`: dark sci-fi theme with cyan/purple neon accents. 3s polling loop. Layout: JVM gauge + DB latency bars (left), crawler hero stats (center), API pie + CPU line (right), QPS full-width line chart (bottom). Portal page shows a live preview card with CPU/Memory/QPS/Neo4j metrics.

## Profile Hub

`ProfileController` manages user profile data (all endpoints require login):
- `GET /api/profile/info`: username, avatar, email, phone
- `GET /api/profile/stats`: aggregated from `weibo_comments` (monitor cases), `sim_session` (AI predictions), `monitor_star` (monitored stars), app start time (uptime days)
- `GET /api/profile/module-usage`: 5-module bar chart data (热搜监测/评论分析/AI沙盘/传播溯源/攻防实验室), values from real table counts + simulation
- `GET /api/profile/heatmap`: 365-day GitHub-style contribution grid (simulated with recent-day bias)
- `GET /api/profile/activity`: merged timeline from `weibo_comments` history, `sim_session` records, and `user_activity_log` table
- `PUT /api/profile/username`, `PUT /api/profile/avatar`, `GET/PUT /api/profile/api-key`: user settings CRUD

New tables via V5 migration: `sys_user.avatar` (VARCHAR), `user_activity_log` (user_id, module, action, create_time), `app_config` (user_id, config_key, config_value — used for API key storage).

Frontend `profile.html`: dark cyberpunk theme. Layout: profile header with avatar selector (6 gradient presets), 4 neon stat cards, 3-column content grid (account settings with API key management, module usage bar chart + contribution heatmap, activity feed timeline).

**Portal page (`portal.html`):**
- Dashboard preview card (dark background, LIVE badge, 10s polling)
- 7 module cards: 热搜监测看板, 博文风向分析, 舆情沙盘模拟室, 蝴蝶效应·传播溯源, 舆情攻防实验室, 个人中心, 系统健康态势感知大屏 (preview card above grid)

## Python environment

`/opt/anaconda3/envs/weibo_spider/bin/python`, configured via `weibo.python.path` in `application.properties`.

## Cookie management

Stored in `spider/weibo_config.json`, refreshed via `spider/cookie_helper.py` (Playwright browser login, valid ~6 hours).

## Official API config

App Key and `access_token` in `application.properties` (`weibo.open.*`). Token validation at boot via `oauth2/get_token_info`. Known permission gaps: `trends/hourly`, `statuses/show` (code 20112).

## Removed legacy

`HttpUtils` (cookie-based scraping), `TraceAsyncService` (Python spider) — all deleted.

## Global theme system

`theme.css` defines all CSS custom properties on `:root` (dark default) and `.light-theme` (light override). `theme.js` provides the `window.CPMSTheme` API.

**Every page MUST load both files:**
```html
<link rel="stylesheet" href="css/theme.css">
<script src="js/theme.js"></script>
```

**Theme colors:**

| Token | Dark (Cyberpunk) | Light (Professional) |
|---|---|---|
| `--bg-primary` | `#080c17` | `#f1f5f9` |
| `--bg-card` | `rgba(12,20,40,0.85)` | `rgba(255,255,255,0.78)` |
| `--text-primary` | `#e2e8f0` | `#1e293b` |
| `--accent` | `#00e5ff` (cyan neon) | `#2563eb` (business blue) |
| `--accent-purple` | `#a855f7` | (not set) |
| `--success` / `--warning` / `--danger` | `#10b981` / `#f59e0b` / `#ef4444` | `#059669` / `#d97706` / `#dc2626` |

Dark theme borders use cyan tint (`rgba(0,229,255,…)`) instead of flat white — gives a neon-glow cyberpunk feel. Body background has radial gradient overlays (cyan + purple + green) and a subtle grid pattern.

**CPMSTheme API:**
- `CPMSTheme.current()` → `'dark'` | `'light'`
- `CPMSTheme.toggle()` / `CPMSTheme.set(theme)` — switch themes, persists to localStorage
- `CPMSTheme.chartTokens()` → `{ chartText, chartAxis, chartSplit }`
- `CPMSTheme.registerChart(instance, optionBuilder)` — auto-update on theme switch
- `CPMSTheme.unregisterChart(instance)` — clean up on teardown
- Listen to `window.addEventListener('theme-changed', e => { ... })` for custom reactions

**Glassmorphism cards:** Use `.glass-card` or `.card` class (or `var(--glass-bg)`, `--glass-border`, `--glass-blur`, `--glass-shadow` directly). All panels, nav bars, and form elements use theme tokens — prefer `var(--bg-card)`, `var(--border)`, `var(--text-dim)` over hardcoded colours.

**ECharts integration:** After `echarts.init(dom)`, call `CPMSTheme.registerChart(chart, themePatchFn)`. The builder function receives `(tokens)` and must return **only colour/style properties** (backgroundColor, axisLine, splitLine, axisLabel, label) — **never `data` arrays**. Returning `data: []` would overwrite the chart's real data on theme switch. Keep the full-options builder (`buildChartOption`) separate for initial `setOption`, and use a slim `buildChartThemePatch` for `registerChart`.

## Static frontend

`src/main/resources/static/` — each page is a standalone HTML file. Vue 3 and ECharts loaded from CDN. All state is per-page, no shared Vuex/Pinia store.

**Page list:** `login.html` → `portal.html` → `index.html` (hot search), `analysis.html` (comment analysis), `simulation.html` (AI sandbox), `trace.html` (repost trace), `lab.html` (stress test lab), `dashboard.html` (DevOps dashboard), `profile.html` (profile hub), `help.html`.

## Required infrastructure

- MySQL on `localhost:3306/cpms` (Flyway auto-migrates)
- Elasticsearch on `localhost:9200` (needs `ik` analyzer plugin installed), container name `es-server` (image `elasticsearch:7.17.9`)
- Neo4j on `bolt://localhost:7687` (Docker compose in `docker/neo4j/`, container name `cpms-neo4j`)
- Python 3 (Anaconda env `weibo_spider` at `/opt/anaconda3/envs/weibo_spider/bin/python`) with packages: `requests`, `pymysql`, `py2neo`, `playwright`
- Cookie file `spider/weibo_config.json` — if missing or expired (>6h), run `python spider/cookie_helper.py` to scan QR login

**Infrastructure auto-start:** `InfraStartupRunner` (ApplicationRunner) runs at boot — checks `cpms-neo4j` and `es-server` Docker containers via `docker ps`, starts them via `bash start-infra.sh` if either is down. Script path config: `infra.script.path=./start-infra.sh`. Timeout 120s.

## Spider scripts

| Script | Purpose | Called from |
|---|---|---|
| `spider/hot_search_spider.py` | Scrape realtime hot search via Ajax | `WeiboServiceImpl` (subprocess) |
| `spider/weibo_content_spider.py` | Fetch post detail (`status`), comments (`comments`), repost timeline (`repostTimeline`) via Ajax; URL→MID auto-extraction | `CommentAnalysisServiceImpl` (subprocess, fallback), `TraceService` (subprocess, fallback) |
| `spider/repost_crawler.py` | Build repost tree (standalone) | Command line: `python repost_crawler.py <mid>` |
| `spider/cookie_helper.py` | Playwright browser login → save cookie to `weibo_config.json` | Manual when cookie expires |
| `start-infra.sh` | Auto-start cpms-neo4j + es-server Docker containers if not running | `InfraStartupRunner` (boot), or manual `./start-infra.sh` |
