package com.nbamanager.service;

import com.nbamanager.domain.MatchRecord;
import com.nbamanager.domain.PlayByPlay;
import com.nbamanager.domain.PlayByPlaySyncLog;
import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.domain.provider.PlayByPlayFetchResult;
import com.nbamanager.domain.replay.ReplayPageState;
import com.nbamanager.domain.replay.ReplayQualityLevel;
import com.nbamanager.domain.replay.ReplaySyncState;
import com.nbamanager.repository.MatchRecordRepository;
import com.nbamanager.repository.PlayByPlayRepository;
import com.nbamanager.repository.ReplaySyncStateRepository;
import com.nbamanager.service.provider.ReplayOperationStaleException;
import com.nbamanager.web.dto.ReplayQualityDto;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReplayFactsPersistenceService {

    private final PlayByPlayNormalizeService normalizeService;
    private final PlayByPlayValidationService validationService;
    private final PlayByPlaySyncLogService syncLogService;
    private final MomentumComputeService momentumComputeService;
    private final HighlightDetectService highlightDetectService;
    private final TurningPointDetectService turningPointDetectService;
    private final PlayByPlayRepository playByPlayRepository;
    private final MatchRecordRepository matchRecordRepository;
    private final ReplayFactsHashService factsHashService;
    private final ReplayQualityService qualityService;
    private final ReplaySyncStateRepository syncStateRepository;
    private final CacheManager cacheManager;
    private final GameIdMappingService gameIdMappingService;

    @Transactional
    public PersistResult persist(String gameId, PlayByPlayFetchResult fetchResult, String operationId) {
        long startedNanos = System.nanoTime();
        String source = sourceName(fetchResult);
        PlayByPlaySyncLog syncLog = syncLogService.startSync(gameId, source, operationId);

        if (fetchResult == null || !fetchResult.success()) {
            String message = fetchResult == null || fetchResult.errorMessage() == null
                    ? "Replay provider failed"
                    : fetchResult.errorMessage();
            markFailure(syncLog, message, startedNanos);
            failState(gameId, operationId, "REPLAY_SOURCE_UNAVAILABLE", message);
            return new PersistResult(false, source, 0, message, ReplayQualityLevel.UNAVAILABLE.name(), null);
        }

        List<PlayByPlayEvent> providerEvents = fetchResult.events() == null
                ? List.of()
                : List.copyOf(fetchResult.events());
        if (providerEvents.isEmpty()) {
            String message = "No replay events returned";
            markFailure(syncLog, message, startedNanos);
            failState(gameId, operationId, "REPLAY_DATA_REJECTED", message);
            return new PersistResult(false, source, 0, message, ReplayQualityLevel.UNAVAILABLE.name(), null);
        }

        List<PlayByPlayEvent> validEvents = validationService.filterValidEvents(providerEvents);
        if (validEvents == null || validEvents.isEmpty()) {
            String message = "No valid replay events after validation";
            markFailure(syncLog, message, startedNanos);
            failState(gameId, operationId, "REPLAY_DATA_REJECTED", message);
            return new PersistResult(false, source, 0, message, ReplayQualityLevel.UNAVAILABLE.name(), null);
        }

        if (fetchResult.source() == null) {
            String message = "Replay provider source is missing";
            markFailure(syncLog, message, startedNanos);
            failState(gameId, operationId, "REPLAY_DATA_REJECTED", message);
            return new PersistResult(false, source, 0, message, ReplayQualityLevel.UNAVAILABLE.name(), null);
        }

        List<PlayByPlay> normalized = normalizeService.normalizeAll(validEvents, gameId, fetchResult.source());
        String factsHash = factsHashService.hash(normalized);
        List<PlayByPlay> persisted = playByPlayRepository.findByGameIdOrderByEventOrderAsc(gameId);
        MatchRecord match = matchRecordRepository.findByNbaGameId(gameId).orElse(null);
        ReplayQualityDto quality = qualityService.evaluate(normalized, match, LocalDateTime.now());

        if (persisted != null && persisted.size() == normalized.size()
                && Objects.equals(factsHash, factsHashService.hash(persisted))) {
            completeLog(syncLog, validEvents.size(), quality, factsHash, startedNanos, "unchanged");
            completeState(gameId, operationId, quality, factsHash, validEvents.size(), source);
            return new PersistResult(true, source, validEvents.size(), "unchanged",
                    quality.level().name(), factsHash);
        }

        assertCurrentOperation(gameId, operationId);
        playByPlayRepository.deleteByGameId(gameId);
        List<PlayByPlay> saved = playByPlayRepository.saveAll(normalized);
        List<PlayByPlay> savedFacts = saved == null ? normalized : saved;
        evictMatchDetailCaches(gameId);

        momentumComputeService.compute(gameId, validEvents);
        highlightDetectService.detect(gameId, validEvents, savedFacts);
        turningPointDetectService.detect(gameId, validEvents, savedFacts);

        completeLog(syncLog, validEvents.size(), quality, factsHash, startedNanos, "Success");
        completeState(gameId, operationId, quality, factsHash, validEvents.size(), source);
        return new PersistResult(true, source, validEvents.size(), "Success",
                quality.level().name(), factsHash);
    }

    private void evictMatchDetailCaches(String gameId) {
        if (cacheManager == null) {
            return;
        }
        try {
            evictCache("boxScore", gameId);
            evictCache("quarterScores", gameId);
            evictCache("playByPlay", gameId);
            if (gameIdMappingService != null) {
                String espnGameId = gameIdMappingService.getEspnGameId(gameId);
                if (espnGameId != null && !espnGameId.isBlank() && !espnGameId.equals(gameId)) {
                    evictCache("boxScore", espnGameId);
                    evictCache("quarterScores", espnGameId);
                    evictCache("playByPlay", espnGameId);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to evict match detail caches for gameId={}: {}", gameId, e.getMessage());
        }
    }

    private void evictCache(String cacheName, String key) {
        org.springframework.cache.Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }

    private void assertCurrentOperation(String gameId, String operationId) {
        if (operationId == null || operationId.isBlank()) {
            return;
        }
        ReplaySyncState state = syncStateRepository.findByGameId(gameId)
                .orElseThrow(() -> new ReplayOperationStaleException(gameId, operationId));
        if (!operationId.equals(state.getOperationId())) {
            throw new ReplayOperationStaleException(gameId, operationId);
        }
    }

    private void completeLog(PlayByPlaySyncLog syncLog, int eventCount, ReplayQualityDto quality,
                             String factsHash, long startedNanos, String message) {
        syncLog.setQualityLevel(quality.level().name());
        syncLog.setFactsHash(factsHash);
        syncLog.setQualityWarningsJson(new JSONArray(quality.warnings()).toString());
        syncLog.setDurationMs((System.nanoTime() - startedNanos) / 1_000_000L);
        syncLogService.logSuccess(syncLog, eventCount);
        log.info("Replay facts persisted: gameId={}, events={}, quality={}, message={}",
                syncLog.getGameId(), eventCount, quality.level(), message);
    }

    private void markFailure(PlayByPlaySyncLog syncLog, String message, long startedNanos) {
        syncLog.setDurationMs((System.nanoTime() - startedNanos) / 1_000_000L);
        syncLogService.logFailure(syncLog, message);
    }

    private void completeState(String gameId, String operationId, ReplayQualityDto quality,
                               String factsHash, int eventCount, String source) {
        if (operationId == null || operationId.isBlank()) {
            return;
        }
        syncStateRepository.findByGameId(gameId).ifPresent(state -> {
            if (!operationId.equals(state.getOperationId())) {
                throw new ReplayOperationStaleException(gameId, operationId);
            }
            state.setQuality(quality.level());
            state.setStatus(pageStateFor(quality.level()));
            state.setFactsHash(factsHash);
            state.setEventCount(eventCount);
            state.setSource(source);
            state.setCompletedAt(LocalDateTime.now());
            state.setLeaseExpiresAt(null);
            state.setLastErrorCode(null);
            state.setLastErrorMessage(null);
            state.setLastErrorAt(null);
            state.setNextRetryAt(null);
            syncStateRepository.save(state);
        });
    }

    private void failState(String gameId, String operationId, String code, String message) {
        if (operationId == null || operationId.isBlank()) {
            return;
        }
        syncStateRepository.findByGameId(gameId).ifPresent(state -> {
            if (!operationId.equals(state.getOperationId())) {
                throw new ReplayOperationStaleException(gameId, operationId);
            }
            state.setStatus(ReplayPageState.UNAVAILABLE);
            state.setQuality(ReplayQualityLevel.UNAVAILABLE);
            state.setLastErrorCode(code);
            state.setLastErrorMessage(message);
            state.setLastErrorAt(LocalDateTime.now());
            state.setLeaseExpiresAt(null);
            syncStateRepository.save(state);
        });
    }

    private ReplayPageState pageStateFor(ReplayQualityLevel quality) {
        return quality == ReplayQualityLevel.FULL ? ReplayPageState.READY : ReplayPageState.PARTIAL;
    }

    private String sourceName(PlayByPlayFetchResult fetchResult) {
        if (fetchResult == null) {
            return "UNKNOWN";
        }
        if (fetchResult.providerSource() != null && !fetchResult.providerSource().isBlank()) {
            return fetchResult.providerSource();
        }
        return fetchResult.source() == null ? "UNKNOWN" : fetchResult.source().name();
    }

    public record PersistResult(
            boolean success,
            String source,
            int eventCount,
            String message,
            String quality,
            String factsHash) {
    }
}
