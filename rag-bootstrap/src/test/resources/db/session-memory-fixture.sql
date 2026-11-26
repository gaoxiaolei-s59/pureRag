CREATE TABLE IF NOT EXISTS t_memory (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64),
    conversation_id VARCHAR(64),
    role VARCHAR(16),
    content TEXT,
    thinking_content TEXT,
    thinking_duration INT,
    compressed_flag TINYINT DEFAULT 0,
    del_flag TINYINT DEFAULT 0,
    create_time DATETIME(3),
    update_time DATETIME(3)
);

CREATE TABLE IF NOT EXISTS t_conversation_summary (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64),
    conversation_id VARCHAR(64),
    summary_content TEXT,
    del_flag TINYINT DEFAULT 0,
    create_time DATETIME(3),
    update_time DATETIME(3)
);

CREATE TABLE IF NOT EXISTS t_conversation (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64),
    title VARCHAR(100),
    description VARCHAR(256),
    deep_thinking TINYINT,
    pinned TINYINT,
    del_flag TINYINT DEFAULT 0,
    create_time DATETIME(3),
    update_time DATETIME(3)
);
