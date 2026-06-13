package com.nbamanager.repository;

import com.nbamanager.domain.PlayerShotChart;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayerShotChartRepository extends JpaRepository<PlayerShotChart, Long> {

    /** 查询球员指定赛季的投篮数据 */
    List<PlayerShotChart> findByNbaPlayerIdAndSeason(Long nbaPlayerId, String season);

    /** 查询球员最近N场比赛的投篮数据 */
    @Query("SELECT s FROM PlayerShotChart s WHERE s.nbaPlayerId = :playerId AND s.season = :season ORDER BY s.gameDate DESC")
    List<PlayerShotChart> findRecentShots(@Param("playerId") Long playerId, @Param("season") String season);

    /** 统计球员指定赛季的投篮数据数量 */
    long countByNbaPlayerIdAndSeason(Long nbaPlayerId, String season);

    /** 删除球员指定赛季的投篮数据 */
    void deleteByNbaPlayerIdAndSeason(Long nbaPlayerId, String season);
}
