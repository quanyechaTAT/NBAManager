package com.nbamanager.repository;

import com.nbamanager.domain.TeamSeasonStats;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TeamSeasonStatsRepository extends JpaRepository<TeamSeasonStats, Long> {

    /** 按球队名和赛季查找 */
    Optional<TeamSeasonStats> findByTeamNameAndSeason(String teamName, String season);

    /** 按赛季查找所有球队（按胜场排序） */
    List<TeamSeasonStats> findBySeasonOrderByWinsDesc(String season);

    /** 按赛季和联盟查找 */
    List<TeamSeasonStats> findBySeasonAndConferenceOrderByWinsDesc(
            String season, String conference);

    /** 查询指定赛季联盟前N名 */
    @Query("SELECT t FROM TeamSeasonStats t WHERE t.season = :season " +
           "AND t.conference = :conference ORDER BY t.wins DESC")
    List<TeamSeasonStats> findTopTeamsByConference(
            @Param("season") String season,
            @Param("conference") String conference);

    /** 删除指定赛季数据 */
    @Modifying
    @Query("DELETE FROM TeamSeasonStats t WHERE t.season = :season")
    int deleteBySeason(@Param("season") String season);

    /** 统计指定赛季球队数量 */
    long countBySeason(String season);
}
