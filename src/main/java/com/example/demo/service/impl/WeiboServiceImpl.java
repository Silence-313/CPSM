package com.example.demo.service.impl;

import com.alibaba.fastjson2.JSON;
import com.example.demo.dto.HotSearchItem;
import com.example.demo.entity.MonitorStar;
import com.example.demo.repository.MonitorStarMapper;
import com.example.demo.service.WeiboService;
import com.example.demo.utils.HttpUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class WeiboServiceImpl implements WeiboService {

    @Autowired
    private HttpUtils httpUtils;

    @Autowired
    private MonitorStarMapper starMapper;

    // 核心看板数据（包含热搜分析）
    @Override
    public List<MonitorStar> getMonitorDetail(Integer userId) {
        // 1. 获取基础名单（已包含去重聚合后的 keywords 列表）
        List<MonitorStar> stars = getAllStarsWithKeywords(userId);

        // 2. 获取当前热搜
        String rawJson = httpUtils.getWeiboHotSearchJson();
        if (rawJson == null) return stars;

        List<HotSearchItem> hotItems = JSON.parseObject(rawJson)
                .getJSONObject("data").getList("realtime", HotSearchItem.class);

        // 3. 统计逻辑：确保一条热搜只被计票一次
        for (MonitorStar star : stars) {
            // 关键：筛选出所有命中的热搜项（名字或关键词命中其一即可）
            List<HotSearchItem> matches = hotItems.stream()
                    .filter(item -> {
                        String title = item.getWord();
                        // 只要名字匹配 或者 关键词列表里的任何一个匹配
                        boolean nameMatch = star.getName() != null && title.contains(star.getName());
                        boolean kwMatch = star.getKeywords() != null &&
                                star.getKeywords().stream().anyMatch(title::contains);
                        return nameMatch || kwMatch;
                    })
                    .collect(Collectors.toList());

            // 4. 统一赋值，确保数据源唯一
            star.setOnBoardCount(matches.size()); // 命中几条
            star.setTotalHot(matches.stream().mapToLong(HotSearchItem::getNum).sum()); // 累加热度
        }
        return stars;
    }

    @Override
    public List<HotSearchItem> getAnalyzedHotSearch(Integer userId) {
        // 1. 健壮获取名人列表
        List<MonitorStar> stars = new ArrayList<>();
        if (userId != null) {
            stars = getAllStarsWithKeywords(userId);
        }

        // 2. 获取微博原始 JSON
        String rawJson = httpUtils.getWeiboHotSearchJson();
        if (rawJson == null) return new ArrayList<>();

        // 3. 解析并标记
        List<HotSearchItem> hotItems = JSON.parseObject(rawJson)
                .getJSONObject("data").getList("realtime", HotSearchItem.class);

        List<MonitorStar> finalStars = stars;
        return hotItems.stream().peek(item -> {
            // 如果没有添加任何名人，则 highlighted 全部为 false，但不影响显示榜单
            boolean isMatch = finalStars.stream()
                    .flatMap(s -> s.getKeywords() != null ? s.getKeywords().stream() : java.util.stream.Stream.empty())
                    .anyMatch(kw -> item.getWord().contains(kw));
            item.setHighlighted(isMatch);
        }).collect(Collectors.toList());
    }

    // 解决 Mapper 多行数据合并的逻辑
    private List<MonitorStar> getAllStarsWithKeywords(Integer userId) {
        // 1. 从数据库拿到带重复名人的原始列表 (JOIN 产生的结果)
        List<MonitorStar> rawList = starMapper.findAllWithKeywords(userId);

        // 2. 核心：按 ID 进行分组聚合
        Map<Integer, List<MonitorStar>> grouped = rawList.stream()
                .collect(Collectors.groupingBy(MonitorStar::getId));

        return grouped.values().stream().map(list -> {
            MonitorStar base = list.get(0); // 取出其中一个作为主体
            // 把同一个 ID 下所有的 keyword 收集成一个 List<String>
            List<String> kws = list.stream()
                    .map(MonitorStar::getKeyword)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());
            base.setKeywords(kws); // 填入你实体类中的 keywords 字段
            return base;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void addStarWithUser(String name, String color, Integer userId) {
        MonitorStar star = new MonitorStar();
        star.setName(name);
        star.setColor(color);
        star.setUserId(userId); // 确保 MonitorStar 实体类里有这个字段

        starMapper.insertStar(star);
        if (star.getId() != null) {
            starMapper.insertKeyword(star.getId(), name);
        }
    }

    @Override
    @Transactional
    public void addKeyword(Integer starId, String keyword) {
        // 插入颗粒度关键词到 monitor_keywords 表
        starMapper.insertKeyword(starId, keyword);
    }

    @Override
    @Transactional
    public void removeKeyword(Integer starId, String keyword) {
        // 从 monitor_keywords 表删除指定关键词
        starMapper.deleteKeyword(starId, keyword);
    }

    @Override
    @Transactional
    public void updateColor(Integer id, String color) {
        // 确保调用 mapper 更新数据库
        starMapper.updateStarColor(id, color);
    }

    @Override
    public List<MonitorStar> getMonitorDetailByUser(Integer userId) {
        // 这里需要调用 Mapper 中带 userId 过滤的查询方法
        return starMapper.findAllByUserId(userId);
    }
}