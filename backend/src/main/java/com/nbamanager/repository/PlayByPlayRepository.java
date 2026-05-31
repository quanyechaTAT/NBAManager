package com.nbamanager.repository;

import com.nbamanager.domain.PlayByPlay;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayByPlayRepository extends JpaRepository<PlayByPlay, Long> {

    List<PlayByPlay> findByGameIdOrderByPeriodAscGameClockDesc(String gameId);

    List<PlayByPlay> findByGameIdAndPeriodOrderByGameClockDesc(String gameId, Integer period);
}
