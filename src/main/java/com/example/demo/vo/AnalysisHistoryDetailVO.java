package com.example.demo.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class AnalysisHistoryDetailVO {
    private String mid;
    private LocalDateTime lastAnalyzeTime;
    private int totalComments;
    private int positiveCount;
    private int neutralCount;
    private int negativeCount;
    private List<CommentSnippet> comments;      // 评论列表
    private List<Map<String, Object>> wordCloudData; // 词云数据

    @Data
    public static class CommentSnippet {
        private String text;
        private double sentiment;
    }
}