package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.mapper.UserMapper;
import com.example.demo.service.ProfileService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final UserMapper userMapper;

    private Long getUserId(HttpSession session) {
        Integer uid = (Integer) session.getAttribute("userId");
        return uid != null ? uid.longValue() : null;
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getUserInfo(HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "请先登录"));
        User user = userMapper.selectById(userId);
        if (user == null) return ResponseEntity.status(404).body(Map.of("error", "用户不存在"));
        return ResponseEntity.ok(Map.of(
            "username", user.getUsername() != null ? user.getUsername() : "",
            "avatar", user.getAvatar() != null ? user.getAvatar() : "",
            "email", user.getEmail() != null ? user.getEmail() : "",
            "phone", user.getPhone() != null ? user.getPhone() : ""
        ));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "请先登录"));
        return ResponseEntity.ok(profileService.getStats(userId));
    }

    @GetMapping("/module-usage")
    public ResponseEntity<Map<String, Object>> getModuleUsage(HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "请先登录"));
        return ResponseEntity.ok(profileService.getModuleUsage(userId));
    }

    @GetMapping("/heatmap")
    public ResponseEntity<Map<String, Object>> getHeatmap(HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "请先登录"));
        return ResponseEntity.ok(profileService.getHeatmapData(userId));
    }

    @GetMapping("/activity")
    public ResponseEntity<Map<String, Object>> getActivity(
            HttpSession session,
            @RequestParam(defaultValue = "20") int limit) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "请先登录"));
        return ResponseEntity.ok(profileService.getActivityFeed(userId, limit));
    }

    @PutMapping("/username")
    public ResponseEntity<Map<String, Object>> updateUsername(
            @RequestBody Map<String, String> payload, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "请先登录"));
        Map<String, Object> result = profileService.updateUsername(userId, payload.get("username"));
        if (Boolean.TRUE.equals(result.get("success"))) {
            session.setAttribute("username", payload.get("username"));
        }
        return ResponseEntity.ok(result);
    }

    @PutMapping("/avatar")
    public ResponseEntity<Map<String, Object>> updateAvatar(
            @RequestBody Map<String, String> payload, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "请先登录"));
        return ResponseEntity.ok(profileService.updateAvatar(userId, payload.get("avatar")));
    }

    @GetMapping("/api-key")
    public ResponseEntity<Map<String, Object>> getApiKey(HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "请先登录"));
        return ResponseEntity.ok(profileService.getApiKey(userId));
    }

    @PutMapping("/api-key")
    public ResponseEntity<Map<String, Object>> updateApiKey(
            @RequestBody Map<String, String> payload, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "请先登录"));
        return ResponseEntity.ok(profileService.updateApiKey(userId, payload.get("apiKey")));
    }
}
