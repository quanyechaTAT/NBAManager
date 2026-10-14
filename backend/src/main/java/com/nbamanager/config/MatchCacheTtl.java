package com.nbamanager.config;

import com.nbamanager.web.dto.MatchRecordDto;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.data.redis.cache.RedisCacheWriter;

/** Match lists inherit the shortest lifetime of their members, so live games stay fresh. */
public final class MatchCacheTtl implements RedisCacheWriter.TtlFunction {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final Clock clock;

    public MatchCacheTtl() {
        this(Clock.system(ZONE));
    }

    MatchCacheTtl(Clock clock) {
        this.clock = clock.withZone(ZONE);
    }

    @Override
    public Duration getTimeToLive(Object key, Object value) {
        if (value instanceof MatchRecordDto match) return lifetime(match);
        if (value instanceof Iterable<?> matches) {
            Duration shortest = null;
            for (Object item : matches) {
                Duration ttl = item instanceof MatchRecordDto match ? lifetime(match) : Duration.ofSeconds(5);
                if (shortest == null || ttl.compareTo(shortest) < 0) shortest = ttl;
            }
            return shortest == null ? Duration.ofSeconds(5) : shortest;
        }
        return Duration.ofSeconds(5);
    }

    private Duration lifetime(MatchRecordDto match) {
        LocalDate today = LocalDate.now(clock);
        if ("LIVE".equalsIgnoreCase(match.status())) return Duration.ofSeconds(5);
        if ("SCHEDULED".equalsIgnoreCase(match.status())) {
            return Duration.ofSeconds(match.matchDate() != null && match.matchDate().isAfter(today.plusDays(1)) ? 300 : 30);
        }
        if ("FINISHED".equalsIgnoreCase(match.status()) && match.matchDate() != null) {
            return match.matchDate().isBefore(today.minusDays(2)) ? Duration.ofHours(6) : Duration.ofSeconds(60);
        }
        return Duration.ofSeconds(5);
    }
}
