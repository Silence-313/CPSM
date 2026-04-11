package com.example.demo.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WeiboComment {
    private Long id;
    private String mid;
    private String weiboTitle;   // 新增
    private String content;
    private Double sentimentScore;
    private String sentimentType;
    private Integer userId;      // 新增
    private LocalDateTime createTime;
}