package com.nbamanager.domain.provider;

import java.util.List;

public record PlayByPlayFetchResult(
        boolean success,
        ProviderType source,
        List<PlayByPlayEvent> events,
        String rawPayloadJson,
        String errorMessage,
        long fetchTimeMs
) {
    public static PlayByPlayFetchResult failure(ProviderType source, String error, long timeMs) {
        return new PlayByPlayFetchResult(false, source, List.of(), null, error, timeMs);
    }

    public static PlayByPlayFetchResult success(ProviderType source, List<PlayByPlayEvent> events,
                                                 String rawJson, long timeMs) {
        return new PlayByPlayFetchResult(true, source, events, rawJson, null, timeMs);
    }
}
