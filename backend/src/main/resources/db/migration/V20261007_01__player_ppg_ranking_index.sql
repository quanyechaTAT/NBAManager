-- Database paging orders by PPG DESC and ID ASC; the ID is a stable tie-breaker.
-- Match the mixed direction order on MySQL 8 instead of sorting the entire table.
SET @index_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'players'
      AND INDEX_NAME = 'idx_player_ppg_rank'
);
SET @ddl = IF(@index_exists = 0,
    'CREATE INDEX idx_player_ppg_rank ON players (points_per_game DESC, id ASC)',
    'SELECT 1');
PREPARE ranking_index_stmt FROM @ddl;
EXECUTE ranking_index_stmt;
DEALLOCATE PREPARE ranking_index_stmt;
