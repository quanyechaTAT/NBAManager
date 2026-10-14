package com.nbamanager.repository;

import com.nbamanager.domain.GameNews;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GameNewsRepository extends JpaRepository<GameNews, Long> {

    String RETENTION_ELIGIBLE = "COALESCE(g.category, 'general') NOT IN ('game', 'summer_game') "
            + "AND COALESCE(g.status, 'FINISHED') NOT IN ('SCHEDULED', 'LIVE') "
            + "AND NOT EXISTS (SELECT f.id FROM UserFavorite f WHERE f.targetType = 'NEWS' AND f.targetId = g.id)";

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GameNews g WHERE g.id = :id")
    java.util.Optional<GameNews> findForUpdateById(@Param("id") Long id);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GameNews g WHERE " + RETENTION_ELIGIBLE
            + " AND COALESCE(g.publishedAt, g.createTime) < :cutoff ORDER BY COALESCE(g.publishedAt, g.createTime), g.id")
    List<GameNews> findExpiredRetentionCandidates(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM GameNews g WHERE " + RETENTION_ELIGIBLE
            + " ORDER BY COALESCE(g.publishedAt, g.createTime), g.id")
    List<GameNews> findRetentionCandidates(Pageable pageable);

    @Query("SELECT COUNT(g) FROM GameNews g WHERE " + RETENTION_ELIGIBLE)
    long countRetentionCandidates();

    @Modifying
    @Query("UPDATE GameNews g SET g.favoriteCount = COALESCE(g.favoriteCount, 0) + :delta WHERE g.id = :id")
    int bumpFavoriteCount(@Param("id") Long id, @Param("delta") int delta);


    /** 按标题或主客队搜索 */
    @Query("SELECT g FROM GameNews g WHERE "
            + "g.title LIKE %:q% OR g.homeTeam LIKE %:q% OR g.awayTeam LIKE %:q%")
    Page<GameNews> search(@Param("q") String q, Pageable pageable);

    /** 查询今日赛事（仅比赛类，排除普通新闻与无效记录） */
    @Query("SELECT g FROM GameNews g WHERE g.gameStartTime >= :start AND g.gameStartTime < :end "
            + "AND g.category IN ('game', 'summer_game') "
            + "AND g.nbaGameId IS NOT NULL AND g.nbaGameId <> '' "
            + "AND g.homeTeam IS NOT NULL AND g.homeTeam <> '' AND g.homeTeam NOT IN ('待定', 'TBD', 'tbd') "
            + "AND g.awayTeam IS NOT NULL AND g.awayTeam <> '' AND g.awayTeam NOT IN ('待定', 'TBD', 'tbd') "
            + "ORDER BY g.gameStartTime ASC")
    List<GameNews> findTodayGames(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(g) FROM GameNews g WHERE g.gameStartTime >= :start AND g.gameStartTime < :end "
            + "AND g.category IN ('game', 'summer_game') "
            + "AND g.nbaGameId IS NOT NULL AND g.nbaGameId <> '' "
            + "AND g.homeTeam IS NOT NULL AND g.homeTeam <> '' AND g.homeTeam NOT IN ('待定', 'TBD', 'tbd') "
            + "AND g.awayTeam IS NOT NULL AND g.awayTeam <> '' AND g.awayTeam NOT IN ('待定', 'TBD', 'tbd')")
    long countTodayGames(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

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

    /** 按nbaGameId查找新闻 */
    List<GameNews> findByNbaGameId(String nbaGameId);

    List<GameNews> findBySourceUrlIn(Collection<String> sourceUrls);

    List<GameNews> findByTitleIn(Collection<String> titles);

    List<GameNews> findByTitleEnIn(Collection<String> titles);

    List<GameNews> findByNbaGameIdIn(Collection<String> nbaGameIds);

    @Query("SELECT g.id FROM GameNews g ORDER BY g.createTime ASC, g.id ASC")
    List<Long> findOldestIds(Pageable pageable);

    @Query("SELECT g.sourceUrl FROM GameNews g WHERE g.sourceUrl IS NOT NULL AND g.sourceUrl <> ''")
    List<String> findAllSourceUrls();

    @Query("SELECT CASE WHEN g.titleEn IS NOT NULL AND g.titleEn <> '' THEN g.titleEn ELSE g.title END FROM GameNews g")
    List<String> findAllKnownTitles();

    /** 获取最大 ID（不加载整张表） */
    @org.springframework.data.jpa.repository.Query("SELECT MAX(n.id) FROM GameNews n")
    Long findMaxId();
}
