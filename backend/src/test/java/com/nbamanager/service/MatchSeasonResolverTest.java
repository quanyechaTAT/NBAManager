package com.nbamanager.service;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class MatchSeasonResolverTest {
    @Test void usesActualGameSeasonRatherThanTodaysSeason() {
        assertThat(MatchSeasonResolver.resolve(null, "0022500001", LocalDate.of(2026, 10, 2))).isEqualTo("2025-26");
        assertThat(MatchSeasonResolver.resolve(null, "401810936", LocalDate.of(2024, 3, 2))).isEqualTo("2023-24");
        assertThat(MatchSeasonResolver.resolve("2022-23", "0022500001", LocalDate.of(2026, 10, 2))).isEqualTo("2022-23");
        assertThat(MatchSeasonResolver.resolve(null, null, null)).isNull();
    }
}
