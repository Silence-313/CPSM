package com.example.demo.component;

import com.example.demo.service.OpenAICompatibleService;
import com.example.demo.service.OpenAICompatibleService.ModelResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModelConnectivityTester {

    private final OpenAICompatibleService aiService;

    /**
     * 项目启动后自动执行连通性测试（异步，不阻塞启动）
     */
    @PostConstruct
    public void testAllModelsOnStartup() {
        CompletableFuture.runAsync(() -> {
            // 稍等一秒，确保日志系统完全初始化
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {}

            log.info("========================================");
            log.info("🚀 开始大模型 API 连通性测试...");
            log.info("========================================");

            String testPrompt = "请回复'连通成功'四个字，不需要其他任何内容。";

            // 并发测试所有模型
            CompletableFuture<ModelResponse> qwenFuture = CompletableFuture.supplyAsync(() -> aiService.callQwen(testPrompt));
            CompletableFuture<ModelResponse> deepseekFuture = CompletableFuture.supplyAsync(() -> aiService.callDeepSeek(testPrompt));
            CompletableFuture<ModelResponse> siliconflowFuture = CompletableFuture.supplyAsync(() -> aiService.callSiliconflow(testPrompt));
            CompletableFuture<ModelResponse> doubaoFuture = CompletableFuture.supplyAsync(() -> aiService.callDoubao(testPrompt));
            CompletableFuture<ModelResponse> hunyuanFuture = CompletableFuture.supplyAsync(() -> aiService.callHunyuan(testPrompt));
            CompletableFuture<ModelResponse> kimiFuture = CompletableFuture.supplyAsync(() -> aiService.callKimi(testPrompt));
            CompletableFuture<ModelResponse> siliconFuture = CompletableFuture.supplyAsync(() -> aiService.callSiliconflow(testPrompt));

            // 等待所有测试完成
            CompletableFuture.allOf(qwenFuture, deepseekFuture, siliconflowFuture,
                    doubaoFuture, hunyuanFuture, kimiFuture).join();

            // 汇总结果
            log.info("========================================");
            log.info("📊 连通性测试结果汇总：");
            log.info("========================================");
            printResult(qwenFuture.join());
            printResult(deepseekFuture.join());
            printResult(siliconflowFuture.join());
            printResult(doubaoFuture.join());
            printResult(hunyuanFuture.join());
            printResult(kimiFuture.join());
            printResult(siliconFuture.join());
            log.info("========================================");
            log.info("✅ 连通性测试完成！");
            log.info("========================================");
        });
    }

    private void printResult(ModelResponse response) {
        if (response.success) {
            log.info("✅ {} - 成功 (耗时: {}ms, 返回: {})",
                    response.modelName, response.costTime,
                    response.content.length() > 50 ? response.content.substring(0, 50) + "..." : response.content);
        } else {
            log.error("❌ {} - 失败 (耗时: {}ms, 错误: {})",
                    response.modelName, response.costTime, response.errorMsg);
        }
    }
}