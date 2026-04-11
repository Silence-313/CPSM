package com.example.demo.mapper;

import com.example.demo.entity.MonitorStar;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface MonitorStarMapper {

    /**
     * 查询当前用户的所有监测名人及其关键词（用于看板展示）
     */
    @Select("SELECT s.id, s.star_name AS name, s.color, s.user_id, k.keyword " +
            "FROM monitor_star s " +
            "LEFT JOIN monitor_keywords k ON s.id = k.star_id AND s.user_id = k.user_id " +
            "WHERE s.user_id = #{userId}")
    List<MonitorStar> findAllWithKeywords(@Param("userId") Integer userId);

    /**
     * 插入监测名人，自动回填主键 ID
     */
    @Insert("INSERT INTO monitor_star(star_name, color, user_id) VALUES(#{name}, #{color}, #{userId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertStar(MonitorStar star);

    /**
     * 插入颗粒度关键词（需传入 userId 进行数据隔离）
     */
    @Insert("INSERT INTO monitor_keywords(star_id, keyword, user_id) VALUES(#{starId}, #{keyword}, #{userId})")
    int insertKeyword(@Param("starId") Integer starId,
                      @Param("keyword") String keyword,
                      @Param("userId") Integer userId);

    /**
     * 删除关键词（增加 userId 校验，防止越权操作）
     */
    @Delete("DELETE FROM monitor_keywords WHERE star_id = #{starId} AND keyword = #{keyword} AND user_id = #{userId}")
    int deleteKeyword(@Param("starId") Integer starId,
                      @Param("keyword") String keyword,
                      @Param("userId") Integer userId);

    /**
     * 更新名人颜色标记（增加 userId 校验）
     */
    @Update("UPDATE monitor_star SET color = #{color} WHERE id = #{id} AND user_id = #{userId}")
    int updateStarColor(@Param("id") Integer id,
                        @Param("color") String color,
                        @Param("userId") Integer userId);

    /**
     * 查询指定用户的所有监测名人（不含关键词聚合）
     */
    @Select("SELECT id, star_name AS name, color, user_id, create_time " +
            "FROM monitor_star WHERE user_id = #{userId}")
    List<MonitorStar> findAllByUserId(@Param("userId") Integer userId);

    /**
     * 删除指定用户的某个监测名人
     */
    @Delete("DELETE FROM monitor_star WHERE star_name = #{starName} AND user_id = #{userId}")
    int deleteByNameAndUser(@Param("starName") String starName, @Param("userId") Integer userId);

    /**
     * 获取用户监测的所有名人姓名（仅姓名列表）
     */
    @Select("SELECT star_name FROM monitor_star WHERE user_id = #{userId}")
    List<String> findAllNamesByUserId(@Param("userId") Integer userId);

    // ---------- 以下方法为兼容旧代码，不推荐在新业务中使用 ----------

    /**
     * @deprecated 请使用 {@link #findAllNamesByUserId(Integer)} 代替，避免数据混淆
     */
    @Deprecated
    @Select("SELECT star_name AS name FROM monitor_star")
    List<String> findAllNames();
}