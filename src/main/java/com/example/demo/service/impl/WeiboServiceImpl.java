package com.example.demo.service.impl;

import com.example.demo.dto.HotSearchItem;
import com.example.demo.entity.MonitorStar;
import com.example.demo.mapper.MonitorStarMapper;
import com.example.demo.service.WeiboOpenApiService;
import com.example.demo.service.WeiboService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class WeiboServiceImpl implements WeiboService {

    @Autowired
    private WeiboOpenApiService weiboOpenApiService;

    @Autowired
    private MonitorStarMapper starMapper;

    @Value("${weibo.python.path}")
    private String pythonPath;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 获取当前用户的监测看板（包含热搜命中统计）
     */
    @Override
    public List<MonitorStar> getMonitorDetail(Integer userId) {
        // 1. 获取该用户所有监测名人及其关键词
        List<MonitorStar> stars = getAllStarsWithKeywords(userId);
        if (stars.isEmpty()) {
            return stars;
        }

        // 2. 获取微博热搜榜单（Python爬虫）
        List<HotSearchItem> hotItems = fetchTrendsAsHotItems();
        if (hotItems.isEmpty()) {
            return stars;
        }

        // 3. 计算每个名人在热搜中的命中次数及热度总和
        for (MonitorStar star : stars) {
            List<HotSearchItem> matches = hotItems.stream()
                    .filter(item -> {
                        String title = item.getWord();
                        boolean nameMatch = star.getName() != null && title.contains(star.getName());
                        boolean kwMatch = star.getKeywords() != null &&
                                star.getKeywords().stream().anyMatch(title::contains);
                        return nameMatch || kwMatch;
                    })
                    .collect(Collectors.toList());

            star.setOnBoardCount(matches.size());
            star.setTotalHot(matches.stream().mapToLong(HotSearchItem::getNum).sum());
        }
        return stars;
    }

    /**
     * 获取分析后的热搜榜单（用于首页展示）
     */
    @Override
    public List<HotSearchItem> getAnalyzedHotSearch(Integer userId) {
        List<MonitorStar> stars = new ArrayList<>();
        if (userId != null) {
            stars = getAllStarsWithKeywords(userId);
        }

        List<HotSearchItem> hotItems = fetchTrendsAsHotItems();
        if (hotItems == null) {
            return new ArrayList<>();
        }

        List<MonitorStar> finalStars = stars;
        return hotItems.stream().peek(item -> {
            boolean isMatch = finalStars.stream()
                    .flatMap(s -> s.getKeywords() != null ? s.getKeywords().stream() : java.util.stream.Stream.empty())
                    .anyMatch(kw -> item.getWord().contains(kw));
            item.setHighlighted(isMatch);
        }).collect(Collectors.toList());
    }

    /**
     * 添加监测名人
     */
    @Override
    @Transactional
    public void addStarWithUser(String name, String color, Integer userId) {
        MonitorStar star = new MonitorStar();
        star.setName(name);
        star.setColor(color);
        star.setUserId(userId);
        starMapper.insertStar(star);

        // 自动将名人姓名作为默认关键词
        if (star.getId() != null) {
            starMapper.insertKeyword(star.getId(), name, userId);
        }
    }

    /**
     * 添加颗粒度关键词
     */
    @Override
    @Transactional
    public void addKeyword(Integer starId, String keyword, Integer userId) {
        starMapper.insertKeyword(starId, keyword, userId);
    }

    /**
     * 删除关键词
     */
    @Override
    @Transactional
    public void removeKeyword(Integer starId, String keyword, Integer userId) {
        starMapper.deleteKeyword(starId, keyword, userId);
    }

    /**
     * 更新名人颜色标记
     */
    @Override
    @Transactional
    public void updateColor(Integer id, String color, Integer userId) {
        starMapper.updateStarColor(id, color, userId);
    }

    /**
     * 获取指定用户的监测名人详情（纯数据库查询，不含热搜统计）
     */
    @Override
    public List<MonitorStar> getMonitorDetailByUser(Integer userId) {
        return getAllStarsWithKeywords(userId);
    }

    // ========== 私有辅助方法 ==========

    /**
     * 通过 Python 爬虫获取微博热搜榜单，转换为 HotSearchItem 列表
     * 调用 spider/hot_search_spider.py，使用 Cookie 认证而非官方 API
     */
    private List<HotSearchItem> fetchTrendsAsHotItems() {
        try {
            String spiderPath = System.getProperty("user.dir") + "/spider/hot_search_spider.py";
            ProcessBuilder pb = new ProcessBuilder(pythonPath, spiderPath);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            // 读取标准输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            // 读取错误输出
            StringBuilder errOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errOutput.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0 || output.length() == 0) {
                System.err.println("Python爬虫执行失败, exitCode=" + exitCode + ", stderr=" + errOutput);
                return new ArrayList<>();
            }

            JsonNode root = objectMapper.readTree(output.toString());
            JsonNode data = root.path("data");
            if (!data.isArray() || data.size() == 0) {
                if (root.has("error")) {
                    System.err.println("爬虫返回错误: " + root.path("error").asText());
                }
                return new ArrayList<>();
            }

            List<HotSearchItem> items = new ArrayList<>();
            for (int i = 0; i < data.size(); i++) {
                JsonNode t = data.get(i);
                String word = t.path("word").asText("").trim();
                if (word.isEmpty()) continue;

                long num = t.path("num").asLong(0);

                HotSearchItem item = new HotSearchItem();
                item.setWord(word);
                item.setNum(num);
                item.setRank(t.path("rank").asInt(i + 1));
                items.add(item);
            }
            return items;
        } catch (Exception e) {
            System.err.println("获取微博热搜失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 从数据库获取用户的所有名人及其关键词（聚合去重）
     */
    private List<MonitorStar> getAllStarsWithKeywords(Integer userId) {
        List<MonitorStar> rawList = starMapper.findAllWithKeywords(userId);
        if (rawList == null || rawList.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Integer, List<MonitorStar>> grouped = rawList.stream()
                .collect(Collectors.groupingBy(MonitorStar::getId));

        return grouped.values().stream().map(list -> {
            MonitorStar base = list.get(0);
            List<String> kws = list.stream()
                    .map(MonitorStar::getKeyword)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            base.setKeywords(kws);
            return base;
        }).collect(Collectors.toList());
    }
}