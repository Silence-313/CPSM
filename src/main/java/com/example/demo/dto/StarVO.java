package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StarVO {
    private String name;
    private Long totalHot; // 综合热度
}