package com.nbamanager.repository;

import com.nbamanager.domain.GameHighlight;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameHighlightRepository extends JpaRepository<GameHighlight, Long> {
    List<GameHighlight> findByGameIdOrderByPeriodAscGameClockDesc(String gameId);
    void deleteByGameId(String gameId);
}
