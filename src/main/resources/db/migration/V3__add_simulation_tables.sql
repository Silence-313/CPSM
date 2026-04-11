-- 沙盘模拟会话表
CREATE TABLE IF NOT EXISTS `sim_session` (
                                             `id` BIGINT NOT NULL AUTO_INCREMENT,
                                             `user_id` BIGINT NOT NULL COMMENT '用户ID',
                                             `event_description` TEXT NOT NULL COMMENT '模拟事件描述',
                                             `final_verdict` TEXT COMMENT 'DeepSeek最终裁决',
                                             `risk_level` VARCHAR(20) COMMENT '风险等级(HIGH/MEDIUM/LOW)',
                                             `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                             PRIMARY KEY (`id`),
                                             KEY `idx_user_id` (`user_id`),
                                             CONSTRAINT `fk_session_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 各模型预测结果表
CREATE TABLE IF NOT EXISTS `sim_result` (
                                            `id` BIGINT NOT NULL AUTO_INCREMENT,
                                            `session_id` BIGINT NOT NULL,
                                            `model_name` VARCHAR(50) NOT NULL COMMENT '模型名称',
                                            `raw_output` TEXT NOT NULL COMMENT '原始输出',
                                            `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
                                            PRIMARY KEY (`id`),
                                            KEY `idx_session_id` (`session_id`),
                                            CONSTRAINT `fk_result_session` FOREIGN KEY (`session_id`) REFERENCES `sim_session` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;