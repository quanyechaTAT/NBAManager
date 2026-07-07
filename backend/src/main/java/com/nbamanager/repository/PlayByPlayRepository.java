package com.nbamanager.repository;

import com.nbamanager.domain.PlayByPlay;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayByPlayRepository extends JpaRepository<PlayByPlay, Long> {

    List<PlayByPlay> findByGameIdOrderByPeriodAscEventOrderDesc(String gameId);

    List<PlayByPlay> findByGameIdAndPeriodOrderByEventOrderDesc(String gameId, Integer period);

    // Keep old methods for backward compatibility
    List<PlayByPlay> findByGameIdOrderByPeriodAscGameClockDesc(String gameId);
    List<PlayByPlay> findByGameIdAndPeriodOrderByGameClockDesc(String gameId, Integer period);
}
