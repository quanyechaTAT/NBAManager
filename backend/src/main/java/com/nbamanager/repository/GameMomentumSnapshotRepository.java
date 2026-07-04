package com.nbamanager.repository;

import com.nbamanager.domain.GameMomentumSnapshot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameMomentumSnapshotRepository extends JpaRepository<GameMomentumSnapshot, Long> {
    List<GameMomentumSnapshot> findByGameIdOrderByEventOrderAsc(String gameId);
    void deleteByGameId(String gameId);
}
