package com.nbamanager.repository;

import com.nbamanager.domain.MatchRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchRecordRepository extends JpaRepository<MatchRecord, Long> {

    /** 查询某支球队的所有比赛（主场或客场），按日期降序 */
    @Query("SELECT m FROM MatchRecord m WHERE m.homeTeam = :teamName OR m.awayTeam = :teamName ORDER BY m.matchDate DESC")
    List<MatchRecord> findByTeamName(@Param("teamName") String teamName);

    /** 查询两队之间的交锋记录 */
    @Query("SELECT m FROM MatchRecord m WHERE (m.homeTeam = :team1 AND m.awayTeam = :team2) OR (m.homeTeam = :team2 AND m.awayTeam = :team1) ORDER BY m.matchDate DESC")
    List<MatchRecord> findHeadToHead(@Param("team1") String team1, @Param("team2") String team2);

    /** 查询所有比赛，按日期降序 */
    List<MatchRecord> findAllByOrderByMatchDateDesc();

    /** 按日期+主队+客队查找比赛 */
    Optional<MatchRecord> findByHomeTeamAndAwayTeamAndMatchDate(String homeTeam, String awayTeam, LocalDate matchDate);

    /** 按日期+主队+客队判断是否已存在 */
    boolean existsByHomeTeamAndAwayTeamAndMatchDate(String homeTeam, String awayTeam, LocalDate matchDate);

    /** 查找某天所有比赛 */
    List<MatchRecord> findByMatchDate(LocalDate matchDate);
}
