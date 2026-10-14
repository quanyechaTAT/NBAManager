package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.PlayoffMatchup;
import com.nbamanager.repository.PlayoffGameRepository;
import com.nbamanager.repository.PlayoffMatchupRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlayoffServiceTest {

    @Mock
    private PlayoffMatchupRepository matchupRepository;

    @Mock
    private PlayoffGameRepository gameRepository;

    @Mock
    private PlayoffReferenceData playoffReferenceData;

    @InjectMocks
    private PlayoffService playoffService;

    @Test
    void getPlayoffBracketUsesReferenceOnlyWhenDatabaseIsEmpty() {
        PlayoffMatchup stale = new PlayoffMatchup();
        stale.setSeason("2025-26");
        stale.setConference("East");
        stale.setRound(1);
        stale.setTeam1Name("76人");
        stale.setTeam2Name("魔术");
        stale.setTeam1Wins(0);
        stale.setTeam2Wins(0);
        stale.setStatus("IN_PROGRESS");

        when(playoffReferenceData.supports("2025-26")).thenReturn(true);
        when(playoffReferenceData.getMatchups("2025-26")).thenReturn(List.of(
                Map.of(
                        "seriesId", "2026E101",
                        "conference", "East",
                        "round", 1,
                        "team1Name", "活塞",
                        "team1Wins", 4,
                        "team2Name", "魔术",
                        "team2Wins", 3,
                        "winner", "活塞",
                        "status", "COMPLETED"),
                Map.of(
                        "seriesId", "2026F001",
                        "conference", "Finals",
                        "round", 4,
                        "team1Name", "尼克斯",
                        "team1Wins", 4,
                        "team2Name", "马刺",
                        "team2Wins", 1,
                        "winner", "尼克斯",
                        "status", "COMPLETED")));

        Map<String, Object> bracket = playoffService.getPlayoffBracket("2025-26");

        assertThat(bracket.get("season")).isEqualTo("2025-26");
        assertThat((List<?>) bracket.get("eastern"))
                .anySatisfy(m -> {
                    Map<?, ?> matchup = (Map<?, ?>) m;
                    assertThat(matchup.get("team1Name")).isEqualTo("活塞");
                    assertThat(matchup.get("team1Wins")).isEqualTo(4);
                    assertThat(matchup.get("team2Name")).isEqualTo("魔术");
                    assertThat(matchup.get("team2Wins")).isEqualTo(3);
                });
        assertThat((Map<String, Object>) bracket.get("finals"))
                .containsEntry("team1Name", "尼克斯")
                .containsEntry("team2Name", "马刺")
                .containsEntry("team1Wins", 4)
                .containsEntry("team2Wins", 1);
    }

    @Test
    void getPlayoffBracketDisplaysSynchronizedDatabaseSnapshot() {
        PlayoffMatchup row = new PlayoffMatchup();
        row.setSeason("2025-26"); row.setRound(4); row.setConference("Finals");
        row.setTeam1Name("同步球队A"); row.setTeam2Name("同步球队B");
        row.setTeam1Wins(4); row.setTeam2Wins(2); row.setStatus("COMPLETED");
        when(matchupRepository.findBySeasonOrderByRoundAsc("2025-26")).thenReturn(List.of(row));
        assertThat((Map<String, Object>) playoffService.getPlayoffBracket("2025-26").get("finals"))
                .containsEntry("team1Name", "同步球队A").containsEntry("team2Wins", 2);
        org.mockito.Mockito.verifyNoInteractions(playoffReferenceData);
    }
}
