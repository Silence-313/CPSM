package com.example.demo.controller;

import com.example.demo.entity.SimResult;
import com.example.demo.entity.SimSession;
import com.example.demo.service.OpenAICompatibleService;
import com.example.demo.service.SimulationService;
import com.example.demo.service.StressTestService;
import com.example.demo.service.OpenAICompatibleService.ModelResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/simulation")
@RequiredArgsConstructor
public class SimulationController {

    private final OpenAICompatibleService aiService;
    private final SimulationService simulationService;
    private final StressTestService stressTestService;   // 用于异步沙盘推演与数据库操作

    // ========== 1. 快速并发测试接口（不存库） ==========
    @PostMapping("/predict-all")
    public Map<String, ModelResponse> predictAll(@RequestBody Map<String, String> payload) {
        String prompt = payload.get("prompt");
        Map<String, ModelResponse> results = new ConcurrentHashMap<>();

        // 并发调用六个模型
        CompletableFuture<Void> qwenFuture = CompletableFuture.runAsync(() ->
                results.put("千问", aiService.callQwen(prompt)));
        CompletableFuture<Void> deepseekFuture = CompletableFuture.runAsync(() ->
                results.put("DeepSeek", aiService.callDeepSeek(prompt)));
        CompletableFuture<Void> siliconflowFuture = CompletableFuture.runAsync(() ->
                results.put("SiliconFlow", aiService.callSiliconflow(prompt)));
        CompletableFuture<Void> doubaoFuture = CompletableFuture.runAsync(() ->
                results.put("豆包", aiService.callDoubao(prompt)));
        CompletableFuture<Void> hunyuanFuture = CompletableFuture.runAsync(() ->
                results.put("腾讯混元", aiService.callHunyuan(prompt)));
        CompletableFuture<Void> sparkFuture = CompletableFuture.runAsync(() ->
                results.put("Kimi", aiService.callKimi(prompt)));

        // 等待所有调用完成
        CompletableFuture.allOf(qwenFuture, deepseekFuture, siliconflowFuture,
                doubaoFuture, hunyuanFuture, sparkFuture).join();

        return results;
    }

    // ========== 1.5. 舆情压力测试（多智能体攻防模拟） ==========
    @PostMapping("/stress-test")
    public ResponseEntity<Map<String, Object>> stressTest(@RequestBody Map<String, String> payload) {
        String text = payload.get("text");
        if (text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请输入测试文本"));
        }
        Map<String, Object> result = stressTestService.runStressTest(text.trim());
        return ResponseEntity.ok(result);
    }

    // ========== 2. 启动沙盘模拟（异步，存库） ==========
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(@RequestBody Map<String, String> payload, HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "请先登录"));
        }
        String event = payload.get("event");
        if (event == null || event.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "事件描述不能为空"));
        }
        CompletableFuture<Long> future = simulationService.runSimulation(event, userId);
        Long sessionId = future.join();  // 异步方法内部已使用 @Async，此处 join 会等待最终完成（但不会阻塞主线程太久，因为内部是另起线程）
        return ResponseEntity.ok(Map.of("sessionId", sessionId));
    }

    // ========== 3. 轮询获取沙盘状态与结果 ==========
    @GetMapping("/status/{sessionId}")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable Long sessionId) {
        SimSession session = simulationService.getSession(sessionId);
        if (session == null) {
            return ResponseEntity.notFound().build();
        }
        List<SimResult> results = simulationService.getResults(sessionId);

        Map<String, Object> resp = new HashMap<>();
        resp.put("complete", session.getFinalVerdict() != null);
        // 修复重复键问题：如果 modelName 重复，保留先前的值或合并
        resp.put("predictions", results.stream()
                .collect(Collectors.toMap(
                        SimResult::getModelName,
                        SimResult::getRawOutput,
                        (existing, replacement) -> existing   // 重复键时保留已存在的值
                )));
        resp.put("verdict", session.getFinalVerdict());
        resp.put("riskLevel", session.getRiskLevel());
        return ResponseEntity.ok(resp);
    }

    // ========== 4. 获取用户历史记录 ==========
    @GetMapping("/history")
    public ResponseEntity<List<SimSession>> getHistory(HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        List<SimSession> history = simulationService.getUserHistory(userId);
        return ResponseEntity.ok(history);
    }

    // ========== 5. 删除用户历史记录 ==========
    @DeleteMapping("/history/{sessionId}")
    public ResponseEntity<?> deleteHistory(@PathVariable Long sessionId, HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).body("请先登录");
        boolean deleted = simulationService.deleteSession(sessionId, userId);
        return deleted ? ResponseEntity.ok("删除成功") : ResponseEntity.status(404).body("记录不存在");
    }

    // 清空全部历史
    @DeleteMapping("/history")
    public ResponseEntity<?> deleteAllHistory(HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).body("请先登录");
        simulationService.deleteAllSessions(userId);
        return ResponseEntity.ok("清空成功");
    }
}