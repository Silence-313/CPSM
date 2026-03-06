package com.example.demo.service;

import com.example.demo.dto.HotSearchItem;
import com.example.demo.entity.MonitorStar;
import java.util.List;

public interface WeiboService {

    List<HotSearchItem> getAnalyzedHotSearch(Integer userId);

    // 获取监测对象的详细看板数据（含统计结果）
    List<MonitorStar> getMonitorDetail(Integer userId);

    void addKeyword(Integer starId, String keyword);
    void removeKeyword(Integer starId, String keyword);
    void updateColor(Integer id, String color);

    void addStarWithUser(String name, String color, Integer userId);

    // 修复错误 3：添加支持 userId 的获取详情方法
    List<MonitorStar> getMonitorDetailByUser(Integer userId);
}