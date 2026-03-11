package com.example.demo.service.impl;

import com.example.demo.service.CommentAnalysisService;
import com.example.demo.utils.HttpUtils;
import com.example.demo.vo.AnalysisResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CommentAnalysisServiceImpl implements CommentAnalysisService {

    @Autowired
    private HttpUtils httpUtils;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, Integer> progressMap = new ConcurrentHashMap<>();

    @Override
    public int getProgress(String mid) {
        return progressMap.getOrDefault(mid, 0);
    }

    @Override
    public AnalysisResultVO analyzeComments(String mid) {
        List<JsonNode> allComments = new ArrayList<>();
        String maxId = "0";
        int totalPages = 0;
        int maxPageLimit = 100;

        try {
            while (totalPages < maxPageLimit) {
                // 1. 发起请求
                String json = httpUtils.getWeiboCommentsJson(mid, maxId);
                if (json == null || json.isEmpty()) break;

                JsonNode root = objectMapper.readTree(json);
                if (root.path("ok").asInt() != 1) break;

                // 2. 提取数据
                JsonNode data = root.path("data");
                if (data.isArray() && data.size() > 0) {
                    data.forEach(allComments::add);
                } else {
                    break; // 该页没数据，说明到头了
                }

                // 3. 更新进度（此处可配合进度条逻辑）
                totalPages++;
                updateProgress(mid, totalPages);

                // 获取下一页票据
                maxId = root.path("max_id").asText();

                // 关键：微博返回 max_id 为 "0" 或空时，表示最后一页
                if ("0".equals(maxId) || maxId.isEmpty()) {
                    System.out.println("成功抓取全部评论，总数：" + allComments.size());
                    break;
                }

                totalPages++;

                // --- 安全防线：频率限制 ---
                // 抓全量评论必须慢！建议每页随机等待 1-2 秒，模拟人类翻页
                Thread.sleep(1000 + (int)(Math.random() * 1000));
            }

            // --- 第二部分：统一处理抓取到的所有评论 ---
            if (allComments.isEmpty()) {
                return new AnalysisResultVO(0, 0, 0, null, "未抓取到任何评论");
            }

            List<AnalysisResultVO.CommentSnippet> snippets = new ArrayList<>();
            int pos = 0, neu = 0, neg = 0;

            for (JsonNode node : allComments) {
                // 使用 text_raw 字段，这是最干净的文本
                String content = node.path("text_raw").asText();
                if (content == null || content.trim().isEmpty()) continue;

                double score = mockAnalyze(content);
                if (score > 0.6) pos++;
                else if (score < 0.4) neg++;
                else neu++;

                // 选取前 10 条典型的作为展示片段
                if (snippets.size() < 10) {
                    snippets.add(new AnalysisResultVO.CommentSnippet(content, score));
                }
            }

            return new AnalysisResultVO(pos, neu, neg, snippets, null);

        } catch (Exception e) {
            e.printStackTrace();
            return new AnalysisResultVO(0, 0, 0, null, "分析失败: " + e.getMessage());
        }
    }

    // 在类内部添加这个私有方法
    private void updateProgress(String mid, int totalPages) {
        int progress;
        if (totalPages <= 10) {
            // 前10页，每页加 5% (最高 50%)
            progress = totalPages * 5;
        } else if (totalPages <= 50) {
            // 11-50页，每页加 1% (最高 90%)
            progress = 50 + (totalPages - 10);
        } else {
            // 50页以后，每页加 0.1%，无限逼近 99%
            progress = Math.min(99, 90 + (totalPages - 50) / 10);
        }
        progressMap.put(mid, progress);
    }

    private double mockAnalyze(String text) {
        // 这里的关键词匹配只是为了演示，实际可调用更高级的 NLP 接口
        if (text.contains("好") || text.contains("心") || text.contains("支持") || text.contains("赞")) return 0.9;
        if (text.contains("可悲") || text.contains("失望") || text.contains("难看") || text.contains("不行")) return 0.2;
        return 0.5;
    }
}