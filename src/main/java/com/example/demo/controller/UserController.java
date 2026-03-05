package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.repository.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {
    @Autowired
    private UserMapper userMapper;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> data) {
        User user = userMapper.login(data.get("account"), data.get("password"));
        return user != null ? ResponseEntity.ok(user) : ResponseEntity.status(401).body("账号或密码错误");
    }

    @PostMapping("/reg")
    public ResponseEntity<?> register(@RequestBody User user) {
        try {
            userMapper.register(user);
            return ResponseEntity.ok("注册成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("用户名或联系方式已存在");
        }
    }

    @PostMapping("/forget")
    public ResponseEntity<?> reset(@RequestBody Map<String, String> data) {
        String account = data.get("account");
        String username = data.get("username");

        System.out.println("正在验证找回密码: 账号=" + account + ", 用户名=" + username);

        User user = userMapper.findByAccountAndName(account, username);

        if (user != null) {
            System.out.println("验证通过，用户ID: " + user.getId());
            userMapper.updatePassword(user.getId(), data.get("password"));
            return ResponseEntity.ok("密码已重置");
        }

        System.out.println("验证失败：数据库中找不到匹配项");
        return ResponseEntity.status(404).body("信息验证失败");
    }
}