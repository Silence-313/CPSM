### 2026-03-03
- [x] 使用 IntelliJ IDEA Spring Boot 初始化项目。
- [x] 配置项目元数据（Group: com.example, Artifact: demo）。
- [x] 引入核心依赖：`Spring Web`, `Lombok`, `MySQL Driver`, `MyBatis Framework`。
- [x] 成功关联 Gitee 远程仓库并完成首次推送 (Initial Commit)。
- [x] 在 IDEA 内关联到 MySQL 数据库，为后续数据远程存储打下基础

### 2026-03-05
- [x] **数据库建模**：创建 `monitor_star`（名人表）与 `monitor_keywords`（关键词表），建立一对多关联关系。
- [x] **持久层开发**：编写 `MonitorStarMapper`，实现关联查询 `findAllWithKeywords` 及主键回填插入。
- [x] **核心逻辑实现**：在 `WeiboServiceImpl` 中开发“颗粒度匹配算法”，支持通过名人姓名及其关联关键词组同步检索热搜热度。
- [x] **数据聚合修复**：解决 MyBatis 联表查询导致的记录平铺问题，使用 Stream 流对结果集进行 ID 分组与关键词手动聚合。
- [x] **前端架构搭建**：基于 Vue 3 组合式 API 构建响应式监测看板。
- [x] **UI/UX 优化**：引入 `colorPresets` 预设颜色系统，实现监测项的个性化突出显示（背景与边框联动）。
- [x] **字段对齐专项修复**：
    - 解决后端 `MonitorStar` 实体类字段 `star_name` 与前端模板 `name` 不一致导致的显示异常。
    - 废弃精简版 `StarVO`，统一使用完整实体类以确保 `id` 和 `keywords` 数据链路通畅。
- [x] **功能闭环**：
    - 完成明星项的“快速添加”与“动态移除”功能。
    - 实现关键词的实时管理（回车添加、点击删除），并对接后端持久化接口。
- [x] **实时性增强**：集成自动刷新逻辑（60s 间隔），确保监测看板数据与微博热搜同步。
- [x] **实体类构建 (Entity)**：
    - 创建 `User.java`：用户基础信息模型。
    - 创建 `MonitorStar.java`：核心监测对象模型，包含 `star_name`、`keywords` 列表及 `totalHot` 统计字段。
    - 创建 `MonitorKeyword.java`：存储颗粒度关键词的一对多子表模型。
- [x] **数据访问层 (Repository)**：
    - 创建 `MonitorStarMapper.java`：定义 SQL 映射，包含 `LEFT JOIN` 关联查询、`@Options` 主键回填、明星与关键词的增删操作。
    - 创建 `UserMapper.java`：实现用户登录与信息查询接口。
- [x] **业务逻辑层 (Service)**：
    - 创建 `WeiboService.java` 接口：定义热搜抓取、监测分析及关键词管理的方法标准。
    - 创建 `WeiboServiceImpl.java` 实现类：
        - 编写 `getAllStarsWithKeywords`：利用 **Java Stream** 对数据库平铺结果集进行 ID 分组与聚合。
        - 编写 `getMonitorDetail`：实现核心比对算法，将实时热搜词与“明星名+关键词组”进行多维度命中检测。
- [x] **数据传输对象 (DTO) 定义**：
    - `HotSearchItem.java`：严格对应微博 API 返回的单条热搜结构（关键词、热度值、标签）。
    - `HotSearchDTO.java`：封装完整的微博 API 响应外壳。
    - `StarVO.java`：创建视图对象，用于向前端精简传递明星姓名与计算后的聚合热度。
- [x] **工具类开发**：
    - 创建 `HttpUtils.java`：基于 `RestTemplate` 或 `HttpClient` 封装对微博热搜 API 的原始数据请求。
