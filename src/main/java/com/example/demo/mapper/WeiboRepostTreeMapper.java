package com.example.demo.mapper;

import com.example.demo.entity.WeiboRepostTree;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WeiboRepostTreeMapper {

    @Insert("INSERT INTO weibo_repost_tree (mid, parent_mid, root_mid, uid, screen_name, content, repost_num, attitude, post_time) " +
            "VALUES (#{mid}, #{parentMid}, #{rootMid}, #{uid}, #{screenName}, #{content}, #{repostNum}, #{attitude}, #{postTime}) " +
            "ON DUPLICATE KEY UPDATE repost_num = VALUES(repost_num), attitude = VALUES(attitude)")
    int insert(WeiboRepostTree node);
}