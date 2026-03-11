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

    public String getWeiboCommentsJson(String mid, String maxId) {
        // 使用你提供的标准 URL 模板
        String baseUrl = "https://www.weibo.com/ajax/statuses/buildComments";
        // 动态拼接参数：如果有 maxId 就带上，没有就不带（第一页）
        String url = String.format(
                "%s?is_reload=1&id=%s&is_show_bulletin=2&count=20&fetch_level=0&locale=zh-CN",
                baseUrl,
                mid
        );

        if (maxId != null && !maxId.equals("0")) {
            url += "&max_id=" + maxId + "&flow=0";
        }

        HttpHeaders headers = new HttpHeaders();
        // 1. 填入你提供的核心 Token 和 Cookie
        headers.set("x-xsrf-token", "g5fBEoQGvuWcmkWQbsrF3rJT");
        headers.set("Cookie", "SCF=AvXRiQzHBADDS5MCosgG6zrQCyarcvvLj_0SKjjLFNIPtm7LioEAYAlmnVPJQHCg7SivDWUdZjpxZ5y5GfbhBaE.; SUB=_2A25EtIHiDeRhGeFJ6FUW9y_Ewz2IHXVny5sqrDV8PUNbmtANLVmmkW9NfBtk9ztn9ut8NoO0qGS6sivIVc6URmYi; SUBP=0033WrSXqPxfM725Ws9jqgMF55529P9D9WWCOYiHokhZmzA9fKGzzh2A5NHD95QNS0eNS0Mp1hnpWs4DqcjT-cLj9g4o9K50eh2t; ALF=02_1775795890; _s_tentry=weibo.com; Apache=2747410972223.835.1773203948591; SINAGLOBAL=2747410972223.835.1773203948591; ULV=1773203948591:1:1:1:2747410972223.835.1773203948591:; XSRF-TOKEN=g5fBEoQGvuWcmkWQbsrF3rJT; WBPSESS=yWdvjYF_DjaVy4WDFiHlk61QORcfEHefo3ExNllK204bTJ95eP1wXlcns6Ty6HaGxrcc5owCSe7MvtGwlnEBywSjq3OeouqwLCyrtuBl58ccnBqeEJXJ_wM8qxtXRQDszP4Gz5qZ3st1b551x9fgDw==");

        // 2. 填入你提供的 User-Agent 和 Referer
        headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36 Edg/145.0.0.0");
        headers.set("Referer", "https://www.weibo.com/7437373287/PfiPzmHGx");

        headers.set("Accept", "application/json, text/plain, */*");
        headers.set("client-version", "v2.44.64"); // 微博前端版本号，建议带上

        HttpEntity<String> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return response.getBody();
        } catch (Exception e) {
            System.err.println("爬取异常: " + e.getMessage());
            return null;
        }
    }
}