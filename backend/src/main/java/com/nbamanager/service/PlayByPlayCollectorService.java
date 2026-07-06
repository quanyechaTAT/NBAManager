package com.nbamanager.service;

import com.nbamanager.domain.PlayByPlay;
import com.nbamanager.domain.PlayByPlaySyncLog;
import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.domain.provider.PlayByPlayFetchOptions;
import com.nbamanager.domain.provider.PlayByPlayFetchResult;
import com.nbamanager.domain.provider.ProviderType;
import com.nbamanager.repository.PlayByPlayRepository;
import com.nbamanager.service.provider.PlayByPlayProviderManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlayByPlayCollectorService {

    private final PlayByPlayProviderManager providerManager;
    private final PlayByPlayNormalizeService normalizeService;
    private final PlayByPlayValidationService validationService;
    private final PlayByPlaySyncLogService syncLogService;
    private final MomentumComputeService momentumComputeService;
    private final HighlightDetectService highlightDetectService;
    private final TurningPointDetectService turningPointDetectService;
    private final PlayByPlayRepository playByPlayRepository;

    public record CollectResult(boolean success, String source, int eventCount, String message) {
    }

    @Transactional
    public CollectResult collect(String gameId) {
        // 1. Check cache
        if (syncLogService.hasSuccessfulSync(gameId)) {
            log.info("Game {} already has successful sync, returning cached result", gameId);
            return new CollectResult(true, "cached", 0, "cached");
        }

        // 2. Fetch from provider
        PlayByPlayFetchResult fetchResult = providerManager.fetch(gameId, PlayByPlayFetchOptions.defaults());
        String sourceName = fetchResult.source() != null ? fetchResult.source().name() : "UNKNOWN";

        // 3. Start sync log
        PlayByPlaySyncLog syncLog = syncLogService.startSync(gameId, sourceName);

        // 4. Check fetch failure
        if (!fetchResult.success()) {
            String errorMsg = fetchResult.errorMessage() != null ? fetchResult.errorMessage() : "Fetch failed";
            syncLogService.logFailure(syncLog, errorMsg);
            log.error("Play-by-play fetch failed for gameId={}: {}", gameId, errorMsg);
            return new CollectResult(false, sourceName, 0, errorMsg);
        }

        List<PlayByPlayEvent> events = fetchResult.events();
        if (events == null || events.isEmpty()) {
            syncLogService.logFailure(syncLog, "No events returned");
            return new CollectResult(false, sourceName, 0, "No events returned");
        }

        // 5. Filter valid events (drop events with invalid format, don't reject whole batch)
        List<PlayByPlayEvent> validEvents = validationService.filterValidEvents(events);
        if (validEvents.isEmpty()) {
            syncLogService.logFailure(syncLog, "No valid events after filtering");
            return new CollectResult(false, sourceName, 0, "No valid events after filtering");
        }
        log.info("Filtered {}/{} valid events for gameId={}", validEvents.size(), events.size(), gameId);

        // 6. Normalize
        List<PlayByPlay> entities = normalizeService.normalizeAll(validEvents, gameId, fetchResult.source());
        log.info("Normalized {} entities for gameId={}", entities.size(), gameId);

        // 7. Save to repository
        playByPlayRepository.saveAll(entities);
        log.info("Saved {} PlayByPlay records for gameId={}", entities.size(), gameId);

        // 8. Compute momentum
        momentumComputeService.compute(gameId, validEvents);
        log.info("Computed momentum for gameId={}", gameId);

        // 9. Detect highlights
        highlightDetectService.detect(gameId, validEvents);
        log.info("Detected highlights for gameId={}", gameId);

        // 10. Detect turning points
        turningPointDetectService.detect(gameId, validEvents);
        log.info("Detected turning points for gameId={}", gameId);

        // 11. Log success
        syncLogService.logSuccess(syncLog, validEvents.size());

        log.info("Play-by-play collection completed for gameId={}: source={}, events={}",
                gameId, sourceName, validEvents.size());

        // 12. Return success
        return new CollectResult(true, sourceName, validEvents.size(), "Success");
    }
}
