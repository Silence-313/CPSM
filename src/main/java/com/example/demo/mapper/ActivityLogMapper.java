package com.example.demo.mapper;

import com.example.demo.entity.ActivityLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ActivityLogMapper {

    @Insert("INSERT INTO user_activity_log(user_id, module, action) VALUES(#{userId}, #{module}, #{action})")
    int insert(ActivityLog log);

    @Select("SELECT * FROM user_activity_log WHERE user_id = #{userId} ORDER BY create_time DESC LIMIT #{limit}")
    List<ActivityLog> selectByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    @Delete("DELETE FROM user_activity_log WHERE user_id = #{userId}")
    int deleteAllByUser(@Param("userId") Long userId);
}
