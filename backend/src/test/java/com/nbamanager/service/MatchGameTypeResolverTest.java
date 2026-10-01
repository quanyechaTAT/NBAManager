package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nbamanager.domain.MatchRecord;
import com.nbamanager.web.dto.MatchRecordDto;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MatchGameTypeResolverTest {

    @Test
    void explicitProviderTypeTakesPriority() {
        assertThat(MatchGameTypeResolver.resolve(
                "NBA_CUP", "0022500123", LocalDate.of(2025, 11, 20)))
                .isEqualTo("NBA_CUP");
    }

    @Test
    void resolvesSummerLeagueFromEspnIdAndSummerDate() {
        assertThat(MatchGameTypeResolver.resolve(
                null, "401881722", LocalDate.of(2026, 7, 20)))
                .isEqualTo("SUMMER_LEAGUE");
    }

    @Test
    void resolvesPlayoffsFromOfficialGameId() {
        assertThat(MatchGameTypeResolver.resolve(
                null, "0042500401", LocalDate.of(2026, 6, 10)))
                .isEqualTo("PLAYOFFS");
    }

    @Test
    void resolvesNbaCupFinalFromOfficialGameId() {
        assertThat(MatchGameTypeResolver.resolve(
                null, "0062500001", LocalDate.of(2025, 12, 17)))
                .isEqualTo("NBA_CUP");
    }

    @Test
    void defaultsOfficialRegularSeasonGameToRegularSeason() {
        assertThat(MatchGameTypeResolver.resolve(
                null, "0022500123", LocalDate.of(2025, 11, 20)))
                .isEqualTo("REGULAR_SEASON");
    }

    @Test
    void dtoBackfillsLegacyRowsWithoutPersistedType() {
        MatchRecord match = new MatchRecord();
        match.setId(1L);
        match.setHomeTeam("A");
        match.setAwayTeam("B");
        match.setHomeScore(100);
        match.setAwayScore(90);
        match.setMatchDate(LocalDate.of(2026, 7, 20));
        match.setSeason("2025-26");
        match.setStatus("FINISHED");
        match.setNbaGameId("401881722");

        MatchRecordDto dto = MatchRecordDto.from(match);

        assertThat(dto.gameType()).isEqualTo("SUMMER_LEAGUE");
    }
}
