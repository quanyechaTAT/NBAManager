package com.nbamanager.repository;

import com.nbamanager.domain.PlayByPlaySyncLog;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayByPlaySyncLogRepository extends JpaRepository<PlayByPlaySyncLog, Long> {
    Optional<PlayByPlaySyncLog> findFirstByGameIdOrderByStartedAtDesc(String gameId);
    List<PlayByPlaySyncLog> findByStatusAndNextRetryAtBefore(String status, LocalDateTime now);
    List<PlayByPlaySyncLog> findByStatusAndStartedAtAfter(String status, LocalDateTime since);
}
