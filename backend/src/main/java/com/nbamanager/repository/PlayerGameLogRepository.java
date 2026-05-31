package com.nbamanager.repository;

import com.nbamanager.domain.PlayerGameLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerGameLogRepository extends JpaRepository<PlayerGameLog, Long> {

    Page<PlayerGameLog> findByPlayerIdAndSeasonOrderByMatchDateDesc(Long playerId, String season, Pageable pageable);

    Page<PlayerGameLog> findByPlayerIdOrderByMatchDateDesc(Long playerId, Pageable pageable);
}
