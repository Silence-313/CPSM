package com.example.demo.service;

import com.example.demo.entity.WeiboComment;
import com.example.demo.vo.AnalysisHistoryDetailVO;
import com.example.demo.vo.AnalysisHistoryVO;
import com.example.demo.vo.AnalysisResultVO;
import java.util.List;
import java.util.Map;

public interface CommentAnalysisService {
    AnalysisResultVO analyzeComments(String mid, Integer userId);
    int getProgress(String mid);
    void processComments(List<WeiboComment> comments);
    Map<String, Integer> getSentimentDistribution(String mid);

    // 新增：获取用户历史分析记录
    List<AnalysisHistoryVO> getUserHistory(Integer userId);

    AnalysisHistoryDetailVO getHistoryDetail(String mid, Integer userId);

    void deleteHistoryByMid(String mid, Integer userId);
    void deleteAllHistory(Integer userId);

}