package com.example.demo.service;

import com.example.demo.dto.HotSearchItem;
import com.example.demo.entity.MonitorStar;
import java.util.List;

public interface WeiboService {
    List<HotSearchItem> getAnalyzedHotSearch(Integer userId);
    List<MonitorStar> getMonitorDetail(Integer userId);
    void addStarWithUser(String name, String color, Integer userId);
    void addKeyword(Integer starId, String keyword, Integer userId);
    void removeKeyword(Integer starId, String keyword, Integer userId);
    void updateColor(Integer id, String color, Integer userId);
    List<MonitorStar> getMonitorDetailByUser(Integer userId);
}