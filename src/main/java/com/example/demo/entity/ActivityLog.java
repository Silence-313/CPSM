package com.example.demo.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ActivityLog {
    private Long id;
    private Long userId;
    private String module;
    private String action;
    private LocalDateTime createTime;
}
