package com.example.demo.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class StressTestService {

    private final OpenAICompatibleService aiService;

    private static final Map<String, String> ROLES = new LinkedHashMap<>();
    static {
        ROLES.put("激进黑粉",
            "你是一个激进的网络黑粉，擅长断章取义、道德绑架和逻辑诡辩。" +
            "请针对以下微博文本发表一段攻击性评论（60-100字），风格尖锐刻薄，专门寻找文字中的道德漏洞、事实矛盾或潜在歧义进行攻击。" +
            "以第一人称回复，不要使用「作为AI」等暴露身份的措辞。");

        ROLES.put("理智路人",
            "你是一个理智的普通网友，客观中立，关注事实本身，对公关话术免疫。" +
            "请针对以下微博文本发表一段客观评论（60-100字），从普通人视角分析这段话给人的感受。" +
            "以第一人称回复，不要使用「作为AI」等暴露身份的措辞。");

        ROLES.put("忠实粉丝",
            "你是发布该微博的博主忠实粉丝，无条件维护博主，但你的言论往往用力过猛，容易引发路人反感，俗称「招黑」。" +
            "请针对以下微博文本发表一段狂热维护的评论（60-100字），语气激动，充满崇拜，攻击任何可能的质疑者。" +
            "以第一人称回复，不要使用「作为AI」等暴露身份的措辞。");

        ROLES.put("公关专家",
            "你是一名资深公关危机管理专家，拥有15年品牌公关经验。" +
            "请从传播学角度分析以下微博文本：1）评估其在社交媒体上的潜在引爆风险；" +
            "2）指出文本中最容易被攻击的3个敏感点，用JSON数组格式标注：{\"keywords\": [\"词1\", \"词2\", \"词3\"]}；" +
            "3）给出3条简短的危机应对建议。输出控制在150字以内。");
    }

    private static final String JUDGE_PROMPT =
        "你是一位资深舆情分析师兼AI裁判。请根据以下4位不同立场网友对同一条微博的评论，" +
        "综合评估该微博的舆情风险。请严格按以下格式回复：\n\n" +
        "风险评分：[0-100的数字]\n" +
        "风险等级：[安全/低风险/中等风险/高风险/极高风险]\n" +
        "高风险关键词：[逗号分隔的3个关键词]\n" +
        "综合判断：[一段50字以内的综合研判]";

    public Map<String, Object> runStressTest(String userText) {
        long startTime = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, String> comments = new ConcurrentHashMap<>();
        List<String> errors = Collections.synchronizedList(new ArrayList<>());

        // 并行调用4个角色
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Map.Entry<String, String> role : ROLES.entrySet()) {
            String roleName = role.getKey();
            String systemPrompt = role.getValue();
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    var resp = aiService.callWithSystemPrompt(systemPrompt, userText);
                    if (resp.success && resp.content != null) {
                        comments.put(roleName, resp.content.trim());
                    } else {
                        comments.put(roleName, generateFallbackComment(roleName, userText));
                        errors.add(roleName + " 调用失败: " +
                            (resp.errorMsg != null ? resp.errorMsg : "unknown"));
                    }
                } catch (Exception e) {
                    comments.put(roleName, generateFallbackComment(roleName, userText));
                    errors.add(roleName + " 异常: " + e.getMessage());
                }
            });
            futures.add(future);
        }

        // 等待所有角色完成，最多60秒
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("压力测试超时或中断: {}", e.getMessage());
        }

        // 构建裁决官输入
        StringBuilder judgeInput = new StringBuilder();
        for (Map.Entry<String, String> entry : comments.entrySet()) {
            judgeInput.append("【").append(entry.getKey()).append("的评论】\n")
                .append(entry.getValue()).append("\n\n");
        }
        judgeInput.append("【原始微博文本】\n").append(userText);

        // 调用裁决官
        Map<String, Object> judgeResult = callJudge(judgeInput.toString());

        result.put("comments", comments);
        result.put("riskScore", judgeResult.getOrDefault("riskScore", 65));
        result.put("riskLevel", judgeResult.getOrDefault("riskLevel", "中等风险"));
        result.put("keywords", judgeResult.getOrDefault("keywords", List.of("措辞", "歧义", "争议")));
        result.put("verdict", judgeResult.getOrDefault("verdict", "该文本存在一定的舆情风险，建议重新审视措辞。"));
        result.put("expertAdvice", comments.getOrDefault("公关专家", "暂无专家建议"));
        result.put("costTime", System.currentTimeMillis() - startTime);
        if (!errors.isEmpty()) {
            result.put("warnings", errors);
        }
        return result;
    }

    private Map<String, Object> callJudge(String judgeInput) {
        Map<String, Object> result = new HashMap<>();
        result.put("riskScore", 65);
        result.put("riskLevel", "中等风险");
        result.put("keywords", List.of("措辞", "歧义", "争议"));
        result.put("verdict", "该文本存在一定的舆情风险，建议重新审视措辞。");

        try {
            var resp = aiService.callWithSystemPrompt(JUDGE_PROMPT, judgeInput);
            if (resp.success && resp.content != null) {
                String text = resp.content;
                result.putAll(parseJudgeResponse(text));
            }
        } catch (Exception e) {
            log.error("裁决官调用失败: {}", e.getMessage());
        }
        return result;
    }

    private Map<String, Object> parseJudgeResponse(String text) {
        Map<String, Object> parsed = new HashMap<>();

        // 解析风险评分
        Pattern scorePattern = Pattern.compile("风险评分[：:]\\s*(\\d+)");
        Matcher m = scorePattern.matcher(text);
        if (m.find()) {
            int score = Integer.parseInt(m.group(1));
            parsed.put("riskScore", Math.min(100, Math.max(0, score)));
        }

        // 解析风险等级
        Pattern levelPattern = Pattern.compile("风险等级[：:]\\s*(\\S+)");
        m = levelPattern.matcher(text);
        if (m.find()) {
            parsed.put("riskLevel", m.group(1).replaceAll("[，,。.]$", ""));
        }

        // 解析关键词
        Pattern kwPattern = Pattern.compile("高风险关键词[：:]\\s*(.+)");
        m = kwPattern.matcher(text);
        if (m.find()) {
            String[] kws = m.group(1).split("[,，、]");
            List<String> keywords = new ArrayList<>();
            for (String kw : kws) {
                String trimmed = kw.trim().replaceAll("[\\[\\]\"「」]", "");
                if (!trimmed.isEmpty()) {
                    keywords.add(trimmed);
                }
            }
            if (!keywords.isEmpty()) {
                parsed.put("keywords", keywords);
            }
        }

        // 解析综合判断
        Pattern verdictPattern = Pattern.compile("综合判断[：:]\\s*(.+)");
        m = verdictPattern.matcher(text);
        if (m.find()) {
            parsed.put("verdict", m.group(1).trim());
        } else {
            parsed.put("verdict", text.length() > 100 ? text.substring(0, 100) : text);
        }

        return parsed;
    }

    // 当AI调用失败时的本地降级回复
    private String generateFallbackComment(String role, String text) {
        String kw = keywordFrom(text);
        return switch (role) {
            case "激进黑粉" -> "这段话漏洞百出，" + kw + "这个词用得就很有问题，是不是在暗示什么？大家品，细品。";
            case "理智路人" -> "抛开情绪不谈，这段话的核心信息其实就一句话：用" + kw + "来博取关注。理性讨论，别动不动上纲上线。";
            case "忠实粉丝" -> "说得太好了！那些人就是嫉妒，见不得别人好。支持博主，永远站在你这边！黑子们退散！";
            case "公关专家" -> "从公关角度看，" + kw + "这个表述存在被误读的风险。建议：1）提前准备澄清口径；2）避免与网友正面对抗；3）在发酵前主动引导话题走向。";
            default -> "已收到。";
        };
    }

    private String keywordFrom(String text) {
        String[] words = text.replaceAll("[，。！？\\s]+", ",").split("[,，]");
        for (String w : words) {
            String trimmed = w.trim();
            if (trimmed.length() >= 2 && trimmed.length() <= 6) {
                return trimmed;
            }
        }
        return "敏感";
    }
}
