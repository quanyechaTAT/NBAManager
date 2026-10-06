package com.nbamanager.repository;

import com.nbamanager.domain.PlayerSeasonStats;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayerSeasonStatsRepository extends JpaRepository<PlayerSeasonStats, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<PlayerSeasonStats> {

    // Legacy snapshots remain readable; their quality and field states are left unchanged.
    String LEGACY = com.nbamanager.service.DataPublication.LEGACY_SQL;
    String STATES = "CASE WHEN JSON_VALID(p.data_field_states) THEN p.data_field_states ELSE '{}' END";
    String GAMES = "p.season=:season AND p.games_played>=10 AND (" + LEGACY
            + " OR (p.data_quality IN ('COMPLETE','PARTIAL') AND JSON_UNQUOTE(JSON_EXTRACT("
            + STATES + ", '$.gamesPlayed')) IN ('OBSERVED','DERIVED')))";
    String POINTS = "(" + LEGACY + " OR JSON_UNQUOTE(JSON_EXTRACT(" + STATES + ", '$.pointsPerGame')) IN ('OBSERVED','DERIVED'))";
    String REBOUNDS = "(" + LEGACY + " OR JSON_UNQUOTE(JSON_EXTRACT(" + STATES + ", '$.reboundsPerGame')) IN ('OBSERVED','DERIVED'))";
    String ASSISTS = "(" + LEGACY + " OR JSON_UNQUOTE(JSON_EXTRACT(" + STATES + ", '$.assistsPerGame')) IN ('OBSERVED','DERIVED'))";
    String STEALS = "(" + LEGACY + " OR JSON_UNQUOTE(JSON_EXTRACT(" + STATES + ", '$.stealsPerGame')) IN ('OBSERVED','DERIVED'))";
    String BLOCKS = "(" + LEGACY + " OR JSON_UNQUOTE(JSON_EXTRACT(" + STATES + ", '$.blocksPerGame')) IN ('OBSERVED','DERIVED'))";
    String FIELDGOALPCT = "(" + LEGACY + " OR JSON_UNQUOTE(JSON_EXTRACT(" + STATES + ", '$.fieldGoalPct')) IN ('OBSERVED','DERIVED'))";
    String THREEPOINTPCT = "(" + LEGACY + " OR JSON_UNQUOTE(JSON_EXTRACT(" + STATES + ", '$.threePointPct')) IN ('OBSERVED','DERIVED'))";

    /** 按球员ID和赛季查找 */
    Optional<PlayerSeasonStats> findByNbaPlayerIdAndSeason(Long nbaPlayerId, String season);

    /** 按赛季查找所有球员 */
    List<PlayerSeasonStats> findBySeasonOrderByPointsPerGameDesc(String season);

    /** 按赛季和球队查找 */
    List<PlayerSeasonStats> findBySeasonAndTeamNameOrderByPointsPerGameDesc(
            String season, String teamName);

    /** 查询球员历史赛季 */
    List<PlayerSeasonStats> findByNbaPlayerIdOrderBySeasonDesc(Long nbaPlayerId);

    /** 查询指定赛季得分榜 */
    @Query(value = "SELECT p.* FROM player_season_stats p WHERE " + GAMES + " AND p.points_per_game IS NOT NULL AND " + POINTS + " ORDER BY p.points_per_game DESC, p.id ASC", nativeQuery = true)
    List<PlayerSeasonStats> findTopScorers(
            @Param("season") String season, Pageable pageable);

    /** 查询指定赛季篮板榜 */
    @Query(value = "SELECT p.* FROM player_season_stats p WHERE " + GAMES + " AND p.rebounds_per_game IS NOT NULL AND " + REBOUNDS + " ORDER BY p.rebounds_per_game DESC, p.id ASC", nativeQuery = true)
    List<PlayerSeasonStats> findTopRebounders(
            @Param("season") String season, Pageable pageable);

    /** 查询指定赛季助攻榜 */
    @Query(value = "SELECT p.* FROM player_season_stats p WHERE " + GAMES + " AND p.assists_per_game IS NOT NULL AND " + ASSISTS + " ORDER BY p.assists_per_game DESC, p.id ASC", nativeQuery = true)
    List<PlayerSeasonStats> findTopAssisters(
            @Param("season") String season, Pageable pageable);

    /** 查询指定赛季抢断榜 */
    @Query(value = "SELECT p.* FROM player_season_stats p WHERE " + GAMES + " AND p.steals_per_game IS NOT NULL AND " + STEALS + " ORDER BY p.steals_per_game DESC, p.id ASC", nativeQuery = true)
    List<PlayerSeasonStats> findTopStealers(
            @Param("season") String season, Pageable pageable);

    /** 查询指定赛季盖帽榜 */
    @Query(value = "SELECT p.* FROM player_season_stats p WHERE " + GAMES + " AND p.blocks_per_game IS NOT NULL AND " + BLOCKS + " ORDER BY p.blocks_per_game DESC, p.id ASC", nativeQuery = true)
    List<PlayerSeasonStats> findTopBlockers(
            @Param("season") String season, Pageable pageable);

    /** 删除指定赛季数据 */
    @Modifying
    @Query("DELETE FROM PlayerSeasonStats p WHERE p.season = :season")
    int deleteBySeason(@Param("season") String season);

    /** 统计指定赛季球员数量 */
    long countBySeason(String season);

    /** 联盟均值/极值聚合投影（接口别名须与 JPQL 别名一致） */
    interface LeagueStatAggregate {
        java.math.BigDecimal getAvgPoints();
        java.math.BigDecimal getAvgRebounds();
        java.math.BigDecimal getAvgAssists();
        java.math.BigDecimal getAvgSteals();
        java.math.BigDecimal getAvgBlocks();
        java.math.BigDecimal getAvgFieldGoalPct();
        java.math.BigDecimal getAvgThreePointPct();
        java.math.BigDecimal getMaxPoints();
        java.math.BigDecimal getMaxRebounds();
        java.math.BigDecimal getMaxAssists();
        java.math.BigDecimal getMaxSteals();
        java.math.BigDecimal getMaxBlocks();
        java.math.BigDecimal getMaxFieldGoalPct();
        java.math.BigDecimal getMaxThreePointPct();
        Long getPlayerCount();
    }

    /** 指定赛季联盟平均与极值（出场 >= 10 场，剔除边缘样本） */
    @Query(value = "SELECT "
            + "AVG(CASE WHEN " + POINTS + " THEN p.points_per_game ELSE NULL END) AS avgPoints, "
            + "AVG(CASE WHEN " + REBOUNDS + " THEN p.rebounds_per_game ELSE NULL END) AS avgRebounds, "
            + "AVG(CASE WHEN " + ASSISTS + " THEN p.assists_per_game ELSE NULL END) AS avgAssists, "
            + "AVG(CASE WHEN " + STEALS + " THEN p.steals_per_game ELSE NULL END) AS avgSteals, "
            + "AVG(CASE WHEN " + BLOCKS + " THEN p.blocks_per_game ELSE NULL END) AS avgBlocks, "
            + "AVG(CASE WHEN " + FIELDGOALPCT + " THEN p.field_goal_pct ELSE NULL END) AS avgFieldGoalPct, "
            + "AVG(CASE WHEN " + THREEPOINTPCT + " THEN p.three_point_pct ELSE NULL END) AS avgThreePointPct, "
            + "MAX(CASE WHEN " + POINTS + " THEN p.points_per_game ELSE NULL END) AS maxPoints, "
            + "MAX(CASE WHEN " + REBOUNDS + " THEN p.rebounds_per_game ELSE NULL END) AS maxRebounds, "
            + "MAX(CASE WHEN " + ASSISTS + " THEN p.assists_per_game ELSE NULL END) AS maxAssists, "
            + "MAX(CASE WHEN " + STEALS + " THEN p.steals_per_game ELSE NULL END) AS maxSteals, "
            + "MAX(CASE WHEN " + BLOCKS + " THEN p.blocks_per_game ELSE NULL END) AS maxBlocks, "
            + "MAX(CASE WHEN " + FIELDGOALPCT + " THEN p.field_goal_pct ELSE NULL END) AS maxFieldGoalPct, "
            + "MAX(CASE WHEN " + THREEPOINTPCT + " THEN p.three_point_pct ELSE NULL END) AS maxThreePointPct, "
            + "COUNT(*) AS playerCount FROM player_season_stats p WHERE " + GAMES, nativeQuery = true)
    LeagueStatAggregate findLeagueAggregate(@Param("season") String season);
}
