package com.example.demo.controller;

import com.example.demo.common.WeiboIdParser;
import com.example.demo.dto.AnalysisRequest;
import com.example.demo.service.CommentAnalysisService;
import com.example.demo.vo.AnalysisResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analysis")
public class CommentAnalysisController {

    @Autowired
    private CommentAnalysisService analysisService;

    // src/main/java/com/example/demo/controller/CommentAnalysisController.java
    @PostMapping("/weibo")
    public AnalysisResultVO doAnalyze(@RequestBody AnalysisRequest request) {
        // 关键步骤：解析用户输入的混合内容
        String mid = WeiboIdParser.parseMid(request.getMid());

        if (mid == null) {
            // 这里可以抛出一个自定义异常，或者返回一个带有错误信息的 VO
            throw new IllegalArgumentException("无法识别的微博链接或 ID");
        }

        return analysisService.analyzeComments(mid);
    }

    // 在你的 Controller 类中添加
    @GetMapping("/progress")
    @ResponseBody
    public int getProgress(@RequestParam String mid) {
        // 同样进行一次解析，确保 ID 格式与 Service 内存的一致
        String realMid = WeiboIdParser.parseMid(mid);
        return analysisService.getProgress(realMid != null ? realMid : mid);
    }
}