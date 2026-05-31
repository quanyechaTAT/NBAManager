package com.nbamanager.repository;

import com.nbamanager.domain.PlayerCareerStats;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerCareerStatsRepository extends JpaRepository<PlayerCareerStats, Long> {

    List<PlayerCareerStats> findByPlayerIdOrderBySeasonAsc(Long playerId);
}
