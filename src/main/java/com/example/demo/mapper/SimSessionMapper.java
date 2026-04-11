package com.example.demo.mapper;

import com.example.demo.entity.SimSession;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SimSessionMapper {
    @Insert("INSERT INTO sim_session(user_id, event_description, final_verdict, risk_level) VALUES(#{userId}, #{eventDescription}, #{finalVerdict}, #{riskLevel})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SimSession session);

    @Update("UPDATE sim_session SET final_verdict=#{finalVerdict}, risk_level=#{riskLevel} WHERE id=#{id}")
    int update(SimSession session);

    @Select("SELECT * FROM sim_session WHERE id=#{id}")
    SimSession selectById(Long id);

    @Select("SELECT * FROM sim_session WHERE user_id=#{userId} ORDER BY create_time DESC")
    List<SimSession> selectByUserId(Long userId);

    @Delete("DELETE FROM sim_session WHERE id = #{id} AND user_id = #{userId}")
    int deleteByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);

    @Delete("DELETE FROM sim_session WHERE user_id = #{userId}")
    int deleteAllByUser(@Param("userId") Long userId);

}