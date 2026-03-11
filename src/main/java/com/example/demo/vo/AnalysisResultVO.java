// src/main/java/com/example/demo/vo/AnalysisResultVO.java
package com.example.demo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor  // 生成无参构造函数：public AnalysisResultVO() {}
@AllArgsConstructor // 生成全参构造函数：按照字段定义顺序生成
public class AnalysisResultVO {
    private int positive;
    private int neutral;
    private int negative;
    private List<CommentSnippet> topComments;
    private String errorMsg;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CommentSnippet {
        private String text;
        private double sentiment;
    }
}