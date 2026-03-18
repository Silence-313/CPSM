package com.example.demo.utils;

import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;

@Component
public class HttpUtils {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 配置文件路径，根据你的项目结构调整
    private static final String CONFIG_PATH = "spider/weibo_config.json";

    public String getWeiboHotSearchJson() {
        String url = "https://weibo.com/ajax/side/hotSearch";

        HttpHeaders headers = new HttpHeaders();
        // 关键：模拟浏览器行为，否则会被微博拦截
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.set("Referer", "https://weibo.com/hot/search");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return response.getBody();
        } catch (Exception e) {
            return null; // 实际开发建议记录日志或抛出自定义异常
        }
    }

    private String[] getLatestCredentials() {
        File configFile = new File(CONFIG_PATH);
        if (configFile.exists()) {
            JsonNode node = objectMapper.readTree(configFile);
            String cookie = node.path("cookie").asText("");
            String xsrf = node.path("xsrf").asText("");
            return new String[]{cookie, xsrf};
        }
        return new String[]{"", ""};
    }

    public String getWeiboCommentsJson(String mid, String maxId) {
        String baseUrl = "https://www.weibo.com/ajax/statuses/buildComments";
        String url = String.format("%s?is_reload=1&id=%s&is_show_bulletin=2&count=20&fetch_level=0&locale=zh-CN", baseUrl, mid);

        if (maxId != null && !maxId.equals("0") && !maxId.isEmpty()) {
            url += "&max_id=" + maxId + "&flow=0";
        }

        String[] credentials = getLatestCredentials();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Cookie", credentials[0]);
        headers.set("x-xsrf-token", credentials[1]);
        headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.set("Referer", "https://weibo.com/u/" + mid);
        headers.set("Accept", "application/json, text/plain, */*");

        HttpEntity<String> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return response.getBody();
        } catch (Exception e) {
            System.err.println("网络请求失败: " + e.getMessage());
            return null;
        }
    }
}