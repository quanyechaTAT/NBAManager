package com.nbamanager.service.provider;

import com.nbamanager.domain.provider.PlayByPlayFetchOptions;
import com.nbamanager.domain.provider.PlayByPlayFetchResult;
import com.nbamanager.domain.provider.PlayByPlayProvider;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Manages multiple PlayByPlayProvider instances with failover support.
 * Tries each provider in order, returning the first successful result.
 * If all providers fail, returns an aggregated error result.
 */
@Service
@Slf4j
public class PlayByPlayProviderManager {

    private final List<PlayByPlayProvider> providers;

    public PlayByPlayProviderManager(List<PlayByPlayProvider> providers) {
        this.providers = providers;
        log.info("PlayByPlayProviderManager initialized with {} providers: {}",
                providers.size(),
                providers.stream().map(PlayByPlayProviderManager::providerName).toList());
    }

    /**
     * Fetch play-by-play data for the given game, trying providers in order.
     *
     * @param gameId  the NBA game ID
     * @param options fetch options
     * @return the first successful result, or a failure with aggregated errors if all fail
     */
    public PlayByPlayFetchResult fetch(String gameId, PlayByPlayFetchOptions options) {
        List<String> errors = new ArrayList<>();

        for (PlayByPlayProvider provider : providers) {
            String typeName = providerName(provider);

            if (!provider.supports(gameId)) {
                log.debug("Provider {} does not support gameId={}, skipping", typeName, gameId);
                continue;
            }

            log.info("Trying provider {} for gameId={}", typeName, gameId);
            try {
                PlayByPlayFetchResult result = provider.fetch(gameId, options);
                if (result.success() && result.events() != null && !result.events().isEmpty()) {
                    log.info("Provider {} succeeded for gameId={}, events={}",
                            typeName, gameId,
                            result.events().size());
                    return result;
                }
                String errorMsg = result.success()
                        ? "Provider returned no play-by-play events"
                        : result.errorMessage() != null ? result.errorMessage() : "Unknown error";
                log.warn("Provider {} failed for gameId={}: {}", typeName, gameId, errorMsg);
                errors.add(typeName + ": " + errorMsg);
            } catch (Exception e) {
                log.error("Provider {} threw exception for gameId={}", typeName, gameId, e);
                errors.add(typeName + ": " + e.getMessage());
            }
        }

        String aggregatedError = "All providers failed for gameId=" + gameId + ". Errors: [" +
                String.join("; ", errors) + "]";
        log.error(aggregatedError);

        return PlayByPlayFetchResult.failure(null, aggregatedError, 0);
    }

    private static String providerName(PlayByPlayProvider provider) {
        return provider.type() != null ? provider.type().name() : provider.getClass().getSimpleName();
    }
}
