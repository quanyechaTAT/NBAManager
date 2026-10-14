-- Additive migration: existing source rows and favorites remain intact.
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'game_news' AND column_name = 'published_at') = 0,
    'ALTER TABLE game_news ADD COLUMN published_at DATETIME(6) NULL', 'SELECT 1');
PREPARE discovery_stmt FROM @ddl; EXECUTE discovery_stmt; DEALLOCATE PREPARE discovery_stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
    AND table_name = 'game_news' AND column_name = 'published_at_source') = 0,
    'ALTER TABLE game_news ADD COLUMN published_at_source VARCHAR(24) NULL', 'SELECT 1');
PREPARE discovery_stmt FROM @ddl; EXECUTE discovery_stmt; DEALLOCATE PREPARE discovery_stmt;
UPDATE game_news SET published_at = COALESCE(game_start_time, create_time),
    published_at_source = CASE WHEN game_start_time IS NULL THEN 'INGESTED' ELSE 'LEGACY_SOURCE' END
    WHERE published_at IS NULL AND COALESCE(category, 'general') NOT IN ('game', 'summer_game');
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE()
    AND table_name = 'game_news' AND index_name = 'idx_news_published') = 0,
    'CREATE INDEX idx_news_published ON game_news(published_at, id)', 'SELECT 1');
PREPARE discovery_stmt FROM @ddl; EXECUTE discovery_stmt; DEALLOCATE PREPARE discovery_stmt;

CREATE TABLE IF NOT EXISTS discovery_preferences (
    user_id BIGINT NOT NULL PRIMARY KEY, personalized BOOLEAN NOT NULL DEFAULT TRUE,
    cleared_at DATETIME(6) NULL, updated_at DATETIME(6) NOT NULL, revision BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS discovery_feedback (
    user_id BIGINT NOT NULL, item_key VARCHAR(80) NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL, PRIMARY KEY(user_id, item_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Only server-issued items/features are used for attribution and point-in-time training.
CREATE TABLE IF NOT EXISTS discovery_requests (
    request_id VARCHAR(64) NOT NULL PRIMARY KEY, user_id BIGINT NOT NULL, scene VARCHAR(32) NOT NULL,
    strategy_version VARCHAR(80) NOT NULL, feature_version VARCHAR(32) NOT NULL, model_version VARCHAR(80) NULL,
    context_json LONGTEXT NOT NULL, items_json LONGTEXT NOT NULL, created_at DATETIME(6) NOT NULL,
    INDEX idx_discovery_request_user(user_id, created_at), INDEX idx_discovery_request_time(created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Durable events are delivered through CDC, without a synchronous Kafka dependency on business writes.
CREATE TABLE IF NOT EXISTS discovery_events (
    event_id VARCHAR(64) NOT NULL PRIMARY KEY, user_id BIGINT NOT NULL, request_id VARCHAR(64) NULL,
    item_key VARCHAR(80) NOT NULL, event_type VARCHAR(32) NOT NULL, position INT NULL,
    dwell_ms INT NOT NULL DEFAULT 0, occurred_at DATETIME(6) NOT NULL, received_at DATETIME(6) NOT NULL,
    client_dedupe_key VARCHAR(192) NULL,
    UNIQUE KEY uq_discovery_client_event(client_dedupe_key),
    INDEX idx_discovery_event_user(user_id, received_at), INDEX idx_discovery_event_request(request_id, item_key),
    INDEX idx_discovery_event_retention(received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS discovery_entity_aliases (
    alias VARCHAR(120) NOT NULL, item_key VARCHAR(80) NOT NULL, PRIMARY KEY(alias, item_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS discovery_models (
    version VARCHAR(80) NOT NULL PRIMARY KEY, status VARCHAR(24) NOT NULL, feature_version VARCHAR(32) NOT NULL,
    artifact_path VARCHAR(500) NOT NULL, artifact_sha256 CHAR(64) NOT NULL, metrics_json LONGTEXT NOT NULL,
    trained_until DATETIME(6) NOT NULL, created_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS discovery_control (
    control_key VARCHAR(64) NOT NULL PRIMARY KEY, value_json LONGTEXT NOT NULL, updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS discovery_jobs (
    id VARCHAR(64) NOT NULL PRIMARY KEY, kind VARCHAR(24) NOT NULL, status VARCHAR(24) NOT NULL,
    requested_by BIGINT NOT NULL, progress INT NOT NULL DEFAULT 0, cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    detail_json LONGTEXT NOT NULL, created_at DATETIME(6) NOT NULL, started_at DATETIME(6) NULL, finished_at DATETIME(6) NULL,
    INDEX idx_discovery_job_pending(status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS discovery_profiles (
    user_id BIGINT NOT NULL PRIMARY KEY, profile_json LONGTEXT NOT NULL, revision BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS discovery_processed_events (
    event_id VARCHAR(64) NOT NULL PRIMARY KEY, processed_at DATETIME(6) NOT NULL,
    INDEX idx_discovery_processed_time(processed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS discovery_index_state (
    item_key VARCHAR(80) NOT NULL PRIMARY KEY, content_hash CHAR(64) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1, deleted BOOLEAN NOT NULL DEFAULT FALSE, updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS discovery_embedding_jobs (
    item_key VARCHAR(80) NOT NULL PRIMARY KEY, content_hash CHAR(64) NOT NULL,
    attempts INT NOT NULL DEFAULT 0, next_attempt DATETIME(6) NOT NULL, last_error VARCHAR(500) NULL,
    INDEX idx_discovery_embedding_pending(next_attempt)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
