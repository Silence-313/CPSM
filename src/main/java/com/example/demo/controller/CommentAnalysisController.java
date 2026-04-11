package com.example.demo.controller;

import com.example.demo.common.WeiboIdParser;
import com.example.demo.dto.AnalysisRequest;
import com.example.demo.service.CommentAnalysisService;
import com.example.demo.vo.AnalysisResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.Map;

/**
 * 微博评论分析控制器
 * 负责处理前端发起的爬取任务、进度查询、情感统计数据获取以及 Cookie 维护
 */
@RestController
@RequestMapping("/api/analysis")
public class CommentAnalysisController {

    @Autowired
    private CommentAnalysisService analysisService;

    /**
     * 发起微博分析任务
     * @param request 包含微博链接或 mid 的请求体
     * @return 包含情感统计、关键词云和示例评论的结果
     */
    @PostMapping("/weibo")
    public AnalysisResultVO doAnalyze(@RequestBody AnalysisRequest request) {
        // 1. 解析微博ID
        String mid = WeiboIdParser.parseMid(request.getMid());
        if (mid == null) {
            throw new IllegalArgumentException("无法识别的微博链接或 ID，请检查输入格式");
        }

        // 2. 先刷新 Cookie（同步等待脚本执行完成）
        refreshCookieSync();

        // 3. 执行分析
        return analysisService.analyzeComments(mid);
    }

    /**
     * 同步刷新 Cookie（等待 Python 脚本执行结束）
     */
    private void refreshCookieSync() {
        try {
            String pythonPath = "/opt/anaconda3/envs/weibo_spider/bin/python";
            File scriptFile = new File("spider/cookie_helper.py");
            if (!scriptFile.exists()) {
                System.err.println("警告：Cookie 刷新脚本不存在，跳过刷新。路径：" + scriptFile.getAbsolutePath());
                return;
            }

            ProcessBuilder pb = new ProcessBuilder(pythonPath, scriptFile.getAbsolutePath());
            pb.inheritIO(); // 将脚本输出打印到控制台
            Process process = pb.start();
            int exitCode = process.waitFor(); // 等待脚本执行结束
            if (exitCode == 0) {
                System.out.println("Cookie 刷新成功。");
            } else {
                System.err.println("Cookie 刷新脚本执行失败，退出码：" + exitCode);
            }
        } catch (Exception e) {
            System.err.println("刷新 Cookie 时发生异常：" + e.getMessage());
        }
    }

    /**
     * 获取爬取进度
     * 用于前端进度条展示
     * @param mid 微博 ID
     * @return 0-100 的整数进度值
     */
    @GetMapping("/progress")
    @ResponseBody
    public int getProgress(@RequestParam String mid) {
        // 同样进行一次解析，确保 ID 格式一致
        String realMid = WeiboIdParser.parseMid(mid);
        return analysisService.getProgress(realMid != null ? realMid : mid);
    }

    /**
     * 获取情感倾向分布统计
     * 专门为 ECharts 饼图或柱状图提供数据
     * @param mid 微博 ID
     * @return 情感分类统计 Map (如 {"POSITIVE": 50, "NEUTRAL": 30, "NEGATIVE": 20})
     */
    @GetMapping("/sentiment-stats")
    public Map<String, Integer> getSentimentStats(@RequestParam String mid) {
        String realMid = WeiboIdParser.parseMid(mid);
        // 调用 Service 中新定义的统计接口
        return analysisService.getSentimentDistribution(realMid != null ? realMid : mid);
    }

    /**
     * 刷新微博 Cookie
     * 通过运行本地 Python 脚本弹出浏览器登录窗
     */
    @PostMapping("/refresh-cookie")
    public String refreshCookie() {
        try {
            // 适配 M4 Mac 的 Conda 环境 Python 路径
            String pythonPath = "/opt/anaconda3/envs/weibo_spider/bin/python";

            // 脚本相对路径：项目根目录下的 spider/cookie_helper.py
            File scriptFile = new File("spider/cookie_helper.py");
            if (!scriptFile.exists()) {
                return "错误：找不到 Python 脚本文件 " + scriptFile.getAbsolutePath();
            }

            String scriptPath = scriptFile.getAbsolutePath();

            ProcessBuilder pb = new ProcessBuilder(pythonPath, scriptPath);
            pb.inheritIO(); // 将 Python 控制台输出直接打印到 IDEA 控制台，方便调试
            pb.start();

            return "已尝试启动 Cookie 获取脚本，请在弹出的浏览器窗口中完成登录。";
        } catch (Exception e) {
            return "启动 Python 失败: " + e.getMessage();
        }
    }
}