package com.example.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "simulation")
@Data
public class SimulationModelConfig {
    private List<ModelConfig> models = new ArrayList<>();
    private JudgeConfig judge;

    @Data
    public static class ModelConfig {
        private String name;
        private String url;
        private String apiKey;
        private String model;
    }

    @Data
    public static class JudgeConfig {
        private String name;
        private String url;
        private String apiKey;
        private String model;
    }
}