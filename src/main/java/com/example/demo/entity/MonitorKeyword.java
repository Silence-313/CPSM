package com.example.demo.entity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MonitorKeyword {
    private Integer id;
    private Integer starId;
    private String keyword;
    private Integer userId;      // 新增
    private LocalDateTime createTime;
}