package com.example.demo.mapper;

import com.example.demo.entity.WeiboComment;
import com.example.demo.vo.AnalysisHistoryDetailVO;
import com.example.demo.vo.AnalysisHistoryVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CommentMapper {
    /**
     * 查询用户的所有历史分析记录（按微博ID分组，统计各情感类型数量及最近分析时间）
     */
    @Select("SELECT mid, MAX(weibo_title) as weibo_title, MAX(create_time) as last_analyze_time, COUNT(*) as total_comments, " +
            "SUM(CASE WHEN sentiment_type = 'POSITIVE' THEN 1 ELSE 0 END) as positive_count, " +
            "SUM(CASE WHEN sentiment_type = 'NEUTRAL' THEN 1 ELSE 0 END) as neutral_count, " +
            "SUM(CASE WHEN sentiment_type = 'NEGATIVE' THEN 1 ELSE 0 END) as negative_count " +
            "FROM weibo_comments WHERE user_id = #{userId} " +
            "GROUP BY mid ORDER BY last_analyze_time DESC")
    List<AnalysisHistoryVO> getUserHistory(@Param("userId") Integer userId);

    @Select("SELECT content as text, sentiment_score as sentiment FROM weibo_comments " +
            "WHERE mid = #{mid} AND user_id = #{userId} ORDER BY create_time DESC")
    List<AnalysisHistoryDetailVO.CommentSnippet> getCommentsByMidAndUser(@Param("mid") String mid, @Param("userId") Integer userId);

    @Insert("INSERT INTO weibo_comments(mid, content, sentiment_score, sentiment_type, user_id, weibo_title) " +
            "VALUES(#{mid}, #{content}, #{sentimentScore}, #{sentimentType}, #{userId}, #{weiboTitle})")
    void insert(WeiboComment comment);

    @Delete("DELETE FROM weibo_comments WHERE mid = #{mid} AND user_id = #{userId}")
    int deleteByMidAndUser(@Param("mid") String mid, @Param("userId") Integer userId);

    @Delete("DELETE FROM weibo_comments WHERE user_id = #{userId}")
    int deleteAllByUser(@Param("userId") Integer userId);

}