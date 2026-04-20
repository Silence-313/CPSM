package com.example.demo.controller;

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
    public String buildTrace(@RequestParam String mid) {
        log.info("📨 收到溯源请求，MID = {}", mid);
        traceService.asyncBuildTraceTree(mid);
        return "溯源任务已启动，请稍后查询";
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