package com.example.demo.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SimResult {
    private Long id;
    private Long sessionId;
    private String modelName;
    private String rawOutput;
    private LocalDateTime createTime;
}