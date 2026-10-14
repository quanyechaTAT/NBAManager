package com.nbamanager.discovery;

import com.nbamanager.exception.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

public final class DiscoveryTypes {
    private DiscoveryTypes() {}
    public static final String FEATURE_VERSION = "discovery-v1";
    public static final Set<String> TYPES = Set.of("PLAYER", "TEAM", "MATCH", "NEWS", "POST");
    public static final Set<String> SCENES = Set.of("HOME", "NEWS_FEED", "COMMUNITY_FEED", "NEWS_DETAIL", "POST_DETAIL", "SEARCH");

    public record Key(String type, long id) {
        public static Key parse(String value) {
            if (value == null || !value.matches("(PLAYER|TEAM|MATCH|NEWS|POST):[1-9][0-9]{0,18}")) throw bad("内容标识无效");
            String[] parts = value.split(":", 2);
            try { return new Key(parts[0], Long.parseLong(parts[1])); }
            catch (NumberFormatException error) { throw bad("内容标识超出范围"); }
        }
        @Override public String toString() { return type + ":" + id; }
    }
    public record Target(String path, Map<String, String> query) {}
    public record Item(String itemKey, String type, long id, String title, String summary,
                       String imageUrl, String publishedAt, String reason, Target target) {}
    public record Response(String requestId, List<Item> items, String nextCursor, String strategyVersion,
                           boolean degraded) {}
    public record Preferences(boolean personalized, long revision, String clearedAt) {}
    public record PreferenceUpdate(@NotNull Boolean personalized) {}
    public record Feedback(@NotBlank String itemKey, boolean active) {}
    public record Event(@NotBlank @Size(max = 64) String eventId, @NotBlank @Size(max = 64) String requestId,
                        @NotBlank @Size(max = 80) String itemKey, @NotBlank String eventType,
                        @Min(0) @Max(99) int position, @Min(0) @Max(1800000) int dwellMs,
                        @NotNull Instant occurredAt) {}
    public record EventBatch(@NotNull @Size(min = 1, max = 100) List<@Valid Event> events) {}
    public record EventReceipt(int accepted, int duplicates) {}
    public static ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
}
