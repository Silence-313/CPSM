package com.example.demo.dto;

import lombok.Data;

@Data
public class HotSearchItem {
    private String word;       // 热搜词
    private Long num;          // 热度值
    private String label_name; // 标签（如：新、热、爆）
    private int rank;          // 排名
    private boolean highlighted; // 我们自己添加的逻辑字段：是否匹配到名人
    private String color;
}