package com.example.demo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class InfraStartupRunner implements ApplicationRunner {

    @Value("${infra.script.path:./start-infra.sh}")
    private String infraScriptPath;

    @Override
    public void run(ApplicationArguments args) {
        log.info("🔧 检查基础设施容器状态...");

        boolean neo4jRunning = isContainerRunning("cpms-neo4j");
        boolean esRunning = isContainerRunning("es-server");

        if (neo4jRunning && esRunning) {
            log.info("✅ Neo4j 和 Elasticsearch 容器已在运行，跳过启动");
            return;
        }

        if (!neo4jRunning) {
            log.warn("⚠️ cpms-neo4j 容器未运行");
        }
        if (!esRunning) {
            log.warn("⚠️ es-server 容器未运行");
        }

        log.info("🚀 执行启动脚本: {}", infraScriptPath);
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", infraScriptPath);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("  [infra] {}", line);
                }
            }

            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                log.info("✅ 基础设施容器启动完成");
            } else {
                log.error("❌ 启动脚本执行异常, exitCode={}", finished ? process.exitValue() : "timeout");
            }
        } catch (Exception e) {
            log.error("❌ 启动基础设施失败: {}", e.getMessage());
        }
    }

    private boolean isContainerRunning(String name) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("docker");
            cmd.add("ps");
            cmd.add("--filter");
            cmd.add("name=" + name);
            cmd.add("--format");
            cmd.add("{{.Names}}");

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }

            process.waitFor(10, TimeUnit.SECONDS);
            return output.toString().contains(name);
        } catch (Exception e) {
            log.warn("检查容器 {} 状态失败: {}", name, e.getMessage());
            return false;
        }
    }
}
