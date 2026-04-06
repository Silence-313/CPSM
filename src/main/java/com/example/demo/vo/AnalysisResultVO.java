package com.example.demo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResultVO {
    private int positive;
    private int neutral;
    private int negative;
    private List<CommentSnippet> topComments;
    private String errorMsg;
    // 新增：词云数据，格式为 [{name: "词语", value: 次数}, ...]
    private List<Map<String, Object>> wordCloudData;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CommentSnippet {
        private String text;
        private double sentiment;
    }
}