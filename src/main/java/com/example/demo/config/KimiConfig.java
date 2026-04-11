package com.example.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "kimi")
public class KimiConfig {
    private String apiKey;
    private String baseUrl;
    private String model;
}