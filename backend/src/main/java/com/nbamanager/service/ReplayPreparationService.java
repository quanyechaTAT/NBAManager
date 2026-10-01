package com.nbamanager.service;

import com.nbamanager.domain.MatchRecord;
import com.nbamanager.domain.replay.ReplayPageState;
import com.nbamanager.domain.replay.ReplayQualityLevel;
import com.nbamanager.domain.replay.ReplaySyncState;
import com.nbamanager.repository.MatchRecordRepository;
import com.nbamanager.repository.ReplaySyncStateRepository;
import com.nbamanager.web.dto.ReplayErrorDto;
import com.nbamanager.web.dto.ReplayPreparationDto;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ReplayPreparationService {

    private static final Pattern GAME_ID_PATTERN = Pattern.compile("^[0-9]{9,10}$");
    private static final Duration LEASE = Duration.ofSeconds(90);
    private static final Duration RETRY_BACKOFF = Duration.ofSeconds(30);
    private static final ConcurrentMap<String, Object> LOCKS = new ConcurrentHashMap<>();

    private final PlayByPlayCollectorService collectorService;
    private final MatchRecordRepository matchRecordRepository;
    private final ReplaySyncStateRepository syncStateRepository;
    private final ExecutorService executor;
    private final ConcurrentMap<String, CompletableFuture<Void>> inFlight = new ConcurrentHashMap<>();

    public ReplayPreparationService(
            PlayByPlayCollectorService collectorService,
            MatchRecordRepository matchRecordRepository,
            ReplaySyncStateRepository syncStateRepository,
            @Qualifier("replayPreparationExecutor") ExecutorService executor) {
        this.collectorService = collectorService;
        this.matchRecordRepository = matchRecordRepository;
        this.syncStateRepository = syncStateRepository;
        this.executor = executor;
    }

    public ReplayPreparationDto prepare(String gameId) {
        if (!isValidGameId(gameId)) {
            return new ReplayPreparationDto(
                    gameId == null ? "" : gameId,
                    ReplayPageState.UNAVAILABLE,
                    null,
                    false,
                    null,
                    null,
                    null,
                    error("REPLAY_INVALID_GAME_ID", "The game identifier is invalid.", false, null));
        }

        MatchRecord match = matchRecordRepository.findByNbaGameId(gameId).orElse(null);
        if (match == null) {
            return new ReplayPreparationDto(
                    gameId, ReplayPageState.UNAVAILABLE, null, false, null, null, null,
                    error("REPLAY_NOT_FOUND", "The requested game was not found.", false, null));
        }
        if ("SCHEDULED".equalsIgnoreCase(match.getStatus())) {
            return new ReplayPreparationDto(
                    gameId, ReplayPageState.NOT_STARTED, null, false, null, null, null, null);
        }

        Object lock = LOCKS.computeIfAbsent(gameId, ignored -> new Object());
        synchronized (lock) {
            LocalDateTime now = LocalDateTime.now();
            ReplaySyncState state = syncStateRepository.findByGameId(gameId).orElse(null);
            CompletableFuture<Void> running = inFlight.get(gameId);
            if (running != null && state != null && state.getOperationId() != null) {
                return toDto(state, true, now);
            }
            if (state != null && isActiveLease(state, now)) {
                return toDto(state, true, now);
            }
            if (state != null && isTerminalReady(state) && running == null) {
                return toDto(state, true, now);
            }
            if (state != null && state.getNextRetryAt() != null && state.getNextRetryAt().isAfter(now)) {
                return toDto(state, true, now);
            }

            String operationId = UUID.randomUUID().toString();
            ReplaySyncState claimed = claim(state, gameId, operationId, now);
            try {
                syncStateRepository.save(claimed);
            } catch (DataIntegrityViolationException race) {
                ReplaySyncState winner = syncStateRepository.findByGameId(gameId).orElse(null);
                if (winner != null) {
                    return toDto(winner, true, now);
                }
                throw race;
            }

            CompletableFuture<Void> task = new CompletableFuture<>();
            inFlight.put(gameId, task);
            try {
                executor.execute(() -> runPreparation(gameId, operationId, task));
            } catch (RejectedExecutionException rejected) {
                inFlight.remove(gameId, task);
                markFailure(gameId, operationId, "REPLAY_PREPARATION_BUSY", "Replay preparation capacity is full.");
                ReplaySyncState failed = syncStateRepository.findByGameId(gameId).orElse(claimed);
                return toDto(failed, false, LocalDateTime.now());
            }
            return new ReplayPreparationDto(
                    gameId,
                    ReplayPageState.PREPARING,
                    operationId,
                    false,
                    1,
                    claimed.getStartedAt(),
                    claimed.getLeaseExpiresAt(),
                    null);
        }
    }

    private void runPreparation(String gameId, String operationId, CompletableFuture<Void> task) {
        try {
            PlayByPlayCollectorService.CollectResult result = collectorService.collect(gameId, operationId);
            if (!result.success()) {
                markFailure(gameId, operationId, "REPLAY_SOURCE_UNAVAILABLE", result.message());
            }
            task.complete(null);
        } catch (Throwable failure) {
            log.warn("Replay preparation failed for gameId={}: {}", gameId, failure.getMessage());
            markFailure(gameId, operationId, "REPLAY_PREPARATION_FAILED", safeMessage(failure));
            task.completeExceptionally(failure);
        } finally {
            inFlight.remove(gameId, task);
        }
    }

    private ReplaySyncState claim(ReplaySyncState state, String gameId, String operationId, LocalDateTime now) {
        ReplaySyncState claimed = state == null ? new ReplaySyncState() : state;
        claimed.setGameId(gameId);
        claimed.setStatus(ReplayPageState.PREPARING);
        claimed.setQuality(state == null || state.getQuality() == null
                ? ReplayQualityLevel.UNAVAILABLE : state.getQuality());
        claimed.setOperationId(operationId);
        claimed.setOwnerInstanceId(instanceId());
        claimed.setStartedAt(now);
        claimed.setLeaseExpiresAt(now.plus(LEASE));
        claimed.setCompletedAt(null);
        claimed.setLastErrorCode(null);
        claimed.setLastErrorMessage(null);
        claimed.setLastErrorAt(null);
        claimed.setNextRetryAt(null);
        if (claimed.getEventCount() < 0) {
            claimed.setEventCount(0);
        }
        return claimed;
    }

    private void markFailure(String gameId, String operationId, String code, String message) {
        syncStateRepository.findByGameId(gameId).ifPresent(state -> {
            if (!operationId.equals(state.getOperationId())) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            state.setStatus(ReplayPageState.UNAVAILABLE);
            state.setQuality(ReplayQualityLevel.UNAVAILABLE);
            state.setLastErrorCode(code);
            state.setLastErrorMessage(message);
            state.setLastErrorAt(now);
            state.setNextRetryAt(now.plus(RETRY_BACKOFF));
            state.setLeaseExpiresAt(null);
            state.setCompletedAt(now);
            syncStateRepository.save(state);
        });
    }

    private ReplayPreparationDto toDto(ReplaySyncState state, boolean reused, LocalDateTime now) {
        Integer retryAfter = null;
        if (state.getLeaseExpiresAt() != null && state.getLeaseExpiresAt().isAfter(now)) {
            retryAfter = boundedSeconds(Duration.between(now, state.getLeaseExpiresAt()));
        } else if (state.getNextRetryAt() != null && state.getNextRetryAt().isAfter(now)) {
            retryAfter = boundedSeconds(Duration.between(now, state.getNextRetryAt()));
        }
        ReplayErrorDto error = state.getLastErrorCode() == null ? null : error(
                state.getLastErrorCode(),
                state.getLastErrorMessage(),
                state.getNextRetryAt() != null && state.getNextRetryAt().isAfter(now),
                state.getNextRetryAt());
        return new ReplayPreparationDto(
                state.getGameId(),
                state.getStatus(),
                state.getOperationId(),
                reused,
                retryAfter,
                state.getStartedAt(),
                state.getLeaseExpiresAt(),
                error);
    }

    private boolean isActiveLease(ReplaySyncState state, LocalDateTime now) {
        return state.getStatus() == ReplayPageState.PREPARING
                && state.getOperationId() != null
                && state.getLeaseExpiresAt() != null
                && state.getLeaseExpiresAt().isAfter(now);
    }

    private boolean isTerminalReady(ReplaySyncState state) {
        return state.getStatus() == ReplayPageState.READY
                || state.getStatus() == ReplayPageState.PARTIAL;
    }

    private Integer boundedSeconds(Duration duration) {
        long seconds = Math.max(1L, duration.toSeconds());
        return (int) Math.min(10L, seconds);
    }

    private ReplayErrorDto error(String code, String message, boolean retryable, LocalDateTime nextRetryAt) {
        return new ReplayErrorDto(code, message, retryable, LocalDateTime.now(), nextRetryAt);
    }

    private String safeMessage(Throwable failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? failure.getClass().getSimpleName()
                : failure.getMessage();
    }

    private String instanceId() {
        return System.getProperty("HOSTNAME", "local-replay-instance");
    }

    private boolean isValidGameId(String gameId) {
        return gameId != null && GAME_ID_PATTERN.matcher(gameId).matches();
    }
}
