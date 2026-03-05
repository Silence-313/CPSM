package com.example.demo.controller;

import com.example.demo.dto.HotSearchItem;
import com.example.demo.dto.StarVO;
import com.example.demo.entity.MonitorStar;
import com.example.demo.repository.MonitorStarMapper;
import com.example.demo.service.WeiboService;
import com.example.demo.service.impl.WeiboServiceImpl;
import com.example.demo.utils.HttpUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/weibo")
public class HotSearchController {

    @Autowired
    private HttpUtils httpUtils;

    @Autowired
    private WeiboService weiboService;

    @Autowired
    private WeiboServiceImpl weiboServiceimpl;

    @Autowired
    private MonitorStarMapper starMapper;
    /**
     * 测试接口：直接返回微博 API 的原始 JSON 字符串
     */
    @GetMapping("/weibo-raw")
    public String testWeiboApi() {
        String json = httpUtils.getWeiboHotSearchJson();
        if (json == null) {
            return "{\"error\": \"无法访问微博 API，请检查网络或 Header 配置\"}";
        }
        return json;
    }

    @GetMapping("/monitor")
    public List<HotSearchItem> monitor() {
        return weiboServiceimpl.getAnalyzedHotSearch();
    }

    @PostMapping("/star")
    public String addStar(@RequestParam String name,
                          @RequestParam(defaultValue = "#ff8200") String color) {
        weiboServiceimpl.addStar(name, color);
        return "添加成功: " + name;
    }

    // 获取当前所有监测的名人
    @GetMapping("/stars")
    public List<String> getStarList() {
        return starMapper.findAllNames();
    }

    // 删除指定的监测名人
    @DeleteMapping("/star")
    public String deleteStar(@RequestParam String name) {
        starMapper.deleteByName(name);
        return "已移除: " + name;
    }

    @GetMapping("/stars/detail")
    public List<MonitorStar> getStarDetails() {
        // 直接调用 service 中已经写好的核心监控逻辑
        // 该方法会自动关联查询关键词，并计算当前热搜中的 totalHot 和 onBoardCount
        return weiboService.getMonitorDetail();
    }

    @PostMapping("/star/keyword")
    public ResponseEntity<String> addKeyword(@RequestParam Integer starId, @RequestParam String keyword) {
        try {
            weiboService.addKeyword(starId, keyword);
            return ResponseEntity.ok("添加成功");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("添加失败");
        }
    }

    // 新增：删除关键词
    // 对应前端：fetch(`/api/weibo/star/keyword?starId=${starId}&keyword=${...}`, { method: 'DELETE' })
    @DeleteMapping("/star/keyword")
    public ResponseEntity<String> removeKeyword(@RequestParam Integer starId, @RequestParam String keyword) {
        try {
            weiboService.removeKeyword(starId, keyword);
            return ResponseEntity.ok("删除成功");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("删除失败");
        }
    }
}