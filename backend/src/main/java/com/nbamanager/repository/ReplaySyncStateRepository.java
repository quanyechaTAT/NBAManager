package com.nbamanager.repository;

import com.nbamanager.domain.replay.ReplaySyncState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReplaySyncStateRepository extends JpaRepository<ReplaySyncState, Long> {

    Optional<ReplaySyncState> findByGameId(String gameId);

    boolean existsByGameId(String gameId);
}
