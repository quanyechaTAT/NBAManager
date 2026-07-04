package com.nbamanager.domain.provider;

public interface PlayByPlayProvider {
    ProviderType type();
    boolean supports(String gameId);
    PlayByPlayFetchResult fetch(String gameId, PlayByPlayFetchOptions options);
}
