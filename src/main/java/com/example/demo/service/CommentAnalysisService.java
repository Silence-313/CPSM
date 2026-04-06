package com.example.demo.service;

import com.example.demo.entity.WeiboComment;
import com.example.demo.vo.AnalysisResultVO;
import java.util.List;
import java.util.Map;

public interface CommentAnalysisService {
    AnalysisResultVO analyzeComments(String mid);
    int getProgress(String mid);
    void processComments(List<WeiboComment> comments);
    // 新增此方法定义
    Map<String, Integer> getSentimentDistribution(String mid);
}