package com.example.demo.service;

import com.example.demo.common.WeiboIdParser;
import com.example.demo.entity.PostNode;
import com.example.demo.entity.WeiboRepostTree;
import com.example.demo.mapper.WeiboRepostTreeMapper;
import com.example.demo.repository.RepostNeo4jRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TraceService {

    private final RepostNeo4jRepository neo4jRepository;
    private final OpenAICompatibleService aiService;
    private final WeiboOpenApiService weiboApiService;
    private final WeiboRepostTreeMapper repostTreeMapper;

    @Value("${weibo.python.path}")
    private String pythonPath;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, TaskStatus> taskStatusMap = new ConcurrentHashMap<>();

    public enum TaskStatus {
        PENDING, RUNNING, COMPLETED, FAILED
    }

    @Async
    public void asyncBuildTraceTree(String mid) {
        mid = WeiboIdParser.parseMid(mid);
        if (mid == null) {
            log.error("❌ 无法识别的微博链接或ID");
            return;
        }
        log.info("🦋 启动溯源抓取，MID = {}", mid);
        taskStatusMap.put(mid, TaskStatus.RUNNING);
        try {
            crawlAndBuildTree(mid);
            taskStatusMap.put(mid, TaskStatus.COMPLETED);
            log.info("✅ 溯源抓取完成，MID = {}", mid);
        } catch (Exception e) {
            log.error("❌ 抓取失败", e);
            taskStatusMap.put(mid, TaskStatus.FAILED);
        }
    }

    // 2. 核心递归抓取逻辑（官方API优先，爬虫降级）
    private void crawlAndBuildTree(String rootMid) {
        log.info("🔍 开始溯源抓取，MID = {}", rootMid);

        // 抓取根节点：优先官方API
        JsonNode status = weiboApiService.getStatusById(rootMid);
        if (status == null) {
            log.warn("⚠️ 官方API获取微博详情失败，降级到爬虫 mid={}", rootMid);
            status = callContentSpiderStatus(rootMid);
        }
        if (status == null) {
            throw new RuntimeException("无法获取微博详情，官方API和爬虫均失败。mid=" + rootMid);
        }

        // 使用爬虫/API 返回的真实数字 MID 用于后续 API 调用（短 MID 无法用于转发列表接口）
        // controllerMid 保持前端传入的 MID，用于 Neo4j/MySQL 存储时的 root_mid 字段，确保前后端查询一致
        String controllerMid = rootMid;
        String resolvedMid = status.path("mid").asText();
        String apiMid = (resolvedMid != null && !resolvedMid.isEmpty()) ? resolvedMid : rootMid;
        if (!apiMid.equals(rootMid)) {
            log.info("📌 API 解析后的数字 MID = {}，controller MID = {}", apiMid, controllerMid);
        }

        String screenName = status.path("user").path("screen_name").asText();
        String uid = status.path("user").path("id").asText();
        // 兼容官方API(text)和爬虫(text_raw)两种字段名
        String content = status.path("text_raw").asText();
        if (content.isEmpty()) {
            content = status.path("text").asText();
        }
        int repostNum = status.path("reposts_count").asInt();
        LocalDateTime postTime = parseWeiboTime(status.path("created_at").asText());

        // 保存根节点：mid=apiMid（用于图谱节点匹配），rootMid=controllerMid（用于前端查询）
        savePostNode(apiMid, null, controllerMid, uid, screenName, content, repostNum, postTime);

        // 递归抓取转发（useSpider[0] 作为是否降级到爬虫的标志位，默认为 false）
        Set<String> visited = new HashSet<>();
        visited.add(apiMid);
        boolean[] useSpider = new boolean[]{false};
        crawlRepostRecursive(controllerMid, apiMid, 1, visited, useSpider);
    }

    private LocalDateTime parseWeiboTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return LocalDateTime.now();
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                    "EEE MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH
            );
            return LocalDateTime.parse(timeStr, formatter);
        } catch (Exception e) {
            log.warn("时间解析失败: {}", timeStr);
            return LocalDateTime.now();
        }
    }

    private void crawlRepostRecursive(String rootMid, String parentMid, int depth,
                                       Set<String> visited, boolean[] useSpider) {
        if (depth > 5) return; // 最大深度5层

        int page = 1;
        while (true) {
            JsonNode repostData = fetchRepostList(parentMid, page, useSpider);
            if (repostData == null) break;
            JsonNode reposts = repostData.path("reposts");
            if (!reposts.isArray() || reposts.size() == 0) break;

            for (JsonNode repost : reposts) {
                String mid = repost.path("id").asText();
                if (visited.contains(mid)) continue;
                visited.add(mid);

                // 提取字段并保存节点
                String childScreenName = repost.path("user").path("screen_name").asText();
                String childUid = repost.path("user").path("id").asText();
                // 兼容官方API(text)和爬虫(text_raw)两种字段名
                String childContent = repost.path("text_raw").asText();
                if (childContent.isEmpty()) {
                    childContent = repost.path("text").asText();
                }
                int childRepostNum = repost.path("reposts_count").asInt();
                LocalDateTime childPostTime = parseWeiboTime(repost.path("created_at").asText());

                savePostNode(mid, parentMid, rootMid, childUid, childScreenName,
                        childContent, childRepostNum, childPostTime);

                // 递归
                crawlRepostRecursive(rootMid, mid, depth + 1, visited, useSpider);
            }
            // 本页不满200条说明已是最后一页
            if (reposts.size() < 200) break;
            page++;
            // 简单的控制频率，避免超限
            try { Thread.sleep(useSpider[0] ? 2000 : 500); } catch (InterruptedException e) {}
        }
    }

    private JsonNode fetchRepostList(String mid, int page, boolean[] useSpider) {
        // 已降级到爬虫，直接用爬虫
        if (useSpider[0]) {
            return callContentSpiderRepostList(mid, page);
        }

        // 优先尝试官方API
        JsonNode result = weiboApiService.getRepostList(mid, 200, page);
        if (result != null) {
            return result;
        }

        // 官方API失败 → 降级到爬虫
        log.warn("⚠️ 官方API获取转发列表失败，降级到爬虫 mid={} page={}", mid, page);
        useSpider[0] = true;
        return callContentSpiderRepostList(mid, page);
    }

    // ========== Python 爬虫调用 ==========

    /**
     * 获取微博正文详情（爬虫降级）
     */
    private JsonNode callContentSpiderStatus(String mid) {
        try {
            JsonNode result = callContentSpider("status", mid, 0, 0);
            if (result != null && result.path("success").asBoolean(false)) {
                return result;
            }
        } catch (Exception e) {
            log.error("爬虫获取微博详情失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取转发列表（爬虫降级），返回格式与官方API一致（含 reposts 数组）
     */
    private JsonNode callContentSpiderRepostList(String mid, int page) {
        try {
            JsonNode result = callContentSpider("repostTimeline", mid, page, 200);
            if (result != null && result.path("success").asBoolean(false)) {
                // 爬虫返回 { "success": true, "data": [...] }
                String wrapped = "{\"reposts\":" + result.path("data").toString() + "}";
                return objectMapper.readTree(wrapped);
            }
            // 记录具体失败原因
            if (result != null) {
                String error = result.path("error").asText("");
                log.warn("爬虫获取转发列表失败: {} mid={} page={}", error, mid, page);
                if (error.contains("登录") || error.contains("Cookie")) {
                    log.warn("⚠️ Cookie 已过期，请运行: python spider/cookie_helper.py");
                }
            }
        } catch (Exception e) {
            log.error("爬虫获取转发列表失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 调用 spider/weibo_content_spider.py 统一入口
     */
    private JsonNode callContentSpider(String action, String mid, int page, int count) {
        try {
            String spiderPath = System.getProperty("user.dir") + "/spider/weibo_content_spider.py";

            List<String> cmd = new ArrayList<>();
            cmd.add(pythonPath);
            cmd.add(spiderPath);
            cmd.add(action);
            cmd.add(mid);
            if (page > 0) {
                cmd.add(String.valueOf(page));
                if (count > 0) {
                    cmd.add(String.valueOf(count));
                }
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0 || output.length() == 0) {
                log.error("爬虫执行失败, exitCode={}, cmd={}", exitCode, String.join(" ", cmd));
                return null;
            }

            return objectMapper.readTree(output.toString());
        } catch (Exception e) {
            log.error("调用爬虫失败: {}", e.getMessage());
            return null;
        }
    }

    // ========== 查询方法 ==========

    public Map<String, Object> getTreeData(String rootMid) {
        List<PostNode> nodes = neo4jRepository.findAllNodesByRootMid(rootMid);
        if (nodes.isEmpty()) {
            return Map.of("nodes", List.of(), "links", List.of());
        }

        // 转换为 ECharts 格式
        List<Map<String, Object>> graphNodes = new ArrayList<>();
        List<Map<String, Object>> graphLinks = new ArrayList<>();

        // 从 MySQL 获取父子关系映射
        Map<String, String> parentMap = new HashMap<>();
        try {
            List<Map<String, String>> mappings = repostTreeMapper.getParentMappings(rootMid);
            if (mappings != null) {
                for (Map<String, String> m : mappings) {
                    String mid = m.get("mid");
                    String parentMid = m.get("parent_mid");
                    if (mid != null && parentMid != null) {
                        parentMap.put(mid, parentMid);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("从 MySQL 获取父子关系失败: {}", e.getMessage());
        }

        // 计算每个节点的深度（从根节点往上追溯）
        Map<String, Integer> depthMap = new HashMap<>();
        for (PostNode node : nodes) {
            int depth = 0;
            String current = node.getMid();
            while (parentMap.containsKey(current)) {
                depth++;
                current = parentMap.get(current);
                if (depth > 10) break; // 防止环形引用
            }
            depthMap.put(node.getMid(), depth);
        }

        for (PostNode node : nodes) {
            Map<String, Object> n = new HashMap<>();
            n.put("id", node.getMid());
            n.put("name", node.getScreenName());
            n.put("value", node.getRepostCount());
            n.put("sentiment", node.getSentiment());
            n.put("depth", depthMap.getOrDefault(node.getMid(), 0));
            n.put("postTime", node.getPostTime() != null ? node.getPostTime().toString() : "");
            // 前端根据 depth + sentiment 统一着色，不再后端预设 itemStyle
            graphNodes.add(n);

            // 构建连线：从 parent（源）到当前节点（目标）
            String parentMid = parentMap.get(node.getMid());
            if (parentMid != null) {
                Map<String, Object> link = new HashMap<>();
                link.put("source", parentMid);
                link.put("target", node.getMid());
                graphLinks.add(link);
            }
        }

        return Map.of("nodes", graphNodes, "links", graphLinks);
    }

    public String analyzeKeyNode(String mid) {
        log.info("🤖 AI 分析节点，mid = {}", mid);
        PostNode node = neo4jRepository.findById(mid).orElse(null);
        if (node == null) return "节点不存在";

        String prompt = String.format(
                "在微博热搜事件中，用户「%s」于%s转发了一条微博，获得了%d次二次转发。请结合微博生态特征，判断该用户是否为本次传播的关键引爆点，并给出理由（50字以内）。",
                node.getScreenName(),
                node.getPostTime() != null ? node.getPostTime().toString() : "未知时间",
                node.getRepostCount() != null ? node.getRepostCount() : 0
        );
        OpenAICompatibleService.ModelResponse resp = aiService.callSiliconflow(prompt);
        log.info("✅ AI 分析完成，结果长度: {}", resp.content != null ? resp.content.length() : 0);
        return resp.success ? resp.content : "AI分析暂不可用";
    }

    public TaskStatus getTaskStatus(String mid) {
        return taskStatusMap.getOrDefault(mid, TaskStatus.PENDING);
    }

    // savePostNode 方法
    private void savePostNode(String mid, String parentMid, String rootMid,
                              String uid, String screenName, String content,
                              int repostCount, LocalDateTime postTime) {
        // 1. 保存到 MySQL
        WeiboRepostTree mysqlNode = new WeiboRepostTree();
        mysqlNode.setMid(mid);
        mysqlNode.setParentMid(parentMid);
        mysqlNode.setRootMid(rootMid);
        mysqlNode.setUid(uid);
        mysqlNode.setScreenName(screenName);
        mysqlNode.setContent(content);
        mysqlNode.setRepostNum(repostCount);
        mysqlNode.setAttitude(0);
        mysqlNode.setPostTime(postTime);
        repostTreeMapper.insert(mysqlNode);

        // 2. 保存到 Neo4j
        PostNode neoNode = new PostNode();
        neoNode.setMid(mid);
        neoNode.setRootMid(rootMid);
        neoNode.setScreenName(screenName);
        neoNode.setContent(content);
        neoNode.setRepostCount(repostCount);
        neoNode.setSentiment(0);
        neoNode.setPostTime(postTime);

        // 设置父节点关系（先检查是否已存在该节点，避免重复创建）
        PostNode existing = neo4jRepository.findById(mid).orElse(null);
        if (existing != null) {
            // 节点已存在，仅更新属性
            existing.setRepostCount(repostCount);
            existing.setContent(content);
            neoNode = existing;
        }

        if (parentMid != null && neoNode.getParent() == null) {
            PostNode parentNode = neo4jRepository.findById(parentMid).orElse(null);
            if (parentNode != null) {
                neoNode.setParent(parentNode);
            }
        }
        neo4jRepository.save(neoNode);
    }

}