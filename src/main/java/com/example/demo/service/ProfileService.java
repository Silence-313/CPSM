package com.example.demo.service;

import java.util.Map;

public interface ProfileService {
    Map<String, Object> getStats(Long userId);
    Map<String, Object> getModuleUsage(Long userId);
    Map<String, Object> getHeatmapData(Long userId);
    Map<String, Object> getActivityFeed(Long userId, int limit);
    Map<String, Object> updateUsername(Long userId, String newUsername);
    Map<String, Object> updateAvatar(Long userId, String avatar);
    Map<String, Object> updateApiKey(Long userId, String apiKey);
    Map<String, Object> getApiKey(Long userId);
}
