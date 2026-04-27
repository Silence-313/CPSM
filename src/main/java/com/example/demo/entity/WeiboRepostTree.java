package com.example.demo.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class WeiboRepostTree {
    private String mid;
    private String parentMid;
    private String rootMid;
    private String uid;
    private String screenName;
    private String content;
    private Integer repostNum;
    private Integer attitude;
    private LocalDateTime postTime;
}