package com.nbamanager.repository;

import com.nbamanager.domain.GameBoxScore;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameBoxScoreRepository extends JpaRepository<GameBoxScore, Long> {

    List<GameBoxScore> findByGameId(String gameId);

    List<GameBoxScore> findByGameIdAndTeamId(String gameId, Long teamId);
}
