package com.example.demo.mapper;

import com.example.demo.entity.SimResult;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SimResultMapper {
    @Insert("INSERT INTO sim_result(session_id, model_name, raw_output) VALUES(#{sessionId}, #{modelName}, #{rawOutput})")
    int insert(SimResult result);

    @Select("SELECT * FROM sim_result WHERE session_id=#{sessionId}")
    List<SimResult> selectBySessionId(Long sessionId);

    @Delete("DELETE FROM sim_result WHERE session_id = #{sessionId}")
    int deleteBySessionId(@Param("sessionId") Long sessionId);

}