- [x] **控制层开发 (Controller)**：
    - 创建 `HotSearchController.java`：暴露 `/api/weibo/monitor`（热搜列表）、`/api/weibo/stars/detail`（看板数据）及 `/star/keyword`（颗粒度管理）等 RESTful 接口。
- [x] **前端页面开发 (Frontend)**：
    - `login.html`：实现登录界面，通过 localStorage 存储令牌，拦截未授权访问。
    - `portal.html`：构建“功能中枢”导航页，作为进入热度监测系统的入口。
    - 创建 `index.html`：基于 **Vue 3 (Composition API)** 构建单页面应用。
    - 设计响应式看板：实现明星卡片、颗粒度标签云、实时热搜榜的高亮联动显示。
    - 样式定制：编写 CSS 变量系统，支持 `colorPresets` 动态切换监测对象的突出显示颜色。
- [x] **关键问题修复与重构**：
    - **字段映射修复**：重构前端 `fetchData` 逻辑，解决后端 `star_name` 与前端 `name` 属性名不一致导致的渲染失败。
    - **逻辑闭环**：修复 `StarVO` 数据丢失问题，统一改用深度模型返回数据，确保关键词管理（添加/删除）功能链路通畅。
    - **交互优化**：增加回车快速添加关键词、自动刷新开关及退出登录等完整交互逻辑。
- 遗留问题：在`index.html`页面中未解决**人名**与**标签**同时显示的逻辑

### 2026-03-06
- [x] **遗留问题的修复与拓展**：
    - 成功解决遗留问题，同时保留`HostSearchController`中的`getStarDetails`,`getMonitorDetail`两个函数，确保**名人**与**关键词**信息都能匹配。
    - 为**名人**与**关键词**设置相同颜色
- [x] **颜色永久存储**：为`MonitorStar`增加`Color`并同步修改相关代码实现颜色的永久存储。
- [x] **信息以账号为单位的存储**：为`MonitorStar`添加`userId`字段
- [x] **添加对命中次数的统计与显示**
- [x] **通过公网测试**：使用公网穿透工具cpolar实现项目在公网进行访问，尽管加载速度变慢，但仍然能够正常使用。

### 2026-03-11
- [x] **HttpUtils.java** (网络请求工具类)
  - 创建与修改：针对 macOS 环境下的 RestTemplate 请求进行了深度定制。 
  - 核心功能： 
    - 身份伪装：手动构造了包含最新 User-Agent、Referer 和 x-xsrf-token 的请求头。 
    - Session 维持：集成了从浏览器捕获的完整 Cookie（包含 SUB, XSRF-TOKEN 等关键字段），解决了 403 Forbidden 报错。 
    - 动态分页请求：将方法重构为接收 mid 和 maxId 两个参数，通过拼接 URL 实现对微博翻页接口的精准调用。

- [x] **CommentAnalysisServiceImpl.java** (业务逻辑实现类)
  - 创建与修改：实现了从单页抓取向全量循环抓取的逻辑演进。 
  - 核心功能： 
    - 流式爬虫引擎：利用 while(true) 循环，配合 max_id 机制持续抓取评论，直至到达微博返回的最后一页或触及安全上限（100页）。 
    - 异步进度管理：引入 ConcurrentHashMap 记录各任务的 progress，并设计了非线性进度算法（前快后慢），提升用户感知体验。 
    - 数据清洗：精准提取 JSON 中的 text_raw 字段，排除 HTML 干扰，为后续分词做准备。

- [x] **CommentAnalysisController.java** (接口控制层)
  - 创建与修改：定义了前端交互的标准 API 路径。 
  - 核心功能： 
    - POST /api/analysis/weibo：接收前端传来的微博链接或 ID，调用 WeiboIdParser 进行格式化，并启动耗时较长的抓取任务。 
    - GET /api/analysis/progress：响应前端的轮询请求，返回内存中最新的任务百分比，解决了异步任务状态不可见的问题。

