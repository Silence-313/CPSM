package com.example.demo.entity;

import lombok.Data;

@Data // 如果你没装 Lombok，请手动生成 Getter/Setter
public class WeiboComment {
    private Long id;
    private String mid;          // 微博ID
    private String content;      // 评论内容
    private Double sentimentScore; // 情感得分
    private String sentimentType;  // 情感类型 (POSITIVE/NEUTRAL/NEGATIVE)
}