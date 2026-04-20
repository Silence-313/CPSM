CREATE TABLE IF NOT EXISTS weibo_repost_tree (
                                                 mid VARCHAR(64) PRIMARY KEY COMMENT '当前微博ID',
                                                 parent_mid VARCHAR(64) COMMENT '父节点ID',
                                                 root_mid VARCHAR(64) NOT NULL COMMENT '根节点ID',
                                                 uid VARCHAR(32) COMMENT '用户ID',
                                                 screen_name VARCHAR(50) COMMENT '昵称',
                                                 content TEXT COMMENT '转发内容',
                                                 repost_num INT DEFAULT 0 COMMENT '二次转发数',
                                                 attitude INT DEFAULT 0 COMMENT '情感态度：1正面 0中性 -1负面',
                                                 post_time DATETIME COMMENT '发布时间',
                                                 create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_root_mid ON weibo_repost_tree(root_mid);
CREATE INDEX idx_parent_mid ON weibo_repost_tree(parent_mid);
CREATE INDEX idx_root_parent ON weibo_repost_tree(root_mid, parent_mid);