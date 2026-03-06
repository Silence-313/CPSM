# A celebrity popularity monitoring system based on Weibo

## 📌 项目简介
本项目基于 Spring Boot 框架开发，主要用于学习和实践 Java Web 核心技术栈。

## 🛠 技术栈
- **后端**: Java 17, Spring Boot 4.0.3
- **构建工具**: Maven
- **版本控制**: Git (Gitee)
- **开发工具**: IntelliJ IDEA Ultimate

---

## 🗓 开发日志 (Development Log)

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
---

## 📖 运行说明
1. 克隆项目：`git clone https://gitee.com/silencecm/cpms-on-wei-bo.git`
2. 使用 IDEA 打开项目，等待 Maven 依赖下载完成。
3. 运行主类 `DemoApplication`。
4. 访问地址：`http://localhost:8080`

## 📂 目录结构说明
- `src/main/java`: 存放 Java 源代码
- `src/main/resources`: 存放配置文件及静态资源
- `pom.xml`: 项目依赖管理文件