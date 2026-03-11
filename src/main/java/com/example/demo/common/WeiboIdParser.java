package com.example.demo.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WeiboIdParser {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /**
     * 核心解析方法：支持纯数字、移动端链接、网页端Base62链接
     */
    public static String parseMid(String input) {
        if (input == null || input.trim().isEmpty()) return null;

        // 1. 尝试匹配链接中的纯数字 MID (针对 m.weibo.cn 或 weibo.com/detail/数字)
        Pattern numPattern = Pattern.compile("(?:status/|detail/)(\\d{9,16})");
        Matcher m1 = numPattern.matcher(input);
        if (m1.find()) return m1.group(1);

        // 2. 尝试匹配网页端链接中的 Base62 ID (如 QvsJI7fRy)
        // 匹配规则：weibo.com/用户UID/Base62ID
        // src/main/java/com/example/demo/common/WeiboIdParser.java

// 修改这一行：将匹配结尾改为非 A-Za-z0-9 的字符或结尾
        Pattern b62Pattern = Pattern.compile("weibo\\.com/\\d+/([A-Za-z0-9]{4,})(?:\\?|/|$)");
        Matcher m2 = b62Pattern.matcher(input);
        if (m2.find()) {
            return midFromBase62(m2.group(1));
        }

        // 3. 如果输入本身就是 9-16 位的纯数字
        if (input.matches("^\\d{9,16}$")) return input;

        return null;
    }

    /**
     * 微博 Base62 转换算法
     * 将 QvsJI7fRy 这种格式转回数字 MID
     */
    private static String midFromBase62(String b62) {
        StringBuilder mid = new StringBuilder();
        // 算法逻辑：从后往前，每 4 位 Base62 字符对应一组数字
        for (int i = b62.length() - 4; i > -4; i -= 4) {
            int offset = Math.max(i, 0);
            int len = i < 0 ? 4 + i : 4;
            String sub = b62.substring(offset, offset + len);
            String decoded = String.valueOf(base62ToDecimal(sub));

            // 补齐 7 位（除了最左边的一组数字外，其余组不满 7 位需补0）
            if (offset > 0) {
                while (decoded.length() < 7) {
                    decoded = "0" + decoded;
                }
            }
            mid.insert(0, decoded);
        }
        return mid.toString();
    }

    private static long base62ToDecimal(String s) {
        long res = 0;
        for (int i = 0; i < s.length(); i++) {
            res = res * 62 + ALPHABET.indexOf(s.charAt(i));
        }
        return res;
    }
}