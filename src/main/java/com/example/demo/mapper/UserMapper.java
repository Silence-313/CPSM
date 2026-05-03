package com.example.demo.mapper;

import com.example.demo.entity.User;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper {

    // 登录：支持 用户名/手机/邮箱 + 密码
    @Select("SELECT * FROM sys_user WHERE (username=#{acc} OR phone=#{acc} OR email=#{acc}) AND password=#{pwd}")
    User login(@Param("acc") String account, @Param("pwd") String password);

    // 注册：务必包含 phone 和 email
    @Insert("INSERT INTO sys_user(username, password, phone, email) VALUES(#{username}, #{password}, #{phone}, #{email})")
    int register(User user);

    // 找回密码：验证 (手机号对得上 OR 邮箱对得上) AND 用户名也对得上
    @Select("SELECT * FROM sys_user WHERE (phone=#{acc} OR email=#{acc}) AND username=#{un}")
    User findByAccountAndName(@Param("acc") String account, @Param("un") String username);

    // 更新密码
    @Update("UPDATE sys_user SET password=#{pwd} WHERE id=#{id}")
    void updatePassword(@Param("id") Long id, @Param("pwd") String pwd);

    @Update("UPDATE sys_user SET username=#{username} WHERE id=#{id}")
    int updateUsername(@Param("id") Long id, @Param("username") String username);

    @Update("UPDATE sys_user SET avatar=#{avatar} WHERE id=#{id}")
    int updateAvatar(@Param("id") Long id, @Param("avatar") String avatar);

    @Select("SELECT * FROM sys_user WHERE id=#{id}")
    User selectById(@Param("id") Long id);
}