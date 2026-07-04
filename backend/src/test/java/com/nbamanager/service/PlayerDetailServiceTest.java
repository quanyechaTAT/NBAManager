package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.MatchRecord;
import com.nbamanager.domain.Player;
import com.nbamanager.domain.PlayerGameLog;
import com.nbamanager.domain.Team;
import com.nbamanager.repository.MatchRecordRepository;
import com.nbamanager.repository.PlayerCareerStatsRepository;
import com.nbamanager.repository.PlayerGameLogRepository;
import com.nbamanager.repository.PlayerRepository;
import com.nbamanager.repository.TeamRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class PlayerDetailServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private PlayerGameLogRepository playerGameLogRepository;

    @Mock
    private PlayerCareerStatsRepository playerCareerStatsRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private MatchRecordRepository matchRecordRepository;

    @Test
    void getGameLogMergesScoresFromMatchingGameRecord() {
        Team team = new Team();
        team.setName("Lakers");

        Player player = new Player();
        player.setId(7L);
        player.setTeam(team);

        PlayerGameLog log = new PlayerGameLog();
        log.setPlayerId(7L);
        log.setGameId("0022500001");
        log.setMatchDate("2026-01-02");
        log.setOpponent("Celtics");
        log.setHome(true);
        log.setMinutes("32");
        log.setPoints(24);
        log.setRebounds(7);
        log.setAssists(5);
        log.setSteals(1);
        log.setBlocks(0);
        log.setTurnovers(2);
        log.setFgPct(0.5);
        log.setThreePct(0.4);
        log.setFtPct(0.8);
        log.setPlusMinus(3);
        log.setResult("W");
        log.setSeason("2025-26");

        MatchRecord match = new MatchRecord();
        match.setNbaGameId("0022500001");
        match.setHomeTeam("Lakers");
        match.setAwayTeam("Celtics");
        match.setHomeScore(112);
        match.setAwayScore(105);
        match.setMatchDate(LocalDate.of(2026, 1, 2));
        match.setSeason("2025-26");
        match.setStatus("FINISHED");

        PageRequest pageable = PageRequest.of(0, 10);
        when(playerRepository.findById(7L)).thenReturn(Optional.of(player));
        when(matchRecordRepository.findByTeamName("Lakers")).thenReturn(List.of());
        when(playerGameLogRepository.findByPlayerIdOrderByMatchDateDesc(7L, pageable))
                .thenReturn(new PageImpl<>(List.of(log), pageable, 1));
        when(matchRecordRepository.findByNbaGameIdIn(List.of("0022500001"))).thenReturn(List.of(match));

        PlayerDetailService service = new PlayerDetailService(
                playerRepository,
                playerGameLogRepository,
                playerCareerStatsRepository,
                teamRepository,
                matchRecordRepository);

        var result = service.getGameLog(7L, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).teamScore()).isEqualTo(112);
        assertThat(result.getContent().get(0).opponentScore()).isEqualTo(105);
    }

    @Test
    void parseEspnScoreboardGamesMapsTeamAbbreviationsAndScores() {
        String json = """
                {
                  "events": [{
                    "competitions": [{
                      "status": { "type": { "name": "STATUS_FINAL" } },
                      "competitors": [
                        {
                          "homeAway": "home",
                          "score": "139",
                          "team": { "abbreviation": "TOR", "shortDisplayName": "Raptors" }
                        },
                        {
                          "homeAway": "away",
                          "score": "87",
                          "team": { "abbreviation": "ORL", "shortDisplayName": "Magic" }
                        }
                      ]
                    }]
                  }]
                }
                """;

        var games = PlayerDetailService.parseEspnScoreboardGames(json);

        assertThat(games).hasSize(1);
        assertThat(games.get(0).homeTeam()).isEqualTo("猛龙");
        assertThat(games.get(0).awayTeam()).isEqualTo("魔术");
        assertThat(games.get(0).homeScore()).isEqualTo(139);
        assertThat(games.get(0).awayScore()).isEqualTo(87);
        assertThat(games.get(0).status()).isEqualTo("FINISHED");
    }
}
