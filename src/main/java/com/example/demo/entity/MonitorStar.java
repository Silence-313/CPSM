package com.example.demo.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class MonitorStar {
    private Integer id;
    private Integer userId;
    private String name;
    private String color;
    private LocalDateTime create_time;

    private String keyword;

    // 关键：存放该明星关联的所有颗粒度关键词
    private List<String> keywords;

    // 统计字段（非数据库字段，用于业务逻辑）
    private Integer onBoardCount = 0;
    private Long totalHot = 0L;
}