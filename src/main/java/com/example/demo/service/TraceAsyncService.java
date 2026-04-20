package com.example.demo.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TraceAsyncService {

    @Value("${python.interpreter.path:/usr/bin/python3}")
    private String pythonPath;

    private final Map<String, TraceService.TaskStatus> taskStatusMap = new ConcurrentHashMap<>();

    @Async
    public void executeCrawler(String mid, Map<String, TraceService.TaskStatus> statusMap) {
        log.info("🐍 启动 Python 爬虫脚本，MID = {}", mid);
        statusMap.put(mid, TraceService.TaskStatus.RUNNING);

        try {
            File scriptFile = new File("spider/repost_crawler.py");
            if (!scriptFile.exists()) {
                log.error("❌ Python 脚本不存在: {}", scriptFile.getAbsolutePath());
                statusMap.put(mid, TraceService.TaskStatus.FAILED);
                return;
            }
            ProcessBuilder pb = new ProcessBuilder(pythonPath, scriptFile.getAbsolutePath(), mid);
            pb.inheritIO();
            Process process = pb.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("✅ Python 溯源抓取完成，MID={}", mid);
                statusMap.put(mid, TraceService.TaskStatus.COMPLETED);
            } else {
                log.error("❌ Python 脚本执行失败，退出码={}", exitCode);
                statusMap.put(mid, TraceService.TaskStatus.FAILED);
            }
        } catch (Exception e) {
            log.error("❌ 调用 Python 脚本异常", e);
            statusMap.put(mid, TraceService.TaskStatus.FAILED);
        }
    }
}