package com.example.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
public class WeiboOpenApiService {

    @Value("${weibo.open.access-token}")
    private String accessToken;

    @Value("${weibo.open.app-key}")
    private String appKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String API_BASE = "https://api.weibo.com/2";

    public WeiboOpenApiService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * 启动时验证 Access Token 是否有效
     */
    @PostConstruct
    public void validateTokenOnStartup() {
        try {
            JsonNode info = getTokenInfo();
            if (info != null && info.has("uid")) {
                log.info("✅ 微博API Access Token 有效: uid={}, appkey={}, expire_in={}s",
                        info.path("uid").asText(),
                        info.path("appkey").asText(),
                        info.path("expire_in").asText());
            } else {
                log.warn("⚠️ 微博API Access Token 无效，请重新获取。访问: https://open.weibo.com/apps/{}/privilege", appKey);
            }
        } catch (Exception e) {
            log.warn("⚠️ 无法验证微博API Access Token: {}", e.getMessage());
        }
    }

    /**
     * 验证 Access Token 是否有效
     */
    public JsonNode getTokenInfo() {
        String url = UriComponentsBuilder.fromHttpUrl("https://api.weibo.com/oauth2/get_token_info")
                .queryParam("access_token", accessToken)
                .toUriString();
        return postJson(url);
    }

    /**
     * 获取单条微博详情
     */
    public JsonNode getStatusById(String id) {
        String url = UriComponentsBuilder.fromHttpUrl(API_BASE + "/statuses/show.json")
                .queryParam("access_token", accessToken)
                .queryParam("id", id)
                .toUriString();
        return getJson(url);
    }

    /**
     * 获取转发列表（递归溯源用）
     * @param id    微博ID
     * @param count 每次返回条数，最大200
     * @param page  页码
     */
    public JsonNode getRepostList(String id, int count, int page) {
        String url = UriComponentsBuilder.fromHttpUrl(API_BASE + "/statuses/repost_timeline.json")
                .queryParam("access_token", accessToken)
                .queryParam("id", id)
                .queryParam("count", count)
                .queryParam("page", page)
                .toUriString();
        return getJson(url);
    }

    /**
     * 获取评论列表
     * @param id    微博ID
     * @param count 每次返回条数，最大200
     * @param page  页码
     */
    public JsonNode getComments(String id, int count, int page) {
        String url = UriComponentsBuilder.fromHttpUrl(API_BASE + "/comments/show.json")
                .queryParam("access_token", accessToken)
                .queryParam("id", id)
                .queryParam("count", count)
                .queryParam("page", page)
                .toUriString();
        return getJson(url);
    }

    /**
     * 获取热门话题（每小时更新，替代热搜爬虫）
     * 官方API: trends/hourly
     */
    public JsonNode getTrends() {
        String url = UriComponentsBuilder.fromHttpUrl(API_BASE + "/trends/hourly.json")
                .queryParam("access_token", accessToken)
                .toUriString();
        return getJson(url);
    }

    /**
     * GET 请求，返回解析后的 JsonNode
     */
    private JsonNode getJson(String url) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            String body = response.getBody();
            if (body == null || body.isEmpty()) {
                log.error("微博API返回空响应: endpoint={}, url={}", extractEndpoint(url), maskToken(url));
                return null;
            }
            return parseApiResponse(body, url);
        } catch (HttpClientErrorException e) {
            return handleHttpError(e.getResponseBodyAsString(), e.getStatusCode().value(), url);
        } catch (HttpServerErrorException e) {
            return handleHttpError(e.getResponseBodyAsString(), e.getStatusCode().value(), url);
        } catch (Exception e) {
            log.error("调用微博API网络异常: endpoint={}, url={}, error={}", extractEndpoint(url), maskToken(url), e.getMessage());
            return null;
        }
    }

    /** 从 URL 中提取接口路径，便于日志定位 */
    private String extractEndpoint(String url) {
        int idx = url.indexOf("/2/");
        if (idx == -1) return url;
        String path = url.substring(idx);
        int qIdx = path.indexOf('?');
        return qIdx > 0 ? path.substring(0, qIdx) : path;
    }

    /**
     * POST 请求（用于 oauth2/get_token_info）
     */
    private JsonNode postJson(String url) {
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, null, String.class);
            String body = response.getBody();
            if (body == null || body.isEmpty()) return null;
            return parseApiResponse(body, url);
        } catch (HttpClientErrorException e) {
            return handleHttpError(e.getResponseBodyAsString(), e.getStatusCode().value(), url);
        } catch (HttpServerErrorException e) {
            return handleHttpError(e.getResponseBodyAsString(), e.getStatusCode().value(), url);
        } catch (Exception e) {
            log.error("调用微博API网络异常(POST): error={}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析 API 响应并检测业务错误
     */
    private JsonNode parseApiResponse(String body, String url) {
        String endpoint = extractEndpoint(url);
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.has("error_code") || node.has("error")) {
                String errorCode = node.path("error_code").asText("");
                String error = node.path("error").asText("");
                String request = node.path("request").asText("");
                log.error("微博API业务错误: code={}, error={}, request={}, endpoint={}",
                        errorCode, error, request, endpoint);
                if ("21327".equals(errorCode) || "expired_token".equals(error)) {
                    log.warn("⚠️ Access Token 已过期，请到 open.weibo.com 重新申请");
                } else if ("10014".equals(errorCode)) {
                    log.warn("⚠️ 应用权限不足！请到 open.weibo.com 完成邮箱验证并申请接口权限");
                }
                return null;
            }
            return node;
        } catch (Exception e) {
            log.error("微博API响应解析失败: endpoint={}, error={}", endpoint, e.getMessage());
            return null;
        }
    }

    /**
     * 处理 HTTP 4xx/5xx 错误，尝试从响应体提取微博错误信息
     */
    private JsonNode handleHttpError(String errorBody, int httpStatus, String url) {
        String endpoint = extractEndpoint(url);
        if (errorBody != null) {
            try {
                JsonNode errorNode = objectMapper.readTree(errorBody);
                String errorCode = errorNode.path("error_code").asText("");
                String error = errorNode.path("error").asText("");
                if (!errorCode.isEmpty() || !error.isEmpty()) {
                    log.error("微博API错误: HTTP {}, code={}, error={}, endpoint={}",
                            httpStatus, errorCode, error, endpoint);
                    if ("21327".equals(errorCode) || "expired_token".equals(error)) {
                        log.warn("⚠️ Access Token 已过期，请到 open.weibo.com 重新申请");
                    } else if ("10014".equals(errorCode)) {
                        log.warn("⚠️ 应用权限不足！请到 open.weibo.com 完成邮箱验证并申请接口权限");
                    }
                    return null;
                }
            } catch (Exception ignored) {}
        }
        log.error("微博API HTTP错误: status={}, endpoint={}, url={}", httpStatus, endpoint, maskToken(url));
        return null;
    }

    private String maskToken(String url) {
        return url.replaceAll("access_token=[^&]+", "access_token=***");
    }
}
