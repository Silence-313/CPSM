package com.example.demo.controller;

import com.example.demo.common.WeiboIdParser;
import com.example.demo.dto.AnalysisRequest;
import com.example.demo.service.CommentAnalysisService;
import com.example.demo.utils.SentimentAnalyzer;
import com.example.demo.vo.AnalysisHistoryDetailVO;
import com.example.demo.vo.AnalysisHistoryVO;
import com.example.demo.vo.AnalysisResultVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analysis")
public class CommentAnalysisController {

    @Autowired
    private CommentAnalysisService analysisService;

    @Autowired
    private SentimentAnalyzer sentimentAnalyzer;

    /**
     * 发起微博分析任务
     */
    @PostMapping("/weibo")
    public AnalysisResultVO doAnalyze(@RequestBody AnalysisRequest request, HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) {
            throw new RuntimeException("请先登录");
        }
        String mid = WeiboIdParser.parseMid(request.getMid());
        if (mid == null) {
            throw new IllegalArgumentException("无法识别的微博链接或 ID");
        }
        return analysisService.analyzeComments(mid, userId);
    }

    /**
     * 获取爬取进度
     */
    @GetMapping("/progress")
    public int getProgress(@RequestParam String mid) {
        String realMid = WeiboIdParser.parseMid(mid);
        return analysisService.getProgress(realMid != null ? realMid : mid);
    }

    /**
     * 获取情感倾向分布统计
     */
    @GetMapping("/sentiment-stats")
    public Map<String, Integer> getSentimentStats(@RequestParam String mid) {
        String realMid = WeiboIdParser.parseMid(mid);
        return analysisService.getSentimentDistribution(realMid != null ? realMid : mid);
    }

    /**
     * 获取用户历史分析记录列表
     */
    @GetMapping("/history")
    public ResponseEntity<?> getHistory(HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body("请先登录");
        }
        List<AnalysisHistoryVO> history = analysisService.getUserHistory(userId);
        return ResponseEntity.ok(history);
    }

    /**
     * 获取某次历史分析的完整详情（评论+词云）
     */
    @GetMapping("/history/detail")
    public ResponseEntity<?> getHistoryDetail(@RequestParam String mid, HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body("请先登录");
        }
        AnalysisHistoryDetailVO detail = analysisService.getHistoryDetail(mid, userId);
        if (detail == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(detail);
    }

    /**
     * 删除单条历史记录（按 MID）
     */
    @DeleteMapping("/history/{mid}")
    public ResponseEntity<?> deleteHistory(@PathVariable String mid, HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body("请先登录");
        }
        analysisService.deleteHistoryByMid(mid, userId);
        return ResponseEntity.ok("删除成功");
    }

    /**
     * 清空当前用户全部历史记录
     */
    @DeleteMapping("/history")
    public ResponseEntity<?> deleteAllHistory(HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body("请先登录");
        }
        analysisService.deleteAllHistory(userId);
        return ResponseEntity.ok("清空成功");
    }

    @PostMapping("/sentiment/score")
    public Map<String, Object> getSentimentScore(@RequestBody Map<String, String> payload) {
        String text = payload.get("text");
        double score = sentimentAnalyzer.analyzeScore(text);
        return Map.of("score", score);
    }

}