package com.nbamanager.service;

import com.nbamanager.domain.PlayByPlaySyncLog;
import com.nbamanager.repository.PlayByPlaySyncLogRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlayByPlaySyncLogService {

    private final PlayByPlaySyncLogRepository repository;

    private static final int MAX_AUTO_RETRIES = 3;

    public Optional<PlayByPlaySyncLog> getLatestLog(String gameId) {
        return repository.findFirstByGameIdOrderByStartedAtDesc(gameId);
    }

    public PlayByPlaySyncLog startSync(String gameId, String source) {
        return startSync(gameId, source, null);
    }

    public PlayByPlaySyncLog startSync(String gameId, String source, String operationId) {
        PlayByPlaySyncLog syncLog = new PlayByPlaySyncLog();
        syncLog.setGameId(gameId);
        syncLog.setSource(source);
        syncLog.setOperationId(operationId);
        syncLog.setStatus("PENDING");
        syncLog.setAttemptCount(1);
        syncLog.setStartedAt(LocalDateTime.now());
        return repository.save(syncLog);
    }

    public PlayByPlaySyncLog logSuccess(PlayByPlaySyncLog syncLog, int eventCount) {
        syncLog.setStatus("SUCCESS");
        syncLog.setEventCount(eventCount);
        syncLog.setCompletedAt(LocalDateTime.now());
        return repository.save(syncLog);
    }

    public PlayByPlaySyncLog logFailure(PlayByPlaySyncLog syncLog, String errorMessage) {
        syncLog.setStatus("FAILED");
        syncLog.setErrorMessage(errorMessage);
        syncLog.setCompletedAt(LocalDateTime.now());
        syncLog.setNextRetryAt(calculateNextRetry(syncLog.getAttemptCount()));
        return repository.save(syncLog);
    }

    public List<PlayByPlaySyncLog> getFailedLogsForRetry() {
        return repository.findByStatusAndNextRetryAtBefore("FAILED", LocalDateTime.now());
    }

    public List<PlayByPlaySyncLog> getRecentSuccessfulLogs(LocalDateTime since) {
        return repository.findByStatusAndStartedAtAfter("SUCCESS", since);
    }

    public LocalDateTime calculateNextRetry(int attempt) {
        return switch (attempt) {
            case 1 -> LocalDateTime.now().plusMinutes(5);
            case 2 -> LocalDateTime.now().plusMinutes(30);
            case 3 -> LocalDateTime.now().plusHours(6);
            default -> null;
        };
    }
}
