-- Add stage column to sync_jobs for unified sync tracking
ALTER TABLE sync_jobs ADD COLUMN stage VARCHAR(32) NULL AFTER progress;

-- Create sync_job_steps table for full sync module steps
CREATE TABLE IF NOT EXISTS sync_job_steps (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_id BIGINT NOT NULL,
    module VARCHAR(32) NOT NULL,
    step_order INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    progress INT NOT NULL DEFAULT 0,
    stage VARCHAR(32) NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    data_as_of DATETIME(6) NULL,
    source VARCHAR(32) NULL,
    fetched_count INT NOT NULL DEFAULT 0,
    inserted_count INT NOT NULL DEFAULT 0,
    updated_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    error_code VARCHAR(32) NULL,
    error_message TEXT NULL,
    version BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_sync_step_job FOREIGN KEY (job_id) REFERENCES sync_jobs(id) ON DELETE CASCADE,
    CONSTRAINT uk_sync_step_job_module UNIQUE (job_id, module),
    INDEX idx_sync_step_job (job_id, step_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
