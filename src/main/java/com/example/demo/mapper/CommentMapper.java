package com.example.demo.repository;

import com.example.demo.entity.WeiboComment;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CommentMapper {
    @Insert("INSERT INTO weibo_comments(mid, content, sentiment_score, sentiment_type) " +
            "VALUES(#{mid}, #{content}, #{sentimentScore}, #{sentimentType})")
    void insert(WeiboComment comment);
}