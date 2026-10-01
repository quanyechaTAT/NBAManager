package com.nbamanager.domain.provider;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public record PlayByPlayFetchResult(
        boolean success,
        ProviderType source,
        List<PlayByPlayEvent> events,
        String rawPayloadJson,
        ReplayProviderOutcome outcome,
        ReplayProviderQuality quality,
        String providerSource,
        String errorCode,
        String errorMessage,
        boolean retryable,
        long fetchTimeMs
) {
    public static final String PROVIDER_SCHEMA_VERSION = "replay-provider/v1";

    public static PlayByPlayFetchResult failure(ProviderType source, String error, long timeMs) {
        return failure(source, "PROVIDER_ERROR", error, true, null, timeMs);
    }

    public static PlayByPlayFetchResult failure(
            ProviderType source,
            String errorCode,
            String error,
            boolean retryable,
            String rawJson,
            long timeMs
    ) {
        return new PlayByPlayFetchResult(
                false,
                source,
                List.of(),
                rawJson,
                ReplayProviderOutcome.ERROR,
                ReplayProviderQuality.UNAVAILABLE,
                source != null ? source.name() : null,
                errorCode,
                error,
                retryable,
                timeMs
        );
    }

    public static PlayByPlayFetchResult failure(
            ProviderType source,
            ProviderPayload payload,
            String rawJson,
            long timeMs
    ) {
        if (payload.outcome() == ReplayProviderOutcome.EMPTY) {
            return new PlayByPlayFetchResult(
                    false,
                    source,
                    List.of(),
                    rawJson,
                    ReplayProviderOutcome.EMPTY,
                    ReplayProviderQuality.UNAVAILABLE,
                    payload.providerSource(),
                    null,
                    null,
                    false,
                    timeMs
            );
        }
        return new PlayByPlayFetchResult(
                false,
                source,
                List.of(),
                rawJson,
                ReplayProviderOutcome.ERROR,
                ReplayProviderQuality.UNAVAILABLE,
                payload.providerSource(),
                payload.errorCode(),
                payload.errorMessage(),
                payload.retryable(),
                timeMs
        );
    }

    public static PlayByPlayFetchResult success(ProviderType source, List<PlayByPlayEvent> events,
                                                 String rawJson, long timeMs) {
        return success(source, events, rawJson, ReplayProviderQuality.PARTIAL,
                source != null ? source.name() : null, timeMs);
    }

    public static PlayByPlayFetchResult success(
            ProviderType source,
            List<PlayByPlayEvent> events,
            String rawJson,
            ReplayProviderQuality quality,
            String providerSource,
            long timeMs
    ) {
        List<PlayByPlayEvent> safeEvents = events == null ? List.of() : List.copyOf(events);
        if (safeEvents.isEmpty()) {
            return new PlayByPlayFetchResult(
                    false,
                    source,
                    List.of(),
                    rawJson,
                    ReplayProviderOutcome.EMPTY,
                    ReplayProviderQuality.UNAVAILABLE,
                    providerSource,
                    null,
                    null,
                    false,
                    timeMs
            );
        }
        return new PlayByPlayFetchResult(
                true,
                source,
                safeEvents,
                rawJson,
                ReplayProviderOutcome.SUCCESS,
                quality,
                providerSource,
                null,
                null,
                false,
                timeMs
        );
    }

    /**
     * Parse and validate the versioned Python contract. Legacy non-empty arrays
     * and wrappers remain readable, but are downgraded to PARTIAL quality.
     */
    public static ProviderPayload parseProviderPayload(String json, ProviderType expectedProvider) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Provider output is empty");
        }

        final Object parsed;
        try {
            JSONTokener tokener = new JSONTokener(json.trim());
            parsed = tokener.nextValue();
            if (tokener.nextClean() != 0) {
                throw new IllegalArgumentException("Provider output contains trailing non-JSON content");
            }
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Provider output is not valid JSON", exception);
        }

        if (parsed instanceof JSONArray directEvents) {
            return legacyPayload(directEvents, expectedProvider);
        }
        if (!(parsed instanceof JSONObject root)) {
            throw new IllegalArgumentException("Provider output must be a JSON object or array");
        }

        boolean hasRootContract = root.has("schemaVersion") || root.has("outcome")
                || root.has("quality") || root.has("source") || root.has("errorCode")
                || root.has("errorMessage") || root.has("retryable");
        JSONObject metadata = hasRootContract ? root : root.optJSONObject("playByPlayResult");
        if (metadata == null) {
            JSONArray legacyEvents = firstArray(root, "playByPlay", "events", "play_by_play");
            if (legacyEvents == null) {
                throw new IllegalArgumentException("Provider output has no play-by-play events");
            }
            return legacyPayload(legacyEvents, expectedProvider);
        }

        JSONArray events = hasRootContract
                ? root.optJSONArray("events")
                : root.optJSONArray("playByPlay");
        if (events == null) {
            throw new IllegalArgumentException("Typed provider output must contain events array");
        }
        if (hasRootContract && root.has("playByPlay")) {
            JSONArray compatibilityEvents = root.optJSONArray("playByPlay");
            if (compatibilityEvents == null || !events.similar(compatibilityEvents)) {
                throw new IllegalArgumentException("playByPlay alias must match canonical events array");
            }
        }

        String schemaVersion = requiredString(metadata, "schemaVersion");
        if (!PROVIDER_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported provider schemaVersion: " + schemaVersion);
        }

        ReplayProviderOutcome outcome = parseEnum(
                ReplayProviderOutcome.class, requiredString(metadata, "outcome"), "outcome");
        ReplayProviderQuality quality = parseEnum(
                ReplayProviderQuality.class, requiredString(metadata, "quality"), "quality");
        String providerSource = requiredString(metadata, "source");
        validateProviderSource(expectedProvider, providerSource);

        int eventCount = requiredNonNegativeInt(metadata, "eventCount");
        if (eventCount != events.length()) {
            throw new IllegalArgumentException(
                    "eventCount does not match events length: " + eventCount + " != " + events.length());
        }

        String errorCode = nullableString(metadata, "errorCode");
        String errorMessage = nullableString(metadata, "errorMessage");
        boolean retryable = requiredBoolean(metadata, "retryable");
        validateCombination(outcome, quality, events.length(), errorCode, errorMessage, retryable);

        return new ProviderPayload(
                outcome,
                quality,
                providerSource,
                events,
                errorCode,
                errorMessage,
                retryable,
                false
        );
    }

    private static ProviderPayload legacyPayload(JSONArray events, ProviderType expectedProvider) {
        String source = expectedProvider != null ? expectedProvider.name() : "LEGACY_PLAY_BY_PLAY";
        if (events.isEmpty()) {
            return new ProviderPayload(
                    ReplayProviderOutcome.EMPTY,
                    ReplayProviderQuality.UNAVAILABLE,
                    source,
                    events,
                    null,
                    null,
                    false,
                    true
            );
        }
        return new ProviderPayload(
                ReplayProviderOutcome.SUCCESS,
                ReplayProviderQuality.PARTIAL,
                source,
                events,
                null,
                null,
                false,
                true
        );
    }

    private static JSONArray firstArray(JSONObject root, String... keys) {
        for (String key : keys) {
            JSONArray array = root.optJSONArray(key);
            if (array != null) {
                return array;
            }
        }
        return null;
    }

    private static String requiredString(JSONObject object, String key) {
        if (!object.has(key) || object.isNull(key) || !(object.get(key) instanceof String value)
                || value.isBlank()) {
            throw new IllegalArgumentException("Provider contract field '" + key + "' must be a non-blank string");
        }
        return value;
    }

    private static String nullableString(JSONObject object, String key) {
        if (!object.has(key)) {
            throw new IllegalArgumentException("Provider contract field '" + key + "' is required");
        }
        if (object.isNull(key)) {
            return null;
        }
        Object value = object.get(key);
        if (!(value instanceof String stringValue)) {
            throw new IllegalArgumentException("Provider contract field '" + key + "' must be a string or null");
        }
        return stringValue;
    }

    private static int requiredNonNegativeInt(JSONObject object, String key) {
        if (!object.has(key) || object.isNull(key) || !(object.get(key) instanceof Number number)) {
            throw new IllegalArgumentException("Provider contract field '" + key + "' must be an integer");
        }
        long value = number.longValue();
        if (value < 0 || value > Integer.MAX_VALUE || number.doubleValue() != value) {
            throw new IllegalArgumentException("Provider contract field '" + key + "' must be a non-negative integer");
        }
        return (int) value;
    }

    private static boolean requiredBoolean(JSONObject object, String key) {
        if (!object.has(key) || object.isNull(key) || !(object.get(key) instanceof Boolean value)) {
            throw new IllegalArgumentException("Provider contract field '" + key + "' must be a boolean");
        }
        return value;
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> enumType, String rawValue, String field) {
        try {
            return Enum.valueOf(enumType, rawValue);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unknown provider contract " + field + ": " + rawValue, exception);
        }
    }

    private static void validateProviderSource(ProviderType expectedProvider, String providerSource) {
        if (expectedProvider == ProviderType.NBA_PLAY_BY_PLAY
                && !"NBA_PLAY_BY_PLAY".equals(providerSource)) {
            throw new IllegalArgumentException("NBA provider cannot accept source: " + providerSource);
        }
        if (expectedProvider == ProviderType.ESPN_PLAY_BY_PLAY
                && !"ESPN_PLAY_BY_PLAY".equals(providerSource)
                && !"ESPN_SCOREBOARD_SUMMARY".equals(providerSource)) {
            throw new IllegalArgumentException("ESPN provider cannot accept source: " + providerSource);
        }
    }

    private static void validateCombination(
            ReplayProviderOutcome outcome,
            ReplayProviderQuality quality,
            int eventCount,
            String errorCode,
            String errorMessage,
            boolean retryable
    ) {
        if (outcome == ReplayProviderOutcome.SUCCESS) {
            if (eventCount == 0 || quality == ReplayProviderQuality.UNAVAILABLE) {
                throw new IllegalArgumentException("SUCCESS requires events and an available quality");
            }
            if (errorCode != null || errorMessage != null || retryable) {
                throw new IllegalArgumentException("SUCCESS cannot contain provider error metadata");
            }
            return;
        }

        if (eventCount != 0 || quality != ReplayProviderQuality.UNAVAILABLE) {
            throw new IllegalArgumentException(outcome + " requires no events and UNAVAILABLE quality");
        }
        if (outcome == ReplayProviderOutcome.EMPTY) {
            if (errorCode != null || errorMessage != null || retryable) {
                throw new IllegalArgumentException("EMPTY cannot contain provider error metadata");
            }
            return;
        }
        if (errorCode == null || errorCode.isBlank() || errorMessage == null || errorMessage.isBlank()) {
            throw new IllegalArgumentException("ERROR requires non-blank errorCode and errorMessage");
        }
    }

    public record ProviderPayload(
            ReplayProviderOutcome outcome,
            ReplayProviderQuality quality,
            String providerSource,
            JSONArray events,
            String errorCode,
            String errorMessage,
            boolean retryable,
            boolean legacy
    ) {
    }
}
