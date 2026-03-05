package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class HotSearchDTO {
    private String word;       // 热搜关键词
    private Long num;          // 热度数值
    private boolean highlighted; // 是否为监测目标
}