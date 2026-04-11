package com.example.demo.service;

import com.example.demo.config.SimulationModelConfig;
import com.example.demo.entity.SimResult;
import com.example.demo.entity.SimSession;
import com.example.demo.mapper.SimResultMapper;
import com.example.demo.mapper.SimSessionMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationService {
    private final SimulationModelConfig config;
    private final SimSessionMapper sessionMapper;
    private final SimResultMapper resultMapper;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 异步启动沙盘模拟
     */
    @Async
    public CompletableFuture<Long> runSimulation(String event, Integer userId) {
        System.out.println("预测模型数量：" + config.getModels().size());
        // 1. 创建会话
        SimSession session = new SimSession();
        session.setUserId(userId.longValue());
        session.setEventDescription(event);
        sessionMapper.insert(session);

        // 2. 并发调用所有预测模型
        List<CompletableFuture<String>> futures = config.getModels().stream()
                .map(model -> CompletableFuture.supplyAsync(() -> callModel(model, buildForecasterPrompt(event))))
                .toList();

        List<String> predictions = futures.stream().map(CompletableFuture::join).toList();

        // 3. 保存各模型结果（无论裁决是否成功，都先保存）
        for (int i = 0; i < predictions.size(); i++) {
            SimResult result = new SimResult();
            result.setSessionId(session.getId());
            result.setModelName(config.getModels().get(i).getName());
            result.setRawOutput(predictions.get(i));
            resultMapper.insert(result);
        }

        // 4. 尝试调用裁决者，失败时给予默认提示
        String verdict;
        try {
            String combinedPredictions = IntStream.range(0, predictions.size())
                    .mapToObj(i -> String.format("【%s】: %s", config.getModels().get(i).getName(), predictions.get(i)))
                    .collect(Collectors.joining("\n\n"));
            verdict = callJudge(buildJudgePrompt(event, combinedPredictions));
        } catch (Exception e) {
            log.error("裁决者调用失败，使用默认提示", e);
            verdict = "裁决模型暂时不可用，请稍后重试。\n风险定级：UNKNOWN\n应对策略：请结合各模型预测人工判断。";
        }

        // 5. 更新最终裁决
        session.setFinalVerdict(verdict);
        session.setRiskLevel(extractRiskLevel(verdict));
        sessionMapper.update(session);

        return CompletableFuture.completedFuture(session.getId());
    }

    private String callModel(SimulationModelConfig.ModelConfig model, String prompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (model.getApiKey() != null && !model.getApiKey().isEmpty()) {
                headers.set("Authorization", "Bearer " + model.getApiKey());
            }
            Map<String, Object> body = new HashMap<>();
            body.put("model", model.getModel());
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            if ("Kimi".equals(model.getName())) {
                body.put("temperature", 1.0);
            } else {
                body.put("temperature", 0.7);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            // 🔥 确保 URL 正确拼接 /chat/completions
            String url = model.getUrl();
            if (!url.endsWith("/chat/completions")) {
                url = url + "/chat/completions";
            }
            ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
            JsonNode root = objectMapper.readTree(resp.getBody());
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            return String.format("[%s] 调用失败: %s", model.getName(), e.getMessage());
        }
    }

    private String callJudge(String prompt) {
        SimulationModelConfig.JudgeConfig judge = config.getJudge();
        SimulationModelConfig.ModelConfig model = new SimulationModelConfig.ModelConfig();
        model.setName(judge.getName());
        model.setUrl(judge.getUrl());
        model.setApiKey(judge.getApiKey());
        model.setModel(judge.getModel());
        return callModel(model, prompt);
    }

    private String buildForecasterPrompt(String event) {
        return "你是一名资深舆情分析专家。现有一个模拟事件：" + event +
                "。请从'粉丝反应'、'商业价值影响'、'法律风险'三个维度预演未来48小时的走势，字数不超过300字。";
    }

    private String buildJudgePrompt(String event, String predictions) {
        return "你现在是首席舆情合伙人。针对事件：「" + event + "」，以下三位专家给出了预演报告：\n" +
                predictions + "\n请对比它们的异同，指出潜在最可能发生的逻辑，并给出一个最终的'风险定级'（高/中/低）和'应对策略建议'。";
    }

    private String extractRiskLevel(String verdict) {
        if (verdict.contains("高风险") || verdict.contains("风险：高")) return "HIGH";
        if (verdict.contains("中风险") || verdict.contains("风险：中")) return "MEDIUM";
        if (verdict.contains("低风险") || verdict.contains("风险：低")) return "LOW";
        return "UNKNOWN";
    }

    public SimSession getSession(Long sessionId) {
        return sessionMapper.selectById(sessionId);
    }

    public List<SimResult> getResults(Long sessionId) {
        return resultMapper.selectBySessionId(sessionId);
    }

    public List<SimSession> getUserHistory(Integer userId) {
        return sessionMapper.selectByUserId(userId.longValue());
    }

    public boolean deleteSession(Long sessionId, Integer userId) {
        return sessionMapper.deleteByIdAndUser(sessionId, userId.longValue()) > 0;
    }

    public void deleteAllSessions(Integer userId) {
        sessionMapper.deleteAllByUser(userId.longValue());
    }

}