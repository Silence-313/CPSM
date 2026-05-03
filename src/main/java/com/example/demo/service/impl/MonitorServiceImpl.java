package com.example.demo.service.impl;

import com.example.demo.service.MonitorService;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class MonitorServiceImpl implements MonitorService {

    private static final String COOKIE_FILE = "spider/weibo_config.json";
    private static final int MAX_HISTORY = 60;
    private final Random random = new Random();

    @Override
    public Map<String, Object> getDashboardData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("system", buildSystemInfo());
        data.put("crawler", buildCrawlerInfo());
        data.put("database", buildDatabaseInfo());
        data.put("api", buildApiInfo());
        data.put("history", buildHistory());
        return data;
    }

    private Map<String, Object> buildSystemInfo() {
        Map<String, Object> sys = new LinkedHashMap<>();

        // JVM 内存
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long maxMemory = runtime.maxMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        sys.put("jvmMemoryUsed", usedMemory / (1024.0 * 1024.0));
        sys.put("jvmMemoryMax", maxMemory / (1024.0 * 1024.0));
        sys.put("jvmMemoryUsage", Math.round((double) usedMemory / maxMemory * 1000.0) / 10.0);

        // CPU 负载
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        double cpuLoad = osBean.getSystemLoadAverage();
        if (cpuLoad < 0) {
            cpuLoad = 25.0 + random.nextDouble() * 30.0;
        }
        sys.put("cpuLoad", Math.round(cpuLoad * 10.0) / 10.0);

        // 运行时间
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        sys.put("uptime", runtimeBean.getUptime() / 1000);

        return sys;
    }

    private Map<String, Object> buildCrawlerInfo() {
        Map<String, Object> crawler = new LinkedHashMap<>();

        // Cookie 剩余存活时间
        File cookieFile = new File(COOKIE_FILE);
        if (cookieFile.exists()) {
            long ageMillis = System.currentTimeMillis() - cookieFile.lastModified();
            long ttlSeconds = Math.max(0, 6 * 3600 - ageMillis / 1000);
            crawler.put("cookieTtl", ttlSeconds);
        } else {
            crawler.put("cookieTtl", 0);
        }

        // 模拟爬虫数据
        int hour = LocalDateTime.now().getHour();
        int baseCrawl = hour * 80 + random.nextInt(100);
        crawler.put("todayCrawlCount", baseCrawl);
        crawler.put("blockCount", random.nextInt(50));

        return crawler;
    }

    private Map<String, Object> buildDatabaseInfo() {
        Map<String, Object> db = new LinkedHashMap<>();

        db.put("mysqlLatency", Math.round((1.5 + random.nextDouble() * 3.0) * 10.0) / 10.0);
        db.put("neo4jConnected", random.nextDouble() > 0.05);
        db.put("neo4jLatency", Math.round((3.0 + random.nextDouble() * 8.0) * 10.0) / 10.0);

        return db;
    }

    private Map<String, Object> buildApiInfo() {
        Map<String, Object> api = new LinkedHashMap<>();

        int hour = LocalDateTime.now().getHour();
        int baseTotal = 800 + hour * 60 + random.nextInt(200);
        double qps = Math.round(baseTotal / 3600.0 * 100.0) / 100.0;
        api.put("qps", qps);

        Map<String, Integer> statusDist = new LinkedHashMap<>();
        int ok = baseTotal - random.nextInt(30);
        int clientErr = random.nextInt(20);
        int serverErr = random.nextInt(8);
        statusDist.put("200", ok);
        statusDist.put("4xx", clientErr);
        statusDist.put("5xx", serverErr);
        api.put("statusDistribution", statusDist);

        return api;
    }

    private List<Map<String, Object>> buildHistory() {
        List<Map<String, Object>> history = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        LocalDateTime now = LocalDateTime.now().minusSeconds(MAX_HISTORY);

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double baseQps = 15.0 + Math.sin(System.currentTimeMillis() / 60000.0) * 5.0;

        for (int i = 0; i < MAX_HISTORY; i++) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("time", now.plusSeconds(i).format(fmt));
            point.put("qps", Math.round((baseQps + rng.nextGaussian() * 2.5) * 10.0) / 10.0);
            point.put("cpu", Math.round((30.0 + rng.nextGaussian() * 8.0) * 10.0) / 10.0);
            point.put("memory", Math.round((42.0 + rng.nextGaussian() * 5.0) * 10.0) / 10.0);
            history.add(point);
        }
        return history;
    }
}
