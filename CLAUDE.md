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

**Five feature modules:**

| Path prefix | Module | Key dependency |
|---|---|---|
| `/api/user` | User auth (login/reg/forget) | MySQL `sys_user` |
| `/api/weibo` | Hot search monitoring | Python spider `spider/hot_search_spider.py` (Weibo Ajax API, cookie auth), matched against user's monitored stars/keywords |
| `/api/analysis` | Comment sentiment analysis | Official API `comments/show` + `statuses/show` (primary), fallback to `spider/weibo_content_spider.py` (cookie auth); max 50 pages; Ansj tokenizer + custom sentiment dicts + ES for word cloud |
| `/api/simulation` | AI sandbox | 6 LLMs predict outcomes, DeepSeek acts as judge to score and assess risk |
| `/api/trace` | Repost trace | Official API `statuses/show` + `repost_timeline`, stores tree in MySQL + Neo4j graph |

**User data isolation:** All data queries are scoped by `userId` from `HttpSession`. Login required for most operations.

**Dual database:**
- **MySQL** (MyBatis): relational data — users, stars, keywords, comments, simulation sessions
- **Neo4j** (Spring Data Neo4j): `PostNode` entities + `REPOSTED_FROM` relationships for repost propagation trees

**Flyway** manages MySQL schema with 4 migrations in `src/main/resources/db/migration/` (V1–V4).

**Multi-LLM setup:** Each AI provider has its own `@ConfigurationProperties` config class (QwenConfig, DeepSeekConfig, etc.). `OpenAICompatibleService` is the unified gateway — all models use OpenAI-compatible chat completions API. `SimulationService` fans out the same prompt to all 6 models concurrently, then sends their responses to DeepSeek as judge for final scoring.

**Simulation config** is loaded into `SimulationModelConfig` which holds a `List<ModelProperties>` and a `JudgeProperties`, all bound from `simulation.models[*]` and `simulation.judge.*` in application.properties.

**Weibo data acquisition — dual-source strategy:**

The project uses two parallel auth mechanisms to maximize data availability:

| Source | Auth | Used for |
|---|---|---|
| Official API (`WeiboOpenApiService`) | `access_token` query param | Primary: comments, statuses, repost_timeline |
| Python spiders (`spider/*.py`) | Cookie (`weibo_config.json`) | Hot search (always), comment analysis (fallback), repost trace (standalone) |

**Hot search (`/api/weibo`):** Always uses Python spider `spider/hot_search_spider.py` → Weibo Ajax `weibo.com/ajax/side/hotSearch`. Invoked via subprocess from `WeiboServiceImpl.fetchTrendsAsHotItems()`. Official API `trends/hourly` is not used (code=20112 permission denied).

**Comment analysis (`/api/analysis`):** Implements API-first-then-spider-fallback in `CommentAnalysisServiceImpl`:
1. `fetchWeiboTitle(mid)`: official `statuses/show` → fallback `spider/weibo_content_spider.py status`
2. Comments loop (max 50 pages, 200/page): official `comments/show` → on first failure, switches to `spider/weibo_content_spider.py comments` for all remaining pages. Any-page switching supported (not just page 1).
3. Spider endpoints: `weibo.com/ajax/statuses/show` + `weibo.com/ajax/statuses/buildComments`
4. ES tokenization failures are caught per-comment (won't block DB writes). Each `commentMapper.insert()` has independent try-catch with logging.

**Repost trace (`/api/trace`):** `TraceService` implements API-first-then-spider-fallback (same pattern as comment analysis):
1. `crawlAndBuildTree(mid)`: extracts MID from URL (supports `https://weibo.com/{uid}/{mid}?...`). Falls back to `spider/weibo_content_spider.py status` when official `statuses/show` fails.
2. Repost recursion: official `statuses/repost_timeline` → on first failure, switches to `spider/weibo_content_spider.py repostTimeline` for all remaining pages + recursive calls. Dual-source: official API returns `reposts` array, spider wrapper normalizes `data` → `reposts`.
3. MID extraction: `extractMid()` handles `weibo.com/{uid}/{mid}` URLs (extracts `{mid}`), numeric MIDs (9-16 digits), and plain MIDs. Both TraceService and weibo_content_spider.py have extraction logic. The status response's resolved numeric MID is used for subsequent calls (short MIDs like `QCVaxBM5m` don't work with repost timeline API).
4. Standalone Python script `spider/repost_crawler.py` also available for cookie-based tracing with Neo4j/MySQL persistence.
5. Neo4j query `findAllNodesByRootMid` uses `OPTIONAL MATCH (p)-[r:REPOSTED_FROM]->(parent)` to load parent relationships for graph link rendering.

**Python environment:** `/opt/anaconda3/envs/weibo_spider/bin/python`, configured via `weibo.python.path` in `application.properties`.

**Cookie management:** Stored in `spider/weibo_config.json`, refreshed via `spider/cookie_helper.py` (Playwright browser login, valid ~6 hours).

**Official API config:** App Key `4086931651`, `access_token` in `application.properties` (`weibo.open.*`). Token validation at boot via `oauth2/get_token_info`. Known permission gaps: `trends/hourly`, `statuses/show` (code 20112).

**Removed legacy:** `HttpUtils` (cookie-based scraping), `TraceAsyncService` (Python spider) — all deleted.

**Static frontend:** `src/main/resources/static/` — each page is a standalone HTML file. Vue 3 and ECharts loaded from CDN. All state is per-page, no shared Vuex/Pinia store.

## Required infrastructure

- MySQL on `localhost:3306/cpms` (Flyway auto-migrates)
- Elasticsearch on `localhost:9200` (needs `ik` analyzer plugin installed)
- Neo4j on `bolt://localhost:7687` (Docker compose in `docker/neo4j/`)
- Python 3 (Anaconda env `weibo_spider` at `/opt/anaconda3/envs/weibo_spider/bin/python`) with packages: `requests`, `pymysql`, `py2neo`, `playwright`
- Cookie file `spider/weibo_config.json` — if missing or expired (>6h), run `python spider/cookie_helper.py` to scan QR login

## Spider scripts

| Script | Purpose | Called from |
|---|---|---|
| `spider/hot_search_spider.py` | Scrape realtime hot search via Ajax | `WeiboServiceImpl` (subprocess) |
| `spider/weibo_content_spider.py` | Fetch post detail (`status`), comments (`comments`), repost timeline (`repostTimeline`) via Ajax; URL→MID auto-extraction | `CommentAnalysisServiceImpl` (subprocess, fallback), `TraceService` (subprocess, fallback) |
| `spider/repost_crawler.py` | Build repost tree (standalone) | Command line: `python repost_crawler.py <mid>` |
| `spider/cookie_helper.py` | Playwright browser login → save cookie to `weibo_config.json` | Manual when cookie expires |
