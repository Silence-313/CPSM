package com.example.demo.service;

import com.example.demo.dto.HotSearchItem;
import com.example.demo.entity.MonitorStar;
import java.util.List;

public interface WeiboService {

    List<HotSearchItem> getAnalyzedHotSearch();

    // 获取监测对象的详细看板数据（含统计结果）
    List<MonitorStar> getMonitorDetail();

    // 修改：确保接口中方法签名包含 color
    void addStar(String name, String color);
    void addKeyword(Integer starId, String keyword);
    void removeKeyword(Integer starId, String keyword);
    void updateColor(Integer id, String color);
}