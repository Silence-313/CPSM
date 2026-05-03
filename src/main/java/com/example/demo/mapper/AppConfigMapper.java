package com.example.demo.mapper;

import com.example.demo.entity.AppConfig;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AppConfigMapper {

    @Select("SELECT * FROM app_config WHERE user_id = #{userId} AND config_key = #{key}")
    AppConfig selectByKey(@Param("userId") Long userId, @Param("key") String key);

    @Insert("INSERT INTO app_config(user_id, config_key, config_value) VALUES(#{userId}, #{configKey}, #{configValue}) " +
            "ON DUPLICATE KEY UPDATE config_value = VALUES(config_value)")
    int upsert(AppConfig config);

    @Delete("DELETE FROM app_config WHERE user_id = #{userId} AND config_key = #{key}")
    int delete(@Param("userId") Long userId, @Param("key") String key);
}
