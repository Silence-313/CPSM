package com.example.demo.service;

import com.example.demo.config.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAICompatibleService {
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final QwenConfig qwenConfig;
    private final DeepSeekConfig deepSeekConfig;
    private final DoubaoConfig doubaoConfig;
    private final HunyuanConfig hunyuanConfig;
    private final SiliconflowConfig siliconflowConfig;
    private final KimiConfig kimiConfig;
    private final SimulationModelConfig simulationModelConfig;

    // 定义一个内部类来承载API响应
    public static class ModelResponse {
        public String modelName; public String content; public boolean success;
        public String errorMsg; public long costTime;
        public ModelResponse(String modelName, String content, boolean success, String errorMsg, long costTime) {
            this.modelName = modelName; this.content = content; this.success = success;
            this.errorMsg = errorMsg; this.costTime = costTime;
        }
    }

    public ModelResponse callOpenAICompatible(String prompt, String modelName,
                                              String baseUrl, String apiKey, String model) {
        long startTime = System.currentTimeMillis();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            // 针对 Kimi 模型，temperature 必须设为 1
            if ("Kimi".equals(modelName)) {
                body.put("temperature", 1.0);
            } else {
                body.put("temperature", 0.7);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            String url = baseUrl + "/chat/completions";
            ResponseEntity<String> resp = restTemplate.postForEntity(url, entity, String.class);
            JsonNode root = objectMapper.readTree(resp.getBody());
            String content = root.path("choices").get(0).path("message").path("content").asText();

            long costTime = System.currentTimeMillis() - startTime;
            log.info("✅ [{}] 调用成功，耗时 {}ms，返回字数 {}", modelName, costTime, content.length());
            return new ModelResponse(modelName, content, true, null, costTime);
        } catch (Exception e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("❌ [{}] 调用失败，耗时 {}ms，错误: {}", modelName, costTime, e.getMessage());
            return new ModelResponse(modelName, null, false, e.getMessage(), costTime);
        }
    }

    public ModelResponse callQwen(String prompt) {
        return callOpenAICompatible(prompt, "千问",
                qwenConfig.getBaseUrl(), qwenConfig.getApiKey(), qwenConfig.getModel());
    }

    public ModelResponse callDeepSeek(String prompt) {
        return callOpenAICompatible(prompt, "DeepSeek",
                deepSeekConfig.getBaseUrl(), deepSeekConfig.getApiKey(), deepSeekConfig.getModel());
    }

    public ModelResponse callSiliconflow(String prompt) {
        return callOpenAICompatible(prompt, "SiliconFlow",
                siliconflowConfig.getBaseUrl(), siliconflowConfig.getApiKey(), siliconflowConfig.getModel());
    }

    public ModelResponse callDoubao(String prompt) {
        return callOpenAICompatible(prompt, "豆包",
                doubaoConfig.getBaseUrl(), doubaoConfig.getApiKey(), doubaoConfig.getModel());
    }

    public ModelResponse callHunyuan(String prompt) {
        return callOpenAICompatible(prompt, "腾讯混元",
                hunyuanConfig.getBaseUrl(), hunyuanConfig.getApiKey(), hunyuanConfig.getModel());
    }

    public ModelResponse callKimi(String prompt) {
        return callOpenAICompatible(prompt, "Kimi",
                kimiConfig.getBaseUrl(), kimiConfig.getApiKey(), kimiConfig.getModel());
    }

    public ModelResponse callSimulationModel(String prompt) {
        return callOpenAICompatible(prompt, "Simulation",
                siliconflowConfig.getBaseUrl(), siliconflowConfig.getApiKey(), siliconflowConfig.getModel());
    }

}