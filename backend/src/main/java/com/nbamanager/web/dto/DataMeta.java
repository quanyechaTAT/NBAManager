package com.nbamanager.web.dto;

import java.time.Instant;
import java.util.Map;

/** Provenance and publication state. Null is distinct from a measured zero. */
public record DataMeta(String source, String sourceUrl, String season, String scope,
                       Instant dataAsOf, Instant syncedAt, String quality, Integer sampleSize,
                       Map<String, String> fieldStates) {
    public static DataMeta unverified() {
        return new DataMeta("UNKNOWN", null, null, null, null, null, "UNVERIFIED", null, Map.of());
    }
    public boolean publishes(String field) {
        if (!("COMPLETE".equals(quality) || "PARTIAL".equals(quality))) return false;
        String state = fieldStates.get(field);
        return "OBSERVED".equals(state) || "DERIVED".equals(state);
    }
}
