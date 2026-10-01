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
public interface PlayerSeasonStatsRepository extends JpaRepository<PlayerSeasonStats, Long> {

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
    @Query("SELECT p FROM PlayerSeasonStats p WHERE p.season = :season " +
           "AND p.gamesPlayed >= 10 ORDER BY p.pointsPerGame DESC")
    List<PlayerSeasonStats> findTopScorers(
            @Param("season") String season, Pageable pageable);

    /** 查询指定赛季篮板榜 */
    @Query("SELECT p FROM PlayerSeasonStats p WHERE p.season = :season " +
           "AND p.gamesPlayed >= 10 ORDER BY p.reboundsPerGame DESC")
    List<PlayerSeasonStats> findTopRebounders(
            @Param("season") String season, Pageable pageable);

    /** 查询指定赛季助攻榜 */
    @Query("SELECT p FROM PlayerSeasonStats p WHERE p.season = :season " +
           "AND p.gamesPlayed >= 10 ORDER BY p.assistsPerGame DESC")
    List<PlayerSeasonStats> findTopAssisters(
            @Param("season") String season, Pageable pageable);

    /** 查询指定赛季抢断榜 */
    @Query("SELECT p FROM PlayerSeasonStats p WHERE p.season = :season " +
           "AND p.gamesPlayed >= 10 ORDER BY p.stealsPerGame DESC")
    List<PlayerSeasonStats> findTopStealers(
            @Param("season") String season, Pageable pageable);

    /** 查询指定赛季盖帽榜 */
    @Query("SELECT p FROM PlayerSeasonStats p WHERE p.season = :season " +
           "AND p.gamesPlayed >= 10 ORDER BY p.blocksPerGame DESC")
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
    @Query("SELECT AVG(p.pointsPerGame) AS avgPoints, AVG(p.reboundsPerGame) AS avgRebounds, " +
           "AVG(p.assistsPerGame) AS avgAssists, AVG(p.stealsPerGame) AS avgSteals, " +
           "AVG(p.blocksPerGame) AS avgBlocks, AVG(p.fieldGoalPct) AS avgFieldGoalPct, " +
           "AVG(p.threePointPct) AS avgThreePointPct, " +
           "MAX(p.pointsPerGame) AS maxPoints, MAX(p.reboundsPerGame) AS maxRebounds, " +
           "MAX(p.assistsPerGame) AS maxAssists, MAX(p.stealsPerGame) AS maxSteals, " +
           "MAX(p.blocksPerGame) AS maxBlocks, MAX(p.fieldGoalPct) AS maxFieldGoalPct, " +
           "MAX(p.threePointPct) AS maxThreePointPct, COUNT(p) AS playerCount " +
           "FROM PlayerSeasonStats p WHERE p.season = :season AND p.gamesPlayed >= 10")
    LeagueStatAggregate findLeagueAggregate(@Param("season") String season);
}
