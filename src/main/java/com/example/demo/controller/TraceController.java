package com.example.demo.controller;

import com.example.demo.common.WeiboIdParser;
import com.example.demo.service.TraceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/trace")
@Slf4j
@RequiredArgsConstructor
public class TraceController {

    private final TraceService traceService;

    @PostMapping("/build")
    public Map<String, Object> buildTrace(@RequestParam String mid) {
        String resolvedMid = WeiboIdParser.parseMid(mid);
        if (resolvedMid == null) {
            return Map.of("success", false, "message", "无法识别的微博链接或ID");
        }
        log.info("📨 收到溯源请求，原始输入={}，解析后MID={}", mid, resolvedMid);
        traceService.asyncBuildTraceTree(resolvedMid);
        return Map.of("success", true, "mid", resolvedMid, "message", "溯源任务已启动");
    }

    @GetMapping("/tree/{mid}")
    public Map<String, Object> getTraceTree(@PathVariable String mid) {
        log.info("📨 查询溯源树，MID = {}", mid);
        return traceService.getTreeData(mid);
    }

    @PostMapping("/analyze-node")
    public String analyzeNode(@RequestParam String mid) {
        log.info("📨 AI 分析节点请求，MID = {}", mid);
        return traceService.analyzeKeyNode(mid);
    }

    @GetMapping("/status/{mid}")
    public Map<String, String> getTaskStatus(@PathVariable String mid) {
        TraceService.TaskStatus status = traceService.getTaskStatus(mid);
        return Map.of("status", status.name());
    }

}