package com.nbamanager.service;

import com.nbamanager.domain.provider.PlayByPlayFetchOptions;
import com.nbamanager.domain.provider.PlayByPlayFetchResult;
import com.nbamanager.service.provider.PlayByPlayProviderManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlayByPlayCollectorService {

    private final PlayByPlayProviderManager providerManager;
    private final ReplayFactsPersistenceService persistenceService;

    public CollectResult collect(String gameId) {
        return collect(gameId, null);
    }

    /**
     * Fetches outside the database transaction. The persistence service owns the
     * atomic fact/derived-data replacement and operation fencing.
     */
    public CollectResult collect(String gameId, String operationId) {
        PlayByPlayFetchResult fetchResult = providerManager.fetch(
                gameId, PlayByPlayFetchOptions.defaults());
        if (fetchResult == null || !fetchResult.success()) {
            String source = fetchResult == null || fetchResult.source() == null
                    ? "UNKNOWN"
                    : fetchResult.source().name();
            String message = fetchResult == null || fetchResult.errorMessage() == null
                    ? "Replay provider failed"
                    : fetchResult.errorMessage();
            return new CollectResult(false, source, 0, message, "UNAVAILABLE", null);
        }

        ReplayFactsPersistenceService.PersistResult result = persistenceService.persist(
                gameId, fetchResult, operationId);
        return new CollectResult(
                result.success(),
                result.source(),
                result.eventCount(),
                result.message(),
                result.quality(),
                result.factsHash());
    }

    public record CollectResult(
            boolean success,
            String source,
            int eventCount,
            String message,
            String quality,
            String factsHash) {

        public CollectResult(boolean success, String source, int eventCount, String message) {
            this(success, source, eventCount, message, null, null);
        }
    }
}
