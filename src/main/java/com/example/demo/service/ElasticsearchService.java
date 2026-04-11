package com.example.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ElasticsearchService {

    @Autowired
    private RestClient restClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${elasticsearch.analyzer}")
    private String analyzer;

    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "的", "了", "在", "是", "我", "你", "他", "它", "们", "这", "那", "就", "也", "不", "而", "及", "与", "回复", "转发"
    ));

    /**
     * 公开方法：对单个文本进行分词，返回词语列表
     */
    public List<String> analyzeSingleTextAsList(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        text = text.replaceAll("\\[[^\\]]+\\]", "").trim();
        if (text.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            Request request = new Request("GET", "/_analyze");
            Map<String, Object> body = new HashMap<>();
            body.put("analyzer", analyzer);
            body.put("text", text);
            request.setJsonEntity(objectMapper.writeValueAsString(body));

            Response response = restClient.performRequest(request);
            JsonNode root = objectMapper.readTree(response.getEntity().getContent());

            List<String> tokens = new ArrayList<>();
            JsonNode tokensNode = root.path("tokens");
            if (tokensNode.isArray()) {
                for (JsonNode tokenNode : tokensNode) {
                    String token = tokenNode.path("token").asText();
                    if (token.length() > 1 && !STOP_WORDS.contains(token)) {
                        tokens.add(token);
                    }
                }
            }
            return tokens;
        } catch (Exception e) {
            System.err.println("ES 分词失败，返回空列表: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 批量文本词频统计
     */
    public Map<String, Integer> analyzeTexts(List<String> texts) {
        Map<String, Integer> wordCounts = new HashMap<>();
        for (String text : texts) {
            List<String> tokens = analyzeSingleTextAsList(text);
            for (String token : tokens) {
                wordCounts.put(token, wordCounts.getOrDefault(token, 0) + 1);
            }
        }
        return wordCounts;
    }

    /**
     * 生成词云数据
     */
    public List<Map<String, Object>> generateWordCloudData(Map<String, Integer> wordCounts, int limit) {
        return wordCounts.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(limit)
                .map(e -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", e.getKey());
                    map.put("value", e.getValue());
                    return map;
                })
                .collect(Collectors.toList());
    }

    @PostConstruct
    public void checkConnection() {
        try {
            Request request = new Request("GET", "/");
            Response response = restClient.performRequest(request);
            System.out.println("✅ Elasticsearch 连接成功，版本：" +
                    objectMapper.readTree(response.getEntity().getContent()).path("version").path("number").asText());
        } catch (Exception e) {
            System.err.println("❌ Elasticsearch 连接失败: " + e.getMessage());
        }
    }

}