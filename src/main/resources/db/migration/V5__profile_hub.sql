-- V5: 个人中心模块
ALTER TABLE `sys_user` ADD COLUMN `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像URL';

CREATE TABLE IF NOT EXISTS `user_activity_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `module` VARCHAR(50) NOT NULL COMMENT '模块名',
    `action` VARCHAR(255) NOT NULL COMMENT '操作描述',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_time` (`user_id`, `create_time` DESC),
    CONSTRAINT `fk_activity_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户操作日志';

CREATE TABLE IF NOT EXISTS `app_config` (
    `id` INT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `config_key` VARCHAR(100) NOT NULL,
    `config_value` TEXT,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_key` (`user_id`, `config_key`),
    CONSTRAINT `fk_config_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户配置表';
