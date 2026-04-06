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
            loadDict("dict/pos_words.txt", posWords);
            loadDict("dict/neg_words.txt", negWords);
            loadDict("dict/not_words.txt", notWords);
            loadDegreeDict("dict/degree_words.txt", degreeWords);
        } catch (Exception e) {
            System.err.println("词典加载失败: " + e.getMessage());
        }
    }

    public double analyzeScore(String text) {
        if (text == null || text.isEmpty()) return 0;

        Result result = ToAnalysis.parse(text);
        List<Term> terms = result.getTerms();

        double totalScore = 0;
        double currentDegree = 1.0;
        int negativeFlip = 1;

        for (Term term : terms) {
            String word = term.getName();

            if (notWords.contains(word)) {
                negativeFlip *= -1; // 遇到否定词，情感反向
            } else if (degreeWords.containsKey(word)) {
                currentDegree = degreeWords.get(word); // 更新程度副词权重
            } else if (posWords.contains(word)) {
                totalScore += (1.0 * currentDegree * negativeFlip);
                resetWeight(currentDegree, negativeFlip); // 使用后重置
            } else if (negWords.contains(word)) {
                totalScore += (-1.5 * currentDegree * negativeFlip); // 消极词通常权重更高
                resetWeight(currentDegree, negativeFlip);
            }
        }
        return totalScore;
    }

    private void resetWeight(Double d, Integer n) {
        d = 1.0; n = 1;
    }

    // 读取普通词典的方法
    private void loadDict(String path, Set<String> set) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(new ClassPathResource(path).getInputStream()));
        String line;
        while ((line = br.readLine()) != null) {
            if (!line.trim().isEmpty()) set.add(line.trim());
        }
    }

    // 读取带权重的程度副词词典
    private void loadDegreeDict(String path, Map<String, Double> map) throws Exception {
        BufferedReader br = new BufferedReader(new InputStreamReader(new ClassPathResource(path).getInputStream()));
        String line;
        while ((line = br.readLine()) != null) {
            String[] parts = line.split(":");
            if (parts.length == 2) map.put(parts[0], Double.parseDouble(parts[1]));
        }
    }
}