- [x] **analysis.html** (分析展示页面)
  - 创建与修改：基于 Vue 3 和 ECharts 构建，适配了最新的异步轮询机制。 
  - 核心功能： 
    - 状态机管理：定义了 loading、progress、hasResult 等状态，控制 UI 在“搜索-进度显示-结果渲染”之间平滑切换。 
    - 双线程交互： 
      1. 分析线程：发起长连接请求进行后端数据爬取。 
      2. 轮询线程：每 800ms 访问一次进度接口，驱动页面上的橙色进度条平滑移动。 
    - 数据可视化：调用 ECharts 动态渲染饼图（情感比例）和表格（典型评论摘要）。

- [x] **AnalysisRequest.java** (数据传输对象 DTO)
  - 功能：规范前端传参格式，确保 mid 字段能被 Spring 正确反序列化。

- [x] **WeiboIdParser.java** (ID 解析工具类)
  - 功能：负责将用户输入的各种微博链接（如 https://weibo.com/u/123...）正则解析为纯数字 MID，保证后端接口调用的统一性。

⚠️ 待办事项 (Next Steps)
- 分词与词云图实现：目前前端词云卡片仍为空白，需引入 HanLP 或 Jieba 分词组件对全量评论进行热词提取。 
- Cookie 动态管理：当前的 Cookie 是静态硬编码，需研究如何实现自动更新或通过配置文件注入。 
- 情感字典升级：目前的关键词匹配过于简单，计划引入专门的中文情感倾向词库（如知网 Hownet 或大连理工词库）。

### 2026-03-18

- [x] **Python**
  - 在代码中加入`python`代码实现爬虫的逻辑，成功达成cookie的动态管理

⚠️ 待办事项 (Next Steps)
- 分词与词云图实现：目前前端词云卡片仍为空白，需引入 HanLP 或 Jieba 分词组件对全量评论进行热词提取。
- 当前微博评论的爬取结果与微博真实评论数量无法对应。
- 情感字典升级：目前的关键词匹配过于简单，计划引入专门的中文情感倾向词库（如知网 Hownet 或大连理工词库）。

### 2026-03-19

- [x] **评论数量不对应情况**：经过查询资料，因为微博内部对评论存在审核机制，若评论处于待审核或意思违规状态则无法不会显示，但会参与计数，不过对于评论条数较大的微博条目数量差距较大，还需要进行资料查询

根据您提供的项目代码和已有开发日志，我为您撰写了 **4.6 评论分析模块深度优化** 的开发日志：

---

### 2026-04-07

- [x] **评论爬取引擎重构**：
  - **双参数翻页机制**：将 `HttpUtils.getWeiboCommentsJson` 方法签名从 `(mid, maxId)` 升级为 `(mid, maxId, maxIdType)`，增加 `max_id_type` 参数支持，解决微博新版接口翻页断裂问题。
  - **子评论抓取支持**：新增 `getChildCommentsJson(rootId)` 方法，为后续楼中楼评论的递归采集预留接口。
  - **反爬增强**：在请求头中添加 `X-Requested-With: XMLHttpRequest` 标识，模拟 Ajax 请求，降低被识别为爬虫的概率。
  - **响应校验增强**：增加返回内容 HTML 检测逻辑，若接口返回 HTML 页面（登录页或验证页）则立即终止并提示 Cookie 失效。

