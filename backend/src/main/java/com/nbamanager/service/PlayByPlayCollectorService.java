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

        // 5. Validate
        PlayByPlayValidationService.ValidationResult validation = validationService.validate(events);
        if (!validation.valid()) {
            String errors = String.join("; ", validation.errors());
            syncLogService.logFailure(syncLog, "Validation failed: " + errors);
            log.error("Play-by-play validation failed for gameId={}: {}", gameId, errors);
            return new CollectResult(false, sourceName, events.size(), "Validation failed: " + errors);
        }

        // 6. Normalize
        List<PlayByPlay> entities = normalizeService.normalizeAll(events, gameId, fetchResult.source());

        // 7. Save to repository
        playByPlayRepository.saveAll(entities);

        // 8. Compute momentum
        momentumComputeService.compute(gameId, events);

        // 9. Detect highlights
        highlightDetectService.detect(gameId, events);

        // 10. Detect turning points
        turningPointDetectService.detect(gameId, events);

        // 11. Log success
        syncLogService.logSuccess(syncLog, events.size());

        log.info("Play-by-play collection completed for gameId={}: source={}, events={}",
                gameId, sourceName, events.size());

        // 12. Return success
        return new CollectResult(true, sourceName, events.size(), "Success");
    }
}
