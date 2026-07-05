package com.nbamanager.repository;

import com.nbamanager.domain.GameShotChart;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameShotChartRepository extends JpaRepository<GameShotChart, Long> {
    List<GameShotChart> findByGameId(String gameId);
    void deleteByGameId(String gameId);
}
