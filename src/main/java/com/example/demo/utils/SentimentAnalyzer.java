package com.example.demo.utils;

import org.ansj.domain.Result;
import org.ansj.domain.Term;
import org.ansj.splitWord.analysis.ToAnalysis;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

@Component
public class SentimentAnalyzer {
    private final Set<String> posWords = new HashSet<>();
    private final Set<String> negWords = new HashSet<>();
    private final Set<String> notWords = new HashSet<>();
    private final Map<String, Double> degreeWords = new HashMap<>();

    @PostConstruct
    public void init() {
        try {

            System.out.println("=== 新版 SentimentAnalyzer 被调用 ===");

            loadDict("dict/pos_words.txt", posWords);
            loadDict("dict/neg_words.txt", negWords);
            loadDict("dict/not_words.txt", notWords);
            loadDegreeDict("dict/degree_words.txt", degreeWords);
            System.out.println("积极词数量：" + posWords.size());
            System.out.println("消极词数量：" + negWords.size());
            System.out.println("否定词数量：" + notWords.size());
            System.out.println("程度副词数量：" + degreeWords.size());
        } catch (Exception e) {
            System.err.println("词典加载失败: " + e.getMessage());
        }
    }

    /**
     * 分析文本情感得分 (修复版)
     */
    public double analyzeScore(String text) {
        if (text == null || text.isEmpty()) return 0;

        Result result = ToAnalysis.parse(text);
        List<Term> terms = result.getTerms();

        double totalScore = 0.0;
        int matchCount = 0;               // 记录匹配到的情感词数量，用于归一化

        // 滑动窗口扫描，处理否定词、程度副词与情感词的组合
        for (int i = 0; i < terms.size(); i++) {
            Term term = terms.get(i);
            String word = term.getName();

            // 如果是情感词，向前查找否定词和程度副词
            double polarity = 0.0;
            if (posWords.contains(word)) {
                polarity = 1.0;
            } else if (negWords.contains(word)) {
                polarity = -1.5;   // 负面词基础权重略高
            } else {
                continue;          // 非情感词跳过
            }

            // 向前搜索否定词 (最多向前2个词)
            boolean hasNot = false;
            double degree = 1.0;
            for (int j = Math.max(0, i - 2); j < i; j++) {
                String prevWord = terms.get(j).getName();
                if (notWords.contains(prevWord)) {
                    hasNot = !hasNot;      // 双重否定 = 肯定
                }
                if (degreeWords.containsKey(prevWord)) {
                    degree *= degreeWords.get(prevWord);
                }
            }

            // 应用情感反转
            if (hasNot) {
                polarity *= -1.0;
            }
            // 应用程度权重
            polarity *= degree;

            totalScore += polarity;
            matchCount++;
        }

        // 归一化：避免长文本得分过高，同时处理无匹配情况
        if (matchCount == 0) {
            return 0.0;
        }
        return totalScore / Math.sqrt(matchCount);  // 开方归一化，使分数范围更合理
    }

    // 加载普通词典
    private void loadDict(String path, Set<String> set) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    set.add(trimmed);
                }
            }
        }
    }

    // 加载带权重的程度副词词典
    private void loadDegreeDict(String path, Map<String, Double> map) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.trim().split(":");
                if (parts.length == 2) {
                    map.put(parts[0], Double.parseDouble(parts[1]));
                }
            }
        }
    }
}