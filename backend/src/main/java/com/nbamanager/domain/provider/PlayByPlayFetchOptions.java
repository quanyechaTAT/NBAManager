package com.nbamanager.domain.provider;

public record PlayByPlayFetchOptions(
        Integer period,
        boolean includeRawPayload
) {
    public static PlayByPlayFetchOptions defaults() {
        return new PlayByPlayFetchOptions(null, true);
    }
}
