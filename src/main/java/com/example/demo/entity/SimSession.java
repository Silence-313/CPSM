package com.example.demo.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SimSession {
    private Long id;
    private Long userId;
    private String eventDescription;
    private String finalVerdict;
    private String riskLevel;
    private LocalDateTime createTime;
}