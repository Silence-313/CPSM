package com.example.demo.utils;

import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class HttpUtils {

    private final RestTemplate restTemplate = new RestTemplate();

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
}