package com.nbamanager.repository;

import com.nbamanager.domain.GameNews;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GameNewsRepository extends JpaRepository<GameNews, Long> {

    /** 按标题或主客队搜索 */
    @Query("SELECT g FROM GameNews g WHERE "
            + "g.title LIKE %:q% OR g.homeTeam LIKE %:q% OR g.awayTeam LIKE %:q%")
    Page<GameNews> search(@Param("q") String q, Pageable pageable);

    /** 查询今日赛事（比赛开始时间在今天范围内） */
    @Query("SELECT g FROM GameNews g WHERE g.gameStartTime >= :start AND g.gameStartTime < :end ORDER BY g.gameStartTime ASC")
    List<GameNews> findTodayGames(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /** 统计总数 */
    long count();

    /** 按标题查找新闻 */
    List<GameNews> findByTitle(String title);

    /** 按标题或来源URL查找新闻 */
    @Query("SELECT g FROM GameNews g WHERE g.title = :title OR (g.sourceUrl IS NOT NULL AND g.sourceUrl = :sourceUrl AND g.sourceUrl <> '')")
    List<GameNews> findByTitleOrSourceUrl(@Param("title") String title, @Param("sourceUrl") String sourceUrl);

    /** 按主队+客队+日期查找新闻 */
    @Query("SELECT g FROM GameNews g WHERE g.homeTeam = :homeTeam AND g.awayTeam = :awayTeam " +
            "AND g.gameStartTime IS NOT NULL AND FUNCTION('DATE', g.gameStartTime) = :date")
    List<GameNews> findByHomeTeamAndAwayTeamAndDate(@Param("homeTeam") String homeTeam,
                                                     @Param("awayTeam") String awayTeam,
                                                     @Param("date") LocalDate date);

    /** 查找nbaGameId为空的新闻（且主队不为待定） */
    @Query("SELECT g FROM GameNews g WHERE (g.nbaGameId IS NULL OR g.nbaGameId = '') AND g.homeTeam <> '待定'")
    List<GameNews> findByNbaGameIdIsNullOrNbaGameIdEmpty();
}
