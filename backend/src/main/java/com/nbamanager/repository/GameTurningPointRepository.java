package com.nbamanager.repository;

import com.nbamanager.domain.GameTurningPoint;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameTurningPointRepository extends JpaRepository<GameTurningPoint, Long> {
    List<GameTurningPoint> findByGameIdOrderByPeriodAscGameClockDesc(String gameId);
    void deleteByGameId(String gameId);
}
