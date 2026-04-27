package com.example.demo.service.impl;

import com.example.demo.entity.WeiboComment;
import com.example.demo.mapper.CommentMapper;
import com.example.demo.service.CommentAnalysisService;
import com.example.demo.service.ElasticsearchService;
import com.example.demo.service.WeiboOpenApiService;
import com.example.demo.utils.SentimentAnalyzer;
import com.example.demo.vo.AnalysisHistoryDetailVO;
import com.example.demo.vo.AnalysisHistoryVO;
import com.example.demo.vo.AnalysisResultVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class CommentAnalysisServiceImpl implements CommentAnalysisService {

    @Autowired
    private WeiboOpenApiService weiboOpenApiService;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private SentimentAnalyzer sentimentAnalyzer;

    @Autowired
    private ElasticsearchService elasticsearchService;

    @Value("${weibo.python.path}")
    private String pythonPath;

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
        int page = 1;
        int maxPages = 50;

        String weiboTitle = fetchWeiboTitle(mid);

        // null = 尚未决定，Boolean.TRUE = 爬虫模式，Boolean.FALSE = 官方API模式
        Boolean useSpider = null;

        try {
            while (page <= maxPages) {
                JsonNode comments = null;

                // --- 第1页：优先尝试官方API ---
                if (useSpider == null) {
                    System.out.println("[评论分析] 尝试官方API获取评论 page=" + page + " mid=" + mid);
                    try {
                        JsonNode root = weiboOpenApiService.getComments(mid, 200, page);
                        if (root != null) {
                            comments = root.path("comments");
                            if (comments.isArray() && comments.size() > 0) {
                                System.out.println("[评论分析] 官方API成功，获取到 " + comments.size() + " 条评论");
                                useSpider = false;
                            }
                        }
                    } catch (Exception apiEx) {
                        System.err.println("[评论分析] 官方API抛异常: " + apiEx.getMessage());
                    }

                    // 官方API失败 → 降级到爬虫
                    if (useSpider == null) {
                        System.err.println("[评论分析] 官方API失败，降级到爬虫 page=" + page);
                        useSpider = true;
                        comments = null;
                    }
                }

                // --- 官方API模式（page > 1 且官方API仍在工作） ---
                if (Boolean.FALSE.equals(useSpider) && comments == null) {
                    System.out.println("[评论分析] 官方API获取评论 page=" + page + " mid=" + mid);
                    try {
                        JsonNode root = weiboOpenApiService.getComments(mid, 200, page);
                        if (root != null) {
                            comments = root.path("comments");
                        }
                    } catch (Exception apiEx) {
                        System.err.println("[评论分析] 官方API抛异常 page=" + page + ": " + apiEx.getMessage());
                    }
                    // 官方API后续页也失败了 → 降级到爬虫
                    if (comments == null || !comments.isArray() || comments.size() == 0) {
                        System.err.println("[评论分析] 官方API page=" + page + " 失败，降级到爬虫");
                        useSpider = true;
                        comments = null;
                    }
                }

                // --- 爬虫模式 ---
                if (Boolean.TRUE.equals(useSpider) && comments == null) {
                    System.out.println("[评论分析] 爬虫获取评论 page=" + page + " mid=" + mid);
                    JsonNode spiderResult = callContentSpider("comments", mid, page, 200);
                    if (spiderResult == null) {
                        System.err.println("[评论分析] 爬虫返回null，停止抓取");
                        break;
                    }
                    if (!spiderResult.path("success").asBoolean(false)) {
                        System.err.println("[评论分析] 爬虫返回错误: " + spiderResult.path("error").asText(""));
                        break;
                    }
                    comments = spiderResult.path("data");
                }

                // --- 无数据则停止 ---
                if (comments == null || !comments.isArray() || comments.size() == 0) {
                    System.out.println("[评论分析] 无更多评论，停止 page=" + page);
                    break;
                }

                System.out.println("[评论分析] page=" + page + " 获取 " + comments.size() + " 条评论, source="
                        + (Boolean.TRUE.equals(useSpider) ? "spider" : "official"));

                for (JsonNode node : comments) {
                    allComments.add(node);
                }

                updateProgress(mid, Math.min(95, page * 2));

                // 本页不满200条说明已是最后一页
                if (comments.size() < 200) break;

                page++;
                Thread.sleep(Boolean.TRUE.equals(useSpider) ? 1500 : 1000);
            }

            System.out.println("[评论分析] 抓取完成，共 " + allComments.size() + " 条评论, source="
                    + (Boolean.TRUE.equals(useSpider) ? "spider" : (useSpider == null ? "unknown" : "official")));

            if (allComments.isEmpty()) return new AnalysisResultVO(0, 0, 0, null, "未抓取到有效评论数据", null);

            return processAnalysis(allComments, mid, userId, weiboTitle);

        } catch (Exception e) {
            System.err.println("[评论分析] 异常: " + e.getMessage());
            e.printStackTrace();
            return new AnalysisResultVO(0, 0, 0, null, "分析失败: " + e.getMessage(), null);
        }
    }

    private AnalysisResultVO processAnalysis(List<JsonNode> allComments, String mid, Integer userId, String weiboTitle) {
        List<WeiboComment> commentEntities = new ArrayList<>();
        Map<String, Integer> wordCounts = new HashMap<>();
        List<AnalysisResultVO.CommentSnippet> snippets = new ArrayList<>();
        int pos = 0, neu = 0, neg = 0;

        for (JsonNode node : allComments) {
            // 兼容官方API(text)和爬虫(text_raw)两种字段名
            String content = node.path("text_raw").asText(null);
            if (content == null || content.isEmpty()) {
                content = node.path("text").asText(null);
            }
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

            // 使用 Elasticsearch 分词（失败不影响评论入库）
            try {
                List<String> tokens = elasticsearchService.analyzeSingleTextAsList(content);
                for (String token : tokens) {
                    wordCounts.put(token, wordCounts.getOrDefault(token, 0) + 1);
                }
            } catch (Exception esEx) {
                System.err.println("[评论分析] ES分词失败: " + esEx.getMessage());
            }
        }

        processComments(commentEntities);
        System.out.println("[评论分析] processAnalysis 写入完成，commentEntities.size=" + commentEntities.size());

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
        System.out.println("[评论分析] processComments 开始，共 " + comments.size() + " 条待写入");
        int success = 0;
        for (WeiboComment comment : comments) {
            try {
                commentMapper.insert(comment);
                success++;
            } catch (Exception e) {
                System.err.println("[评论分析] 写入单条评论失败: mid=" + comment.getMid()
                        + ", content=" + (comment.getContent() != null ? comment.getContent().substring(0, Math.min(30, comment.getContent().length())) : "null")
                        + ", error=" + e.getMessage());
                e.printStackTrace();
            }
        }
        System.out.println("[评论分析] processComments 完成，成功写入 " + success + "/" + comments.size() + " 条");
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

    /**
     * 获取微博标题：优先官方API，失败降级到爬虫
     */
    private String fetchWeiboTitle(String mid) {
        // 优先使用官方API
        try {
            JsonNode status = weiboOpenApiService.getStatusById(mid);
            if (status != null) {
                String text = status.path("text").asText();
                if (text != null && !text.isEmpty()) {
                    return text.length() > 50 ? text.substring(0, 50) + "..." : text;
                }
            }
        } catch (Exception e) {
            System.err.println("官方API获取标题失败，降级到爬虫: " + e.getMessage());
        }
        // 降级到爬虫
        try {
            JsonNode result = callContentSpider("status", mid, 0);
            if (result != null && result.path("success").asBoolean(false)) {
                String text = result.path("text").asText();
                if (text != null && !text.isEmpty()) {
                    return text.length() > 50 ? text.substring(0, 50) + "..." : text;
                }
            }
        } catch (Exception e) {
            System.err.println("爬虫获取微博标题失败: " + e.getMessage());
        }
        return mid;
    }

    /**
     * 调用 spider/weibo_content_spider.py 统一入口
     * @param action  "status" 获取正文 / "comments" 获取评论
     * @param mid     微博ID
     * @param page    页码（status时传0表示不需要此参数）
     * @param optionalCount 每页数量（仅comments需要）
     */
    private JsonNode callContentSpider(String action, String mid, int page, int... optionalCount) {
        try {
            String spiderPath = System.getProperty("user.dir") + "/spider/weibo_content_spider.py";

            List<String> cmd = new ArrayList<>();
            cmd.add(pythonPath);
            cmd.add(spiderPath);
            cmd.add(action);
            cmd.add(mid);
            if (page > 0) {
                cmd.add(String.valueOf(page));
                if (optionalCount.length > 0 && optionalCount[0] > 0) {
                    cmd.add(String.valueOf(optionalCount[0]));
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
                System.err.println("[评论分析] Python爬虫执行失败, exitCode=" + exitCode + ", cmd=" + String.join(" ", cmd));
                if (output.length() > 0) {
                    System.err.println("[评论分析] 爬虫输出: " + output);
                }
                return null;
            }

            return objectMapper.readTree(output.toString());
        } catch (Exception e) {
            System.err.println("调用内容爬虫失败: " + e.getMessage());
            return null;
        }
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