- [x] **情感分析引擎升级**：
  - **引入 AnsJ 分词器**：替换原有的简单字符串匹配，使用 `ToAnalysis.parse()` 进行中文分词，提升情感判定的准确性。
  - **多词典支持**：创建 `SentimentAnalyzer` 组件，支持三类词典加载：
    - `pos_words.txt`：正面情感词库（如“好”、“赞”、“支持”）
    - `neg_words.txt`：负面情感词库（如“失望”、“难看”、“垃圾”）
    - `not_words.txt`：否定词库（如“不”、“没”、“无”），用于翻转情感极性
    - `degree_words.txt`：程度副词词典（如“非常:2.0”、“稍微:0.5”），支持权重调节
  - **情感得分算法**：实现基于词性序列的情感打分算法，核心逻辑：
    ```
    遍历分词结果：
      - 遇到否定词 → 情感极性翻转（-1 倍乘）
      - 遇到程度副词 → 更新当前权重系数
      - 遇到情感词 → 累加（基础分值 × 权重 × 极性系数）
    ```
  - **词典热加载**：使用 `@PostConstruct` 在 Bean 初始化时从 `classpath:dict/` 目录加载词典文件，支持运行时动态更新。

- [x] **数据库持久化层**：
  - 创建 `WeiboComment` 实体类，字段包括：`id`、`mid`（所属微博ID）、`content`（评论内容）、`sentimentScore`（情感得分）、`sentimentType`（情感类型枚举）
  - 创建 `CommentMapper` 接口，实现评论数据的批量/单条插入，为后续历史数据分析和统计报表奠定基础。
  - 在 `CommentAnalysisServiceImpl.processComments()` 中实现持久化调用，确保每次分析结果自动落库。

- [x] **词云数据生成**：
  - **实时分词统计**：在 `processAnalysis` 方法中，对每条评论进行实时分词和词频统计。
  - **停用词过滤**：维护 `STOP_WORDS` 静态集合，过滤“的、了、在、是”等无意义词汇，确保词云质量。
  - **Top 50 热词提取**：按词频降序排序，截取前 50 个高频词，封装为 `{name, value}` 格式供前端 ECharts 词云组件渲染。

- [x] **API 接口扩展**：
  - **新增情感统计接口**：`GET /api/analysis/sentiment-stats?mid={mid}`，返回 `{"POSITIVE": 50, "NEUTRAL": 30, "NEGATIVE": 20}` 格式数据，专门用于前端 ECharts 饼图渲染。
  - **进度接口优化**：将 `getProgress` 返回类型明确为 `int`，确保前端轮询时能正确解析数值。

- [x] **前端 analysis.html 功能完善**：
  - **词云卡片渲染**：对接后端返回的 `wordCloudData`，调用 ECharts 词云扩展（`echarts-wordcloud`）完成热词可视化展示。
  - **进度文案联动**：根据进度百分比动态切换状态提示文案（如“初始化爬虫环境”→“深度并发抓取历史评论”→“清洗数据并分词”→“生成分析报告”）。
  - **错误处理增强**：对 `errorMsg` 字段进行判断，若存在则弹窗提示并终止进度条动画。

- [x] **配置文件标准化**：
  - 创建 `library.properties` 配置文件，配置 AnsJ 分词器的词典路径、人名识别、数字识别、英文识别等参数，确保分词环境稳定运行。

---

⚠️ 待办事项 (Next Steps)

- **楼中楼评论递归抓取**：当前 `getChildCommentsJson` 方法已预留，但尚未集成到主爬虫流程中，需实现递归或队列机制采集二级及更深层级评论。
- **情感词典扩充**：当前正面/负面词库规模较小（各约 100-200 词），需引入知网 Hownet 或大连理工情感词典（含约 2.7 万词）提升覆盖度。
- **批量持久化优化**：当前为逐条插入，高并发场景下性能较差，需改造为 MyBatis 批量插入（`<foreach>` 或 `batch` 模式）。
- **分布式进度共享**：当前 `progressMap` 为本地 `ConcurrentHashMap`，多实例部署时进度无法共享，需迁移至 Redis。
- **Cookie 自动续期**：当前仍需手动运行 Python 脚本更新 Cookie，需研究微博扫码登录的自动化方案（如使用 Selenium 配合定时任务）。

---

如需我进一步补充 **4.7 评论楼中楼递归抓取** 或 **4.8 情感词典大规模扩充** 的开发日志，请告知！