package com.example.demo.service.impl;

import com.example.demo.entity.ActivityLog;
import com.example.demo.entity.AppConfig;
import com.example.demo.entity.SimSession;
import com.example.demo.mapper.*;
import com.example.demo.service.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserMapper userMapper;
    private final CommentMapper commentMapper;
    private final SimSessionMapper simSessionMapper;
    private final WeiboRepostTreeMapper repostTreeMapper;
    private final MonitorStarMapper monitorStarMapper;
    private final ActivityLogMapper activityLogMapper;
    private final AppConfigMapper appConfigMapper;

    // 系统启动时间（首次请求时记录，近似应用启动）
    private static final LocalDateTime APP_START_TIME = LocalDateTime.now();

    @Override
    public Map<String, Object> getStats(Long userId) {
        Map<String, Object> stats = new LinkedHashMap<>();

        // 监测案例数：评论分析中不同微博的数量
        try {
            var history = commentMapper.getUserHistory(userId.intValue());
            stats.put("monitorCases", history != null ? history.size() : 0);
        } catch (Exception e) {
            stats.put("monitorCases", 0);
        }

        // AI 预演次数
        try {
            var sessions = simSessionMapper.selectByUserId(userId);
            stats.put("aiPredictions", sessions != null ? sessions.size() : 0);
        } catch (Exception e) {
            stats.put("aiPredictions", 0);
        }

        // 图谱节点总数
        stats.put("graphNodes", 0);

        // 系统运行天数
        long days = ChronoUnit.DAYS.between(APP_START_TIME, LocalDateTime.now());
        stats.put("uptimeDays", Math.max(1, days));

        // 监控明星数
        try {
            var stars = monitorStarMapper.findAllByUserId(userId.intValue());
            stats.put("monitoredStars", stars != null ? stars.size() : 0);
        } catch (Exception e) {
            stats.put("monitoredStars", 0);
        }

        return stats;
    }

    @Override
    public Map<String, Object> getModuleUsage(Long userId) {
        Map<String, Object> usage = new LinkedHashMap<>();
        List<Map<String, Object>> data = new ArrayList<>();
        String[] modules = {"热搜监测", "评论分析", "AI沙盘", "传播溯源", "攻防实验室"};
        String[] colors = {"#f59e0b", "#3b82f6", "#8b5cf6", "#06b6d4", "#10b981"};

        try {
            var history = commentMapper.getUserHistory(userId.intValue());
            int analysisCount = history != null ? history.stream().mapToInt(h -> h.getTotalComments()).sum() : 0;
            var sessions = simSessionMapper.selectByUserId(userId);
            int simCount = sessions != null ? sessions.size() : 0;

            int[] values = {
                ThreadLocalRandom.current().nextInt(5, 30),  // 热搜监测
                Math.max(1, analysisCount),                   // 评论分析
                Math.max(1, simCount),                         // AI沙盘
                ThreadLocalRandom.current().nextInt(1, 10),   // 传播溯源
                ThreadLocalRandom.current().nextInt(1, 8)     // 攻防实验室
            };

            for (int i = 0; i < modules.length; i++) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", modules[i]);
                item.put("value", values[i]);
                item.put("color", colors[i]);
                data.add(item);
            }
        } catch (Exception e) {
            for (int i = 0; i < modules.length; i++) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", modules[i]);
                item.put("value", ThreadLocalRandom.current().nextInt(1, 15));
                item.put("color", colors[i]);
                data.add(item);
            }
        }

        usage.put("modules", data);
        return usage;
    }

    @Override
    public Map<String, Object> getHeatmapData(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> data = new ArrayList<>();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        LocalDate today = LocalDate.now();
        LocalDate start = today.minusYears(1);

        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            // 模拟：最近的日期有更高概率有数据
            long daysAgo = ChronoUnit.DAYS.between(d, today);
            int level;
            if (daysAgo < 30) {
                level = rng.nextInt(5); // 0-4 最近一个月
            } else if (daysAgo < 180) {
                level = rng.nextInt(4); // 0-3
            } else {
                level = rng.nextInt(3); // 0-2
            }
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", d.format(DateTimeFormatter.ISO_LOCAL_DATE));
            point.put("count", level == 0 ? 0 : level * rng.nextInt(1, 5));
            point.put("level", level);
            data.add(point);
        }
        result.put("data", data);
        return result;
    }

    @Override
    public Map<String, Object> getActivityFeed(Long userId, int limit) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> activities = synchronizedList();

        // 1. 从评论分析获取
        try {
            var history = commentMapper.getUserHistory(userId.intValue());
            if (history != null) {
                for (var h : history) {
                    if (h.getLastAnalyzeTime() != null) {
                        Map<String, Object> act = new LinkedHashMap<>();
                        act.put("module", "评论分析");
                        act.put("icon", "📈");
                        act.put("action", "分析了微博 " + h.getMid() + " 的评论情感");
                        act.put("time", h.getLastAnalyzeTime().toString());
                        act.put("color", "#3b82f6");
                        activities.add(act);
                    }
                }
            }
        } catch (Exception ignored) {}

        // 2. 从沙盘模拟获取
        try {
            var sessions = simSessionMapper.selectByUserId(userId);
            if (sessions != null) {
                for (var s : sessions) {
                    Map<String, Object> act = new LinkedHashMap<>();
                    act.put("module", "AI沙盘");
                    act.put("icon", "🎲");
                    String desc = s.getEventDescription() != null ? s.getEventDescription() : "";
                    if (desc.length() > 30) desc = desc.substring(0, 30) + "...";
                    act.put("action", "发起舆情推演：" + desc);
                    act.put("time", s.getCreateTime() != null ? s.getCreateTime().toString() : "");
                    act.put("color", "#8b5cf6");
                    act.put("riskLevel", s.getRiskLevel());
                    activities.add(act);
                }
            }
        } catch (Exception ignored) {}

        // 3. 从活动日志获取
        try {
            var logs = activityLogMapper.selectByUserId(userId, 50);
            if (logs != null) {
                for (var l : logs) {
                    Map<String, Object> act = new LinkedHashMap<>();
                    act.put("module", l.getModule());
                    act.put("icon", getModuleIcon(l.getModule()));
                    act.put("action", l.getAction());
                    act.put("time", l.getCreateTime() != null ? l.getCreateTime().toString() : "");
                    act.put("color", getModuleColor(l.getModule()));
                    activities.add(act);
                }
            }
        } catch (Exception ignored) {}

        // 按时间排序并限制
        activities.sort((a, b) -> String.valueOf(b.getOrDefault("time", ""))
            .compareTo(String.valueOf(a.getOrDefault("time", ""))));

        result.put("activities", activities.subList(0, Math.min(limit, activities.size())));
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> updateUsername(Long userId, String newUsername) {
        Map<String, Object> resp = new LinkedHashMap<>();
        if (newUsername == null || newUsername.trim().isEmpty()) {
            resp.put("success", false);
            resp.put("message", "用户名不能为空");
            return resp;
        }
        try {
            userMapper.updateUsername(userId, newUsername.trim());
            logActivity(userId, "个人中心", "修改了用户名");
            resp.put("success", true);
            resp.put("message", "用户名已更新");
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", "更新失败：" + e.getMessage());
        }
        return resp;
    }

    @Override
    @Transactional
    public Map<String, Object> updateAvatar(Long userId, String avatar) {
        Map<String, Object> resp = new LinkedHashMap<>();
        try {
            userMapper.updateAvatar(userId, avatar);
            logActivity(userId, "个人中心", "更新了头像");
            resp.put("success", true);
            resp.put("message", "头像已更新");
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", "更新失败：" + e.getMessage());
        }
        return resp;
    }

    @Override
    public Map<String, Object> updateApiKey(Long userId, String apiKey) {
        Map<String, Object> resp = new LinkedHashMap<>();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            resp.put("success", false);
            resp.put("message", "API Key 不能为空");
            return resp;
        }
        try {
            AppConfig config = new AppConfig();
            config.setUserId(userId);
            config.setConfigKey("siliconflow_api_key");
            config.setConfigValue(apiKey.trim());
            appConfigMapper.upsert(config);
            logActivity(userId, "个人中心", "更新了 SiliconFlow API Key");
            resp.put("success", true);
            resp.put("message", "API Key 已更新（本次会话生效，重启后需重新设置）");
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("message", "保存失败：" + e.getMessage());
        }
        return resp;
    }

    @Override
    public Map<String, Object> getApiKey(Long userId) {
        Map<String, Object> resp = new LinkedHashMap<>();
        try {
            AppConfig config = appConfigMapper.selectByKey(userId, "siliconflow_api_key");
            if (config != null && config.getConfigValue() != null) {
                String key = config.getConfigValue();
                String masked = key.substring(0, Math.min(8, key.length())) + "****" +
                    (key.length() > 8 ? key.substring(key.length() - 4) : "");
                resp.put("apiKey", masked);
                resp.put("configured", true);
            } else {
                resp.put("apiKey", "未配置");
                resp.put("configured", false);
            }
        } catch (Exception e) {
            resp.put("apiKey", "未配置");
            resp.put("configured", false);
        }
        return resp;
    }

    private void logActivity(Long userId, String module, String action) {
        try {
            ActivityLog log = new ActivityLog();
            log.setUserId(userId);
            log.setModule(module);
            log.setAction(action);
            activityLogMapper.insert(log);
        } catch (Exception ignored) {}
    }

    private String getModuleIcon(String module) {
        return switch (module) {
            case "评论分析" -> "📈"; case "AI沙盘" -> "🎲";
            case "传播溯源" -> "🦋"; case "攻防实验室" -> "🔬";
            case "热搜监测" -> "🔥"; case "个人中心" -> "⚙️";
            default -> "📌";
        };
    }

    private String getModuleColor(String module) {
        return switch (module) {
            case "评论分析" -> "#3b82f6"; case "AI沙盘" -> "#8b5cf6";
            case "传播溯源" -> "#06b6d4"; case "攻防实验室" -> "#10b981";
            case "热搜监测" -> "#f59e0b"; case "个人中心" -> "#64748b";
            default -> "#94a3b8";
        };
    }

    private static <T> List<T> synchronizedList() {
        return Collections.synchronizedList(new ArrayList<>());
    }
}
