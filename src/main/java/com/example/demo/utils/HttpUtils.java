package com.example.demo.utils;

import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;

@Component
public class HttpUtils {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String CONFIG_PATH = "spider/weibo_config.json";

    public String getWeiboCommentsJson(String mid, String maxId, int maxIdType) {
        String url = String.format(
                "https://weibo.com/ajax/statuses/buildComments?is_reload=1&id=%s&is_show_bulletin=2&count=20&fetch_level=0&locale=zh-CN&flow=0",
                mid
        );
        if (maxId != null && !"0".equals(maxId) && !maxId.isEmpty()) {
            url += "&max_id=" + maxId + "&max_id_type=" + maxIdType;
        }
        return executeRequest(url);
    }

    public String getChildCommentsJson(String rootId) {
        // 简化参数，只保留最核心的
        String url = "https://weibo.com/ajax/statuses/showComments"
                + "?id=" + rootId
                + "&flow=0"         // 增加这个参数，表示获取最新/热度回复
                + "&is_show_bulletin=2"; // 微博新版接口有时要求这个标志位

        return executeRequest(url);
    }

    public String executeRequest(String url) {
        // 1. 动态获取最新的 Cookie
        String[] credentials = getLatestCredentials();
        String cookie = credentials[0];

        System.out.println("DEBUG - 当前请求使用的Cookie: " + cookie);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Referer", "https://weibo.com/");
        headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.set("Accept", "application/json, text/plain, */*");
        headers.set("X-Requested-With", "XMLHttpRequest");

        // 如果配置文件中有 Cookie 则设置，否则可能被拦截
        if (cookie != null && !cookie.isEmpty()) {
            headers.set("Cookie", cookie);
        }

        HttpEntity<String> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String body = response.getBody();

            // 防御检查：如果返回的是 HTML，说明被拦截了
            if (body != null && body.trim().startsWith("<")) {
                System.err.println("⚠️ 警告：微博接口返回了 HTML 页面（可能是被拦截或 Cookie 过期）");
                return null;
            }
            return body;
        } catch (Exception e) {
            System.err.println("❌ 网络请求异常 [" + url + "]: " + e.getMessage());
            return null;
        }
    }

    private String[] getLatestCredentials() {
        try {
            File configFile = new File(CONFIG_PATH);
            if (configFile.exists()) {
                JsonNode node = objectMapper.readTree(configFile);
                return new String[]{node.path("cookie").asText(), node.path("xsrf").asText()};
            }
        } catch (Exception e) {
            System.err.println("读取配置文件失败: " + e.getMessage());
        }
        return new String[]{"", ""};
    }

    // 在 HttpUtils.java 中确保有这个方法
    public String getWeiboHotSearchJson() {
        String url = "https://weibo.com/ajax/side/hotSearch";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Referer", "https://weibo.com/");
        headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        HttpEntity<String> entity = new HttpEntity<>(headers);
        try {
            // 使用 restTemplate 发送请求
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return response.getBody();
        } catch (Exception e) {
            System.err.println("获取热搜失败: " + e.getMessage());
            return null;
        }
    }
}