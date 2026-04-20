package com.example.demo.service;

import com.example.demo.entity.PostNode;
import com.example.demo.repository.RepostNeo4jRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TraceService {

    private final RepostNeo4jRepository neo4jRepository;
    private final OpenAICompatibleService aiService;
    private final TraceAsyncService traceAsyncService;  // 新增

    private final Map<String, TaskStatus> taskStatusMap = new ConcurrentHashMap<>();

    public enum TaskStatus {
        PENDING, RUNNING, COMPLETED, FAILED
    }

    // 移除 @Async，改为调用异步服务
    public void asyncBuildTraceTree(String mid) {
        traceAsyncService.executeCrawler(mid, taskStatusMap);
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

}