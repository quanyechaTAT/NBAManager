CREATE INDEX idx_gsc_game_id ON game_shot_chart(game_id);
CREATE INDEX idx_gsc_game_team ON game_shot_chart(game_id, team_id);
