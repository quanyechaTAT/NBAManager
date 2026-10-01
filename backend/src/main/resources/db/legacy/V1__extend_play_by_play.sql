-- V1__extend_play_by_play.sql
-- 扩展 play_by_play 表，所有变更均为 ADD COLUMN，不修改现有列

ALTER TABLE play_by_play
  ADD COLUMN source VARCHAR(40) DEFAULT 'NBA_PLAY_BY_PLAY',
  ADD COLUMN source_event_id VARCHAR(80),
  ADD COLUMN event_order INT,
  ADD COLUMN action_type VARCHAR(80),
  ADD COLUMN team_name VARCHAR(120),
  ADD COLUMN score_margin INT,
  ADD COLUMN is_scoring_play BOOLEAN DEFAULT FALSE,
  ADD COLUMN is_highlight_candidate BOOLEAN DEFAULT FALSE,
  ADD COLUMN raw_payload_json JSON,
  ADD COLUMN raw_payload_hash VARCHAR(64),
  ADD COLUMN created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

CREATE INDEX idx_pbp_game_order ON play_by_play(game_id, period, event_order);
CREATE INDEX idx_pbp_game_player ON play_by_play(game_id, player_id);
CREATE INDEX idx_pbp_game_type ON play_by_play(game_id, event_type);
CREATE INDEX idx_pbp_source_event ON play_by_play(source, source_event_id);
