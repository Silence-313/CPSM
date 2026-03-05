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
import java.util.stream.Collectors;

@Service
public class WeiboServiceImpl implements WeiboService {

    @Autowired
    private HttpUtils httpUtils;

    @Autowired
    private MonitorStarMapper starMapper;

    // 核心看板数据（包含热搜分析）
    @Override
    public List<MonitorStar> getMonitorDetail() {
        // 1. 获取基础名单和关键词
        List<MonitorStar> stars = getAllStarsWithKeywords();

        // 2. 获取当前热搜进行比对统计
        String rawJson = httpUtils.getWeiboHotSearchJson();
        List<HotSearchItem> hotItems = JSON.parseObject(rawJson)
                .getJSONObject("data").getList("realtime", HotSearchItem.class);

        // 3. 颗粒度统计逻辑
        for (MonitorStar star : stars) {
            List<HotSearchItem> matches = hotItems.stream()
                    .filter(item -> star.getKeywords().stream().anyMatch(kw -> item.getWord().contains(kw)))
                    .collect(Collectors.toList());

            star.setOnBoardCount(matches.size());
            star.setTotalHot(matches.stream().mapToLong(HotSearchItem::getNum).sum());
        }
        return stars;
    }

    @Override
    public List<HotSearchItem> getAnalyzedHotSearch() {
        List<MonitorStar> stars = getAllStarsWithKeywords();
        String rawJson = httpUtils.getWeiboHotSearchJson();
        List<HotSearchItem> hotItems = JSON.parseObject(rawJson)
                .getJSONObject("data").getList("realtime", HotSearchItem.class);

        // 标记热搜榜单的高亮项
        return hotItems.stream().peek(item -> {
            boolean isMatch = stars.stream()
                    .flatMap(s -> s.getKeywords().stream())
                    .anyMatch(kw -> item.getWord().contains(kw));
            item.setHighlighted(isMatch);
        }).collect(Collectors.toList());
    }

    // 解决 Mapper 多行数据合并的逻辑
    private List<MonitorStar> getAllStarsWithKeywords() {
        // 假设 Mapper 使用了简单的 Join 查询，返回的是包含重复 star 信息但不同 keyword 的平铺对象
        List<MonitorStar> rawData = starMapper.findAllWithKeywords();

        // 使用 Java Stream 按 ID 分组并合并关键词
        Map<Integer, List<MonitorStar>> grouped = rawData.stream()
                .collect(Collectors.groupingBy(MonitorStar::getId));

        List<MonitorStar> result = new ArrayList<>();
        grouped.forEach((id, list) -> {
            MonitorStar base = list.get(0);
            // 提取所有关键词存入 base 对象的 keywords 列表中
            List<String> kws = list.stream()
                    .map(MonitorStar::getKeyword) // 假设 Entity 里有个临时接收单条词的字段 keyword
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
            base.setKeywords(kws);
            result.add(base);
        });
        return result;
    }

    @Override
    @Transactional // 建议开启事务，确保两张表操作的原子性
    public void addStar(String name, String color) {
        // 1. 构造主表对象
        MonitorStar star = new MonitorStar();
        star.setName(name);
        star.setColor(color);

        // 2. 执行插入主表
        starMapper.insertStar(star);
        System.out.println("Generated ID: " + star.getId()); // 如果这里输出 null，说明 @Options 没生效

        // 3. 此时因为有了 @Options，star.getId() 已经被自动赋值了
        if (star.getId() != null) {
            // 4. 将名字本身作为第一个关键词存入从表
            starMapper.insertKeyword(star.getId(), name);
        } else {
            throw new RuntimeException("数据库未返回生成的主键 ID，请检查 Mapper 配置");
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
}