package com.example.demo.controller;

import com.example.demo.dto.HotSearchItem;
import com.example.demo.dto.StarVO;
import com.example.demo.entity.MonitorStar;
import com.example.demo.repository.MonitorStarMapper;
import com.example.demo.service.WeiboService;
import com.example.demo.service.impl.WeiboServiceImpl;
import com.example.demo.utils.HttpUtils;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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
    public List<HotSearchItem> monitor(HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        // 即使 userId 为空（Session 丢失或未登录），也应该返回基础热搜榜单
        // 只是 item.isHighlighted 都会是 false
        return weiboService.getAnalyzedHotSearch(userId);
    }

    @PostMapping("/star")
    public ResponseEntity<String> addStar(
            @RequestParam String name,
            @RequestParam String color,
            HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) return ResponseEntity.status(401).body("请先登录");

        try {
            weiboService.addStarWithUser(name, color, userId);
            return ResponseEntity.ok("添加成功"); // 必须返回 ok
        } catch (Exception e) {
            return ResponseEntity.status(500).body("数据库写入失败");
        }
    }

    // 获取当前所有监测的名人
    @GetMapping("/stars")
    public List<String> getStarList() {
        return starMapper.findAllNames();
    }

    // 删除指定的监测名人
    @DeleteMapping("/star")
    public ResponseEntity<String> deleteStar(@RequestParam String name, HttpSession session) {
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("请先登录");
        }
        starMapper.deleteByNameAndUser(name, userId);
        return ResponseEntity.ok("删除成功");
    }

    @GetMapping("/stars/detail")
    public List<StarVO> getStarDetails(HttpSession session) {
        // 1. 从 session 获取当前用户 ID
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) {
            return new ArrayList<>(); // 未登录返回空数据
        }

        // 2. 获取该用户名下的所有名人名字
        // 注意：建议在 Mapper 中增加 findAllNamesByUserId(userId) 方法
        List<String> names = starMapper.findAllNamesByUserId(userId);

        // 3. 获取该用户对应的热搜分析结果 (传入 userId)
        List<HotSearchItem> hotItems = weiboService.getAnalyzedHotSearch(userId);

        return names.stream().map(name -> {
            // 计算该名人在当前热搜中的热度总和
            long sum = hotItems.stream()
                    .filter(item -> item.getWord().contains(name))
                    .mapToLong(item -> item.getNum() == null ? 0 : item.getNum())
                    .sum();
            return new StarVO(name, sum);
        }).collect(Collectors.toList());
    }

    @GetMapping("/stars/MonitorDetail")
    public List<MonitorStar> getMonitorDetail(HttpSession session) {
        // 假设你登录成功后在 session 存了 "userId"
        Integer userId = (Integer) session.getAttribute("userId");
        if (userId == null) return new ArrayList<>();

        // 调用 service 时传入 userId
        return weiboService.getMonitorDetail(userId);
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

    @PostMapping("/star/updateColor")
    public ResponseEntity<String> updateColor(@RequestParam Integer id, @RequestParam String color) {
        try {
            weiboService.updateColor(id, color);
            return ResponseEntity.ok("颜色已永久保存");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("保存失败");
        }
    }

}