package com.example.demo.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AnalysisHistoryVO {
    private String mid;               // 微博ID
    private String weiboTitle;   // 新增
    private LocalDateTime lastAnalyzeTime;  // 最近分析时间
    private int totalComments;        // 总评论数
    private int positiveCount;        // 正面数
    private int neutralCount;         // 中性数
    private int negativeCount;        // 负面数
}