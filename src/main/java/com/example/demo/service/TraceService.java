package com.example.demo.service;

import com.example.demo.entity.PostNode;
import com.example.demo.entity.WeiboRepostTree;
import com.example.demo.mapper.WeiboRepostTreeMapper;
import com.example.demo.repository.RepostNeo4jRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
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

    private final Map<String, TaskStatus> taskStatusMap = new ConcurrentHashMap<>();

    public enum TaskStatus {
        PENDING, RUNNING, COMPLETED, FAILED
    }

    @Async
    public void asyncBuildTraceTree(String mid) {
        taskStatusMap.put(mid, TaskStatus.RUNNING);
        log.info("🦋 启动官方API溯源抓取，MID = {}", mid);
        try {
            crawlAndBuildTree(mid);
            taskStatusMap.put(mid, TaskStatus.COMPLETED);
            log.info("✅ 官方API溯源抓取完成，MID = {}", mid);
        } catch (Exception e) {
            log.error("❌ 抓取失败", e);
            taskStatusMap.put(mid, TaskStatus.FAILED);
        }
    }

    // 2. 核心递归抓取逻辑（Java实现）
    private void crawlAndBuildTree(String rootMid) {
        // 抓取根节点
        JsonNode status = weiboApiService.getStatusById(rootMid);
        if (status == null) {
            log.warn("⚠️ 无法获取微博详情，可能是 token 失效或微博不可访问。mid={}", rootMid);
            throw new RuntimeException("无法获取微博详情，请检查 Access Token 是否有效");
        }

        String screenName = status.path("user").path("screen_name").asText();
        String uid = status.path("user").path("id").asText();
        String content = status.path("text").asText();
        int repostNum = status.path("reposts_count").asInt();
        LocalDateTime postTime = parseWeiboTime(status.path("created_at").asText());

        // 保存根节点
        savePostNode(rootMid, null, rootMid, uid, screenName, content, repostNum, postTime);

        // 递归抓取转发
        Set<String> visited = new HashSet<>();
        visited.add(rootMid);
        crawlRepostRecursive(rootMid, rootMid, 1, visited);
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

    private void crawlRepostRecursive(String rootMid, String parentMid, int depth, Set<String> visited) {
        if (depth > 5) return; // 最大深度5层

        int page = 1;
        while (true) {
            JsonNode root = weiboApiService.getRepostList(parentMid, 200, page++);
            if (root == null) break;
            JsonNode reposts = root.path("reposts");
            if (!reposts.isArray() || reposts.size() == 0) break;

            for (JsonNode repost : reposts) {
                String mid = repost.path("id").asText();
                if (visited.contains(mid)) continue;
                visited.add(mid);

                // 提取字段并保存节点
                String childScreenName = repost.path("user").path("screen_name").asText();
                String childUid = repost.path("user").path("id").asText();
                String childContent = repost.path("text").asText();
                int childRepostNum = repost.path("reposts_count").asInt();
                LocalDateTime childPostTime = parseWeiboTime(repost.path("created_at").asText());

                savePostNode(mid, parentMid, rootMid, childUid, childScreenName, childContent, childRepostNum, childPostTime);

                // 递归
                crawlRepostRecursive(rootMid, mid, depth + 1, visited);
            }
            // 简单的控制频率，避免超限
            try { Thread.sleep(500); } catch (InterruptedException e) {}
        }
    }

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