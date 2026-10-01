package com.nbamanager.repository;

import com.nbamanager.domain.PlayByPlay;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayByPlayRepository extends JpaRepository<PlayByPlay, Long> {

    long countByGameId(String gameId);

    void deleteByGameId(String gameId);

    Optional<PlayByPlay> findByIdAndGameId(Long id, String gameId);

    List<PlayByPlay> findByGameIdOrderByPeriodAscEventOrderDesc(String gameId);

    List<PlayByPlay> findByGameIdAndPeriodOrderByEventOrderDesc(String gameId, Integer period);

    List<PlayByPlay> findByGameIdOrderByEventOrderAsc(String gameId);

    List<PlayByPlay> findByGameIdAndPeriodOrderByEventOrderAsc(String gameId, Integer period);

    // Keep old methods for backward compatibility
    List<PlayByPlay> findByGameIdOrderByPeriodAscGameClockDesc(String gameId);
    List<PlayByPlay> findByGameIdAndPeriodOrderByGameClockDesc(String gameId, Integer period);
}
