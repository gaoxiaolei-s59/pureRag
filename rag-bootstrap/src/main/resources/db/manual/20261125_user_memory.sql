-- MySQL 8 / InnoDB。开启 rag.memory.long-term-enabled 前执行，可重复执行。
CREATE TABLE IF NOT EXISTS t_user_memory_profile (
    user_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    facts JSON NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 仅关联原始用户消息，不复制聊天原文；源消息在处理前被删除时会跳过学习。
CREATE TABLE IF NOT EXISTS t_user_memory_job (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    source_message_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    failure_count TINYINT NOT NULL DEFAULT 0 COMMENT '连续失败 5 次后终止，保留记录待排查',
    retry_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_memory_source (user_id, source_message_id),
    KEY idx_user_memory_order (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
