package com.example.demo.entity;

import lombok.Data;

@Data
public class AppConfig {
    private Integer id;
    private Long userId;
    private String configKey;
    private String configValue;
}
