package com.example.demo.service.impl;

import com.example.demo.entity.WeiboComment;
import com.example.demo.repository.CommentMapper;
import com.example.demo.service.CommentAnalysisService;
import com.example.demo.utils.HttpUtils;
import com.example.demo.utils.SentimentAnalyzer;
import com.example.demo.vo.AnalysisResultVO;
import org.ansj.domain.Result;
import org.ansj.domain.Term;
import org.ansj.splitWord.analysis.ToAnalysis;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class CommentAnalysisServiceImpl implements CommentAnalysisService {

    @Autowired
    private HttpUtils httpUtils;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private SentimentAnalyzer sentimentAnalyzer;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Integer> progressMap = new ConcurrentHashMap<>();

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "的", "了", "在", "是", "我", "你", "他", "它", "们", "这", "那", "就", "也", "不", "而", "及", "与", "回复", "转发"
    ));

    @Override
    public int getProgress(String mid) {
        return progressMap.getOrDefault(mid, 0);
    }

    @Override
    public AnalysisResultVO analyzeComments(String mid) {
        List<JsonNode> allComments = new ArrayList<>();
        String currentMaxId = "0";
        int currentMaxIdType = 0;
        int totalPages = 0;
        int maxPageLimit = 5;

        try {
            while (totalPages < maxPageLimit) {
                String json = httpUtils.getWeiboCommentsJson(mid, currentMaxId, currentMaxIdType);
                if (json == null) break;

                JsonNode root = objectMapper.readTree(json);
                if (root.path("ok").asInt() != 1) break;

                JsonNode data = root.path("data");
                if (data.isArray() && data.size() > 0) {
                    for (JsonNode node : data) {
                        allComments.add(node);
                    }
                } else {
                    break;
                }

                updateProgress(mid, ++totalPages);
                currentMaxId = root.path("max_id").asText("0");
                currentMaxIdType = root.path("max_id_type").asInt(0);
                if ("0".equals(currentMaxId) || currentMaxId.isEmpty()) break;
                Thread.sleep(1000);
            }

            if (allComments.isEmpty()) return new AnalysisResultVO(0, 0, 0, null, "未抓取到有效数据", null);

            // 关键修正：传递 mid 变量
            return processAnalysis(allComments, mid);

        } catch (Exception e) {
            return new AnalysisResultVO(0, 0, 0, null, "分析失败: " + e.getMessage(), null);
        }
    }

    private AnalysisResultVO processAnalysis(List<JsonNode> allComments, String mid) {
        List<WeiboComment> commentEntities = new ArrayList<>();
        Map<String, Integer> wordCounts = new HashMap<>(); // 修正：定义变量
        List<AnalysisResultVO.CommentSnippet> snippets = new ArrayList<>(); // 修正：定义变量
        int pos = 0, neu = 0, neg = 0;

        for (JsonNode node : allComments) {
            String content = node.path("text_raw").asText(); // 修正：去掉已过时的参数
            if (content.isEmpty()) continue;

            double score = sentimentAnalyzer.analyzeScore(content);

            String type;
            if (score > 0.5) { pos++; type = "POSITIVE"; }
            else if (score < -0.5) { neg++; type = "NEGATIVE"; }
            else { neu++; type = "NEUTRAL"; }

            WeiboComment comment = new WeiboComment();
            comment.setMid(mid); // 现在 mid 可用了
            comment.setContent(content);
            comment.setSentimentScore(score);
            comment.setSentimentType(type);
            commentEntities.add(comment);

            if (snippets.size() < 10) {
                snippets.add(new AnalysisResultVO.CommentSnippet(content, score));
            }

            // 分词逻辑
            Result res = ToAnalysis.parse(content);
            for (Term term : res) {
                String word = term.getName().trim();
                if (word.length() > 1 && !STOP_WORDS.contains(word)) {
                    wordCounts.put(word, wordCounts.getOrDefault(word, 0) + 1);
                }
            }
        }

        // 保存数据库
        this.processComments(commentEntities);

        // 词云转换
        List<Map<String, Object>> wordCloudData = wordCounts.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(50)
                .map(e -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", e.getKey());
                    map.put("value", e.getValue());
                    return map;
                }).collect(Collectors.toList());

        return new AnalysisResultVO(pos, neu, neg, snippets, null, wordCloudData);
    }

    @Override
    public void processComments(List<WeiboComment> comments) {
        for (WeiboComment comment : comments) {
            commentMapper.insert(comment);
        }
    }

    @Override
    public Map<String, Integer> getSentimentDistribution(String mid) {
        // 实训演示建议：由于数据库查询较慢，此处可返回 Mock 数据或从数据库 Count
        Map<String, Integer> stats = new HashMap<>();
        // 示例逻辑：这里可以去 Mapper 写一个 selectCountByMidAndType
        stats.put("POSITIVE", 10);
        stats.put("NEUTRAL", 5);
        stats.put("NEGATIVE", 3);
        return stats;
    }

    private void updateProgress(String mid, int totalPages) {
        int progress = Math.min(95, totalPages * 20);
        progressMap.put(mid, progress);
    }
}