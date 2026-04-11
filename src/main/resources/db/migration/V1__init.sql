-- =====================================================
-- Flyway 初始化迁移脚本 V1
-- 微博监测系统（多用户隔离版）
-- =====================================================

-- 1. 系统用户表
CREATE TABLE IF NOT EXISTS `sys_user` (
                                          `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
                                          `username` VARCHAR(50) NOT NULL COMMENT '用户名',
                                          `password` VARCHAR(100) DEFAULT NULL COMMENT '加密密码',
                                          `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
                                          `email` VARCHAR(50) DEFAULT NULL COMMENT '邮箱',
                                          `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                          PRIMARY KEY (`id`),
                                          UNIQUE KEY `uk_username` (`username`),
                                          UNIQUE KEY `uk_phone` (`phone`),
                                          UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 2. 名人监测主表
CREATE TABLE IF NOT EXISTS `monitor_star` (
                                              `id` INT NOT NULL AUTO_INCREMENT COMMENT '监测ID',
                                              `star_name` VARCHAR(50) NOT NULL COMMENT '名人姓名',
                                              `color` VARCHAR(10) DEFAULT '#ff8200' COMMENT '标记颜色',
                                              `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
                                              `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                              PRIMARY KEY (`id`),
                                              UNIQUE KEY `uk_star_name_user` (`star_name`, `user_id`),
                                              KEY `idx_user_id` (`user_id`),
                                              CONSTRAINT `fk_star_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='名人监测表';

-- 3. 关键词表
CREATE TABLE IF NOT EXISTS `monitor_keywords` (
                                                  `id` INT NOT NULL AUTO_INCREMENT COMMENT '关键词ID',
                                                  `star_id` INT NOT NULL COMMENT '关联的监测名人ID',
                                                  `keyword` VARCHAR(100) NOT NULL COMMENT '关键词',
                                                  `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
                                                  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                                  PRIMARY KEY (`id`),
                                                  UNIQUE KEY `uk_star_keyword` (`star_id`, `keyword`),
                                                  KEY `idx_user_id` (`user_id`),
                                                  CONSTRAINT `fk_keyword_star` FOREIGN KEY (`star_id`) REFERENCES `monitor_star` (`id`) ON DELETE CASCADE,
                                                  CONSTRAINT `fk_keyword_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关键词表';

-- 4. 微博评论情感分析记录表
CREATE TABLE IF NOT EXISTS `weibo_comments` (
                                                `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
                                                `mid` VARCHAR(20) NOT NULL COMMENT '微博ID',
                                                `content` TEXT NOT NULL COMMENT '评论内容',
                                                `sentiment_score` DOUBLE DEFAULT NULL COMMENT '情感得分',
                                                `sentiment_type` VARCHAR(20) DEFAULT NULL COMMENT '情感类型(POSITIVE/NEUTRAL/NEGATIVE)',
                                                `user_id` BIGINT NOT NULL COMMENT '发起分析的用户ID',
                                                `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
                                                PRIMARY KEY (`id`),
                                                KEY `idx_mid` (`mid`),
                                                KEY `idx_user_id` (`user_id`),
                                                CONSTRAINT `fk_comment_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微博评论情感分析记录';