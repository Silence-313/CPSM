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
            String rawContent = root.path("choices").get(0).path("message").path("content").asText();
            return cleanContent(rawContent);   // <-- 关键：清理多余换行
        } catch (Exception e) {
            return String.format("[%s] 调用失败: %s", model.getName(), e.getMessage());
        }
    }

    /**
     * 清理模型返回内容，去除多余连续换行
     */
    private String cleanContent(String raw) {
        if (raw == null) return "";
        // 统一所有换行符为 \n
        String unified = raw.replaceAll("\\r\\n|\\r", "\n");
        // 将连续2个以上的换行符压缩为最多2个（保留一个空行）
        String compressed = unified.replaceAll("\\n{2,}", "\n\n");
        // 去除每行首尾空格，并去除整个字符串首尾空白
        String[] lines = compressed.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line.trim()).append("\n");
        }
        return sb.toString().trim();
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
        return "### 身份定位\n" +
                "你是一位拥有15年经验的微博资深舆情分析专家，擅长从微小信号中捕捉群体心理趋势。\n\n" +
                "### 模拟事件\n" +
                "事件内容：[" + event + "]\n\n" +
                "### 预演任务\n" +
                "请针对未来48小时，进行一次深度沙盘推演：\n" +
                "1. **粉丝反应（心智博弈）**：分析核心粉丝、黑粉、路人三方的博弈点，是否会出现反转或洗白？\n" +
                "2. **商业价值（损益评估）**：预测代言合作方的可能动作（观望/切割/背书）及其品牌连锁反应。\n" +
                "3. **法律/监管风险**：研判是否触及平台红线或法律法规，是否会引来官媒定调。\n\n" +
                "### 输出要求\n" +
                "- **逻辑推导**：请使用‘因为...所以...可能导致...’的链式表达。\n" +
                "- **字数限制**：300字以内，语言风格需专业、冷峻、穿透本质。\n" +
                "- **排版**：段落间最多保留一个空行，严禁出现[br]等HTML标签。";
    }

    private String buildJudgePrompt(String event, String predictions) {
        int expertCount = config.getModels().size();
        return "### 首席舆情合伙人指令\n" +
                "当前针对事件「" + event + "」，你收到了来自" + expertCount + "个不同视角的专家预演报告：\n" +
                "---报告汇总---\n" +
                predictions + "\n" +
                "--------------\n\n" +
                "### 裁决任务\n" +
                "请你履行合伙人职责，完成以下深度复盘：\n" +
                "1. **异同分析**：快速提炼专家们的共识点，并尖锐地指出他们之间存在的推论冲突。\n" +
                "2. **逻辑定音**：基于微博生态特性（如热搜算法、情绪传染性），判断哪一种演变逻辑最接近真实，并说明理由。\n" +
                "3. **风险定级**：从【高/中/低】中选其一。注意：若涉及社会底线或官媒关注，必须定为“高”。\n" +
                "4. **应对策略（Action Plan）**：给出三条极具操作性的建议（例如：冷处理、引导话题、法务介入的时间点）。\n\n" +
                "### 强制格式（必须遵守）\n" +
                "**风险定级**：[在此处仅填写 高/中/低]\n" +
                "**逻辑裁决**：[简述理由]\n" +
                "**应对建议**：[1. / 2. / 3.]\n" +
                "注意：紧凑排版，段落间空行控制在1个以内。";
    }

    private String extractRiskLevel(String verdict) {
        String lower = verdict.toLowerCase();
        if (lower.contains("高风险") || lower.contains("风险：高") || lower.contains("风险定级：高")) {
            return "HIGH";
        }
        if (lower.contains("中高风险") || lower.contains("中风险") ||
                lower.contains("风险：中") || lower.contains("风险定级：中")) {
            return "MEDIUM";
        }
        if (lower.contains("低风险") || lower.contains("风险：低") || lower.contains("风险定级：低")) {
            return "LOW";
        }
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