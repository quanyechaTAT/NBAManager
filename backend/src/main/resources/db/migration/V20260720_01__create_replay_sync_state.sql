-- Enterprise replay preparation state. Flyway is enabled; the non-idempotent
-- legacy migrations (V1-V3) are archived under db/legacy/.
CREATE TABLE IF NOT EXISTS replay_sync_state (
    id BIGINT NOT NULL AUTO_INCREMENT,
    game_id VARCHAR(16) NOT NULL,
    status VARCHAR(20) NOT NULL,
    quality VARCHAR(20) NOT NULL,
    operation_id VARCHAR(64) NULL,
    owner_instance_id VARCHAR(128) NULL,
    facts_hash VARCHAR(64) NULL,
    source VARCHAR(40) NULL,
    event_count INT NOT NULL DEFAULT 0,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    lease_expires_at DATETIME(6) NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_message VARCHAR(500) NULL,
    last_error_at DATETIME(6) NULL,
    next_retry_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_replay_sync_state_game_id UNIQUE (game_id),
    INDEX idx_replay_sync_state_status (status),
    INDEX idx_replay_sync_state_retry (next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
