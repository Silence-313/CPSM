package com.example.demo.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class MonitorStar {
    private Integer id;
    private Integer userId;      // 已有
    private String name;         // 对应 star_name
    private String color;
    private LocalDateTime createTime;
    private String keyword;      // MyBatis 映射用
    private List<String> keywords;
    private Integer onBoardCount;
    private Long totalHot;
}