package com.nbamanager.repository;

import com.nbamanager.domain.GameNews;
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

    /** 查询今日赛事（比赛时间在今天范围内） */
    @Query("SELECT g FROM GameNews g WHERE g.gameTime >= :start AND g.gameTime < :end ORDER BY g.gameTime ASC")
    List<GameNews> findTodayGames(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
