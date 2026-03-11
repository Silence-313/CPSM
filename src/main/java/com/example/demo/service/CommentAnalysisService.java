// src/main/java/com/example/demo/service/CommentAnalysisService.java
package com.example.demo.service;
import com.example.demo.vo.AnalysisResultVO;

public interface CommentAnalysisService {
    AnalysisResultVO analyzeComments(String mid);
    int getProgress(String mid); // 新增：获取进度
}