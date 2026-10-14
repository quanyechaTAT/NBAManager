package com.nbamanager.config;

import static org.assertj.core.api.Assertions.assertThat;
import com.nbamanager.web.dto.MatchRecordDto;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchCacheTtlTest {
    private final MatchCacheTtl ttl = new MatchCacheTtl(Clock.fixed(Instant.parse("2026-10-06T16:30:00Z"), ZoneOffset.UTC));

    private MatchRecordDto match(String status, String date) {
        return new MatchRecordDto(1L, "马刺", "尼克斯", 100, 101,
                date == null ? null : LocalDate.parse(date), "2026-27", status, "0022600001", "REGULAR_SEASON");
    }

    @Test void liveGamesAndListsContainingThemHaveShortLifetimes() {
        var live = match("LIVE", "2026-10-07");
        assertThat(ttl.getTimeToLive("game", live)).isEqualTo(Duration.ofSeconds(5));
        assertThat(ttl.getTimeToLive("all", List.of(match("FINISHED", "2020-01-01"), live)))
                .isEqualTo(Duration.ofSeconds(5));
    }

    @Test void scheduledGamesNearShanghaiTodayRefreshMoreOften() {
        assertThat(ttl.getTimeToLive("game", match("SCHEDULED", "2026-10-08"))).isEqualTo(Duration.ofSeconds(30));
        assertThat(ttl.getTimeToLive("game", match("SCHEDULED", "2026-10-09"))).isEqualTo(Duration.ofSeconds(300));
    }

    @Test void recentResultsStayShortAndStableHistoryCanBeReused() {
        assertThat(ttl.getTimeToLive("game", match("FINISHED", "2026-10-05"))).isEqualTo(Duration.ofSeconds(60));
        assertThat(ttl.getTimeToLive("game", match("FINISHED", "2020-01-01"))).isEqualTo(Duration.ofHours(6));
    }

    @Test void emptyUnknownAndUndatedResultsDoNotBecomeLongLived() {
        assertThat(ttl.getTimeToLive("all", List.of())).isEqualTo(Duration.ofSeconds(5));
        assertThat(ttl.getTimeToLive("game", match("UNKNOWN", "2020-01-01"))).isEqualTo(Duration.ofSeconds(5));
        assertThat(ttl.getTimeToLive("game", match("FINISHED", null))).isEqualTo(Duration.ofSeconds(5));
    }
}
