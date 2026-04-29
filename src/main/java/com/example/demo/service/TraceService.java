package com.example.demo.service;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
        mid = extractMid(mid);
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

    /**
     * 从 URL 或纯文本中提取微博 MID
     * 支持格式：纯数字MID、字母数字MID、https://weibo.com/{uid}/{mid}?...
     */
    private String extractMid(String input) {
        if (input == null || input.isEmpty()) return input;
        // 尝试匹配 weibo.com/{uid}/{mid} 格式
        Pattern urlPattern = Pattern.compile("weibo\\.com/\\d+/([a-zA-Z0-9]+)");
        Matcher m = urlPattern.matcher(input);
        if (m.find()) {
            return m.group(1);
        }
        // 尝试匹配纯数字 MID（16位左右）
        Pattern numericPattern = Pattern.compile("(\\d{9,16})");
        m = numericPattern.matcher(input.replaceAll("\\s+", ""));
        if (m.find()) {
            return m.group(1);
        }
        // 返回原始输入（可能已经是纯 MID）
        return input.trim();
    }

    // 2. 核心递归抓取逻辑（官方API优先，爬虫降级）
    private void crawlAndBuildTree(String rootMid) {
        // 从 URL 中提取纯 MID（兼容用户粘贴完整链接）
        rootMid = extractMid(rootMid);
        log.info("🔍 提取后的 MID = {}", rootMid);

        // 抓取根节点：优先官方API
        JsonNode status = weiboApiService.getStatusById(rootMid);
        if (status == null) {
            log.warn("⚠️ 官方API获取微博详情失败，降级到爬虫 mid={}", rootMid);
            status = callContentSpiderStatus(rootMid);
        }
        if (status == null) {
            throw new RuntimeException("无法获取微博详情，官方API和爬虫均失败。mid=" + rootMid);
        }

        // 使用爬虫/API 返回的真实数字 MID（短 MID 可能无法用于转发列表接口）
        String resolvedMid = status.path("mid").asText();
        if (resolvedMid != null && !resolvedMid.isEmpty()) {
            rootMid = resolvedMid;
            log.info("📌 使用解析后的数字 MID = {}", rootMid);
        }

        String screenName = status.path("user").path("screen_name").asText();
        String uid = status.path("user").path("id").asText();
        String content = status.path("text").asText();
        int repostNum = status.path("reposts_count").asInt();
        LocalDateTime postTime = parseWeiboTime(status.path("created_at").asText());

        // 保存根节点
        savePostNode(rootMid, null, rootMid, uid, screenName, content, repostNum, postTime);

        // 递归抓取转发（useSpider[0] 作为是否降级到爬虫的标志位，默认为 false）
        Set<String> visited = new HashSet<>();
        visited.add(rootMid);
        boolean[] useSpider = new boolean[]{false};
        crawlRepostRecursive(rootMid, rootMid, 1, visited, useSpider);
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

        Map<String, PostNode> nodeMap = nodes.stream()
                .collect(Collectors.toMap(PostNode::getMid, n -> n));

        for (PostNode node : nodes) {
            Map<String, Object> n = new HashMap<>();
            n.put("id", node.getMid());
            n.put("name", node.getScreenName());
            n.put("value", node.getRepostCount());
            n.put("sentiment", node.getSentiment());
            n.put("postTime", node.getPostTime() != null ? node.getPostTime().toString() : "");

            // 情感颜色
            if (node.getSentiment() != null) {
                if (node.getSentiment() == 1) n.put("itemStyle", Map.of("color", "#52c41a"));
                else if (node.getSentiment() == -1) n.put("itemStyle", Map.of("color", "#f5222d"));
                else n.put("itemStyle", Map.of("color", "#1890ff"));
            } else {
                n.put("itemStyle", Map.of("color", "#1890ff"));
            }
            graphNodes.add(n);

            // 构建连线：当前节点 -> 父节点
            PostNode parent = node.getParent();
            if (parent != null) {
                Map<String, Object> link = new HashMap<>();
                link.put("source", parent.getMid());
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

        // 设置父节点关系
        if (parentMid != null) {
            PostNode parentNode = neo4jRepository.findById(parentMid).orElse(null);
            if (parentNode != null) {
                neoNode.setParent(parentNode);
            }
        }
        neo4jRepository.save(neoNode);
    }

}