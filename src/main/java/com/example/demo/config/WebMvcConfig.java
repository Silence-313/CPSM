package com.example.demo.config; // 这里的路径要和你创建的文件夹对应

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        /**
         * 当用户访问根路径 "/" 时
         * 自动重定向 (Redirect) 到 "/login.html"
         */
        registry.addRedirectViewController("/", "/login.html");

    }
}