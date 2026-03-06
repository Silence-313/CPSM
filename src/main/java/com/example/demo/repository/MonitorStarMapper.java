package com.example.demo.repository;

import com.example.demo.entity.MonitorStar;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface MonitorStarMapper {
    // 查询所有明星及其关键词（使用关联查询）
    // 颗粒度查询逻辑：联合查询明星和关键词
    @Select("SELECT s.id, s.star_name AS name, s.color, k.keyword " +
            "FROM monitor_star s " +
            "LEFT JOIN monitor_keywords k ON s.id = k.star_id " + // 注意此处的空格
            "WHERE s.user_id = #{userId}")
    List<MonitorStar> findAllWithKeywords(@Param("userId") Integer userId);

    @Select("SELECT star_name AS name FROM monitor_star")
    List<String> findAllNames();

    // 关键：使用 @Options 获取自动生成的 ID 并回填到 star 对象的 id 属性中
    @Insert("INSERT INTO monitor_star(star_name, color, user_id) VALUES(#{name}, #{color}, #{userId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertStar(MonitorStar star);

    // 插入从表关键词
    @Insert("INSERT INTO monitor_keywords(star_id, keyword) VALUES(#{starId}, #{keyword})")
    int insertKeyword(@Param("starId") Integer starId, @Param("keyword") String keyword);

    @Delete("DELETE FROM monitor_keywords WHERE star_id = #{starId} AND keyword = #{keyword}")
    int deleteKeyword(@Param("starId") Integer starId, @Param("keyword") String keyword);

    @Update("UPDATE monitor_star SET color = #{color} WHERE id = #{id}")
    int updateStarColor(@Param("id") Integer id, @Param("color") String color);

    @Select("SELECT s.id, s.star_name AS name, s.color, k.keyword " +
            "FROM monitor_star s " +
            "LEFT JOIN monitor_keywords k ON s.id = k.star_id " +
            "WHERE s.user_id = #{userId}")
    List<MonitorStar> findAllByUserId(@Param("userId") Integer userId);

    // 对应上面的删除逻辑
    @Delete("DELETE FROM monitor_star WHERE star_name = #{starName} AND user_id = #{userId}")
    int deleteByNameAndUser(@Param("starName") String starName, @Param("userId") Integer userId);

    @Select("SELECT star_name FROM monitor_star WHERE user_id = #{userId}")
    List<String> findAllNamesByUserId(@Param("userId") Integer userId);
}