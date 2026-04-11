package com.example.demo.service.impl;

import com.example.demo.entity.WeiboComment;
import com.example.demo.mapper.CommentMapper;
import com.example.demo.service.CommentAnalysisService;
import com.example.demo.service.ElasticsearchService;
import com.example.demo.utils.HttpUtils;
import com.example.demo.utils.SentimentAnalyzer;
import com.example.demo.vo.AnalysisHistoryDetailVO;
import com.example.demo.vo.AnalysisHistoryVO;
import com.example.demo.vo.AnalysisResultVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Autowired
    private ElasticsearchService elasticsearchService;

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
    public AnalysisResultVO analyzeComments(String mid, Integer userId) {
        List<JsonNode> allComments = new ArrayList<>();
        String currentMaxId = "0";
        int currentMaxIdType = 0;
        int totalPages = 0;
        int maxPageLimit = 5;

        // 获取微博标题
        String weiboTitle = fetchWeiboTitle(mid);

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
                currentMaxId = root.path("max_id").asText(null);
                currentMaxIdType = root.path("max_id_type").asInt(0);
                if (currentMaxId == null || currentMaxId.isEmpty() || "0".equals(currentMaxId)) break;
                Thread.sleep(1000);
            }

            if (allComments.isEmpty()) return new AnalysisResultVO(0, 0, 0, null, "未抓取到有效数据", null);

            return processAnalysis(allComments, mid, userId, weiboTitle);

        } catch (Exception e) {
            return new AnalysisResultVO(0, 0, 0, null, "分析失败: " + e.getMessage(), null);
        }
    }

    private AnalysisResultVO processAnalysis(List<JsonNode> allComments, String mid, Integer userId, String weiboTitle) {
        List<WeiboComment> commentEntities = new ArrayList<>();
        Map<String, Integer> wordCounts = new HashMap<>();
        List<AnalysisResultVO.CommentSnippet> snippets = new ArrayList<>();
        int pos = 0, neu = 0, neg = 0;

        for (JsonNode node : allComments) {
            String content = node.path("text_raw").asText(null);
            if (content == null || content.isEmpty()) continue;

            content = content.replaceAll("\\[[^\\]]+\\]", "").trim();
            if (content.isEmpty()) continue;

            double score = sentimentAnalyzer.analyzeScore(content);

            String type;
            if (score > 0.3) { pos++; type = "POSITIVE"; }
            else if (score < -0.3) { neg++; type = "NEGATIVE"; }
            else { neu++; type = "NEUTRAL"; }

            WeiboComment comment = new WeiboComment();
            comment.setMid(mid);
            comment.setWeiboTitle(weiboTitle);   // 设置标题
            comment.setContent(content);
            comment.setSentimentScore(score);
            comment.setSentimentType(type);
            comment.setUserId(userId);
            commentEntities.add(comment);

            snippets.add(new AnalysisResultVO.CommentSnippet(content, score));

            // 使用 Elasticsearch 分词
            List<String> tokens = elasticsearchService.analyzeSingleTextAsList(content);
            for (String token : tokens) {
                wordCounts.put(token, wordCounts.getOrDefault(token, 0) + 1);
            }
        }

        processComments(commentEntities);

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
        Map<String, Integer> stats = new HashMap<>();
        stats.put("POSITIVE", 0);
        stats.put("NEUTRAL", 0);
        stats.put("NEGATIVE", 0);
        return stats;
    }

    private void updateProgress(String mid, int totalPages) {
        int progress = Math.min(95, totalPages * 20);
        progressMap.put(mid, progress);
    }

    @Override
    public List<AnalysisHistoryVO> getUserHistory(Integer userId) {
        return commentMapper.getUserHistory(userId);
    }

    @Override
    public AnalysisHistoryDetailVO getHistoryDetail(String mid, Integer userId) {
        // 1. 获取摘要信息
        List<AnalysisHistoryVO> historyList = commentMapper.getUserHistory(userId);
        AnalysisHistoryVO summary = historyList.stream()
                .filter(h -> h.getMid().equals(mid))
                .findFirst().orElse(null);
        if (summary == null) return null;

        // 2. 获取评论列表
        List<AnalysisHistoryDetailVO.CommentSnippet> comments =
                commentMapper.getCommentsByMidAndUser(mid, userId);

        // 3. 生成词云（如果 ES 失败，则返回空词云）
        List<Map<String, Object>> wordCloudData = new ArrayList<>();
        try {
            Map<String, Integer> wordCounts = new HashMap<>();
            for (AnalysisHistoryDetailVO.CommentSnippet snippet : comments) {
                String text = snippet.getText();
                if (text == null || text.isEmpty()) continue;
                text = text.replaceAll("\\[[^\\]]+\\]", "").trim();
                if (text.isEmpty()) continue;
                List<String> tokens = elasticsearchService.analyzeSingleTextAsList(text);
                for (String token : tokens) {
                    wordCounts.put(token, wordCounts.getOrDefault(token, 0) + 1);
                }
            }
            wordCloudData = wordCounts.entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(50)
                    .map(e -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("name", e.getKey());
                        map.put("value", e.getValue());
                        return map;
                    }).collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("生成历史词云失败: " + e.getMessage());
            // wordCloudData 保持为空列表
        }

        AnalysisHistoryDetailVO detail = new AnalysisHistoryDetailVO();
        detail.setMid(summary.getMid());
        detail.setLastAnalyzeTime(summary.getLastAnalyzeTime());
        detail.setTotalComments(summary.getTotalComments());
        detail.setPositiveCount(summary.getPositiveCount());
        detail.setNeutralCount(summary.getNeutralCount());
        detail.setNegativeCount(summary.getNegativeCount());
        detail.setComments(comments);
        detail.setWordCloudData(wordCloudData);
        return detail;
    }

    // 新增方法：获取微博标题
    private String fetchWeiboTitle(String mid) {
        try {
            String url = "https://weibo.com/ajax/statuses/show?id=" + mid;
            String json = httpUtils.executeRequest(url);
            if (json != null) {
                JsonNode root = objectMapper.readTree(json);
                String text = root.path("text_raw").asText();
                if (text.isEmpty()) text = root.path("text").asText();
                return text.length() > 50 ? text.substring(0, 50) + "..." : text;
            }
        } catch (Exception e) {
            System.err.println("获取微博标题失败: " + e.getMessage());
        }
        return mid; // 失败时回退到 MID
    }

    @Override
    @Transactional
    public void deleteHistoryByMid(String mid, Integer userId) {
        commentMapper.deleteByMidAndUser(mid, userId);
    }

    @Override
    @Transactional
    public void deleteAllHistory(Integer userId) {
        commentMapper.deleteAllByUser(userId);
    }

}