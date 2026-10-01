package com.nbamanager.service;

import com.nbamanager.config.PythonTimeoutProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.GameBoxScore;
import com.nbamanager.domain.MatchRecord;
import com.nbamanager.domain.PlayByPlay;
import com.nbamanager.repository.GameBoxScoreRepository;
import com.nbamanager.repository.GameNewsRepository;
import com.nbamanager.repository.MatchRecordRepository;
import com.nbamanager.repository.PlayByPlayRepository;
import com.nbamanager.repository.PlayerRepository;
import com.nbamanager.web.dto.BoxScoreDto;
import com.nbamanager.web.dto.PlayByPlayDto;
import com.nbamanager.web.dto.QuarterScoreDto;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchDetailServiceTest {

    @Mock
    private GameBoxScoreRepository gameBoxScoreRepository;

    @Mock
    private PlayByPlayRepository playByPlayRepository;

    @Mock
    private GameIdMappingService gameIdMappingService;

    @Mock
    private MatchRecordRepository matchRecordRepository;

    @Mock
    private GameNewsRepository gameNewsRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private com.nbamanager.service.PythonScriptRunner pythonScriptRunner;

    @BeforeEach
    void setUpPythonFailure() {
        lenient().when(pythonScriptRunner.run(anyString(), any(String[].class), anyInt()))
                .thenReturn(new PythonScriptRunner.PythonResult(-1, "", "Python unavailable", false, 1));
    }

    @Test
    void getBoxScore_populatesScoresFromMatchRecord() {
        String gameId = "0022500001";

        MatchRecord match = new MatchRecord();
        match.setNbaGameId(gameId);
        match.setHomeTeam("Lakers");
        match.setAwayTeam("Celtics");
        match.setHomeScore(112);
        match.setAwayScore(105);
        match.setMatchDate(LocalDate.of(2026, 1, 2));
        match.setStatus("FINISHED");

        when(gameIdMappingService.getNbaGameId(gameId)).thenReturn(gameId);
        when(gameBoxScoreRepository.findByGameId(gameId)).thenReturn(Collections.emptyList());
        when(matchRecordRepository.findByNbaGameId(gameId)).thenReturn(Optional.of(match));

        MatchDetailService service = new MatchDetailService(
                gameBoxScoreRepository,
                playByPlayRepository,
                gameIdMappingService,
                matchRecordRepository,
                gameNewsRepository,
                pythonScriptRunner,
                playerRepository,
                new PythonTimeoutProperties());

        BoxScoreDto result = service.getBoxScore(gameId);

        assertThat(result.homeScore()).isEqualTo(112);
        assertThat(result.awayScore()).isEqualTo(105);
        verifyNoInteractions(pythonScriptRunner);
    }

    @Test
    void getPlayByPlay_whenFactsAreMissingDoesNotCollectFromPublicGet() {
        String gameId = "0022500099";
        when(gameIdMappingService.getNbaGameId(gameId)).thenReturn(gameId);
        when(playByPlayRepository.findByGameIdOrderByEventOrderAsc(gameId)).thenReturn(List.of());

        MatchDetailService service = new MatchDetailService(
                gameBoxScoreRepository,
                playByPlayRepository,
                gameIdMappingService,
                matchRecordRepository,
                gameNewsRepository,
                pythonScriptRunner,
                playerRepository,
                new PythonTimeoutProperties());

        List<PlayByPlayDto> result = service.getPlayByPlay(gameId, null);

        assertThat(result).isEmpty();
        verifyNoInteractions(pythonScriptRunner);
    }

    @Test
    void getQuarterScores_whenPersistedFactsAreInvalidDoesNotCallPythonFromGet() {
        String gameId = "0022500002";

        // PlayByPlay数据全是0:0
        PlayByPlay pbp = new PlayByPlay();
        pbp.setGameId(gameId);
        pbp.setPeriod(1);
        pbp.setGameClock("12:00");
        pbp.setHomeScore(0);
        pbp.setAwayScore(0);

        MatchRecord match = new MatchRecord();
        match.setNbaGameId(gameId);
        match.setHomeScore(110);
        match.setAwayScore(100);
        match.setStatus("FINISHED");
        match.setMatchDate(LocalDate.of(2026, 1, 5));

        when(gameIdMappingService.getNbaGameId(gameId)).thenReturn(gameId);
        when(playByPlayRepository.findByGameIdOrderByEventOrderAsc(gameId))
                .thenReturn(List.of(pbp));

        MatchDetailService service = new MatchDetailService(
                gameBoxScoreRepository,
                playByPlayRepository,
                gameIdMappingService,
                matchRecordRepository,
                gameNewsRepository,
                pythonScriptRunner,
                playerRepository,
                new PythonTimeoutProperties());

        List<QuarterScoreDto> result = service.getQuarterScores(gameId);

        assertThat(result).isEmpty();
        verifyNoInteractions(pythonScriptRunner);
    }

    @Test
    void getQuarterScores_forEspnSummerGameDoesNotCallPythonFromPublicGet() {
        String gameId = "401881845";
        when(gameIdMappingService.getNbaGameId(gameId)).thenReturn(gameId);
        when(playByPlayRepository.findByGameIdOrderByEventOrderAsc(gameId)).thenReturn(List.of());
        MatchDetailService service = new MatchDetailService(
                gameBoxScoreRepository,
                playByPlayRepository,
                gameIdMappingService,
                matchRecordRepository,
                gameNewsRepository,
                pythonScriptRunner,
                playerRepository,
                new PythonTimeoutProperties());

        List<QuarterScoreDto> result = service.getQuarterScores(gameId);

        assertThat(result).isEmpty();
        verifyNoInteractions(pythonScriptRunner);
    }

    @Test
    void getQuarterScores_usesValidPlayByPlayData() {
        String gameId = "0022500003";

        PlayByPlay pbp1 = new PlayByPlay();
        pbp1.setGameId(gameId);
        pbp1.setPeriod(1);
        pbp1.setGameClock("0:00");
        pbp1.setHomeScore(28);
        pbp1.setAwayScore(25);

        PlayByPlay pbp2 = new PlayByPlay();
        pbp2.setGameId(gameId);
        pbp2.setPeriod(2);
        pbp2.setGameClock("0:00");
        pbp2.setHomeScore(55);
        pbp2.setAwayScore(50);

        MatchRecord match = new MatchRecord();
        match.setNbaGameId(gameId);
        match.setHomeScore(110);
        match.setAwayScore(100);
        match.setStatus("FINISHED");
        match.setMatchDate(LocalDate.of(2026, 1, 10));

        when(gameIdMappingService.getNbaGameId(gameId)).thenReturn(gameId);
        when(playByPlayRepository.findByGameIdOrderByEventOrderAsc(gameId))
                .thenReturn(List.of(pbp1, pbp2));
        MatchDetailService service = new MatchDetailService(
                gameBoxScoreRepository,
                playByPlayRepository,
                gameIdMappingService,
                matchRecordRepository,
                gameNewsRepository,
                pythonScriptRunner,
                playerRepository,
                new PythonTimeoutProperties());

        List<QuarterScoreDto> result = service.getQuarterScores(gameId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).period()).isEqualTo(1);
        assertThat(result.get(0).homeScore()).isEqualTo(28);
        assertThat(result.get(0).awayScore()).isEqualTo(25);
        assertThat(result.get(1).period()).isEqualTo(2);
        assertThat(result.get(1).homeScore()).isEqualTo(55);
        assertThat(result.get(1).awayScore()).isEqualTo(50);
    }

    @Test
    void getQuarterScores_filtersZeroScoresFromPlayByPlay() {
        String gameId = "0022500004";

        // 混合有效和无效事件
        PlayByPlay pbp1 = new PlayByPlay();
        pbp1.setGameId(gameId);
        pbp1.setPeriod(1);
        pbp1.setGameClock("12:00");
        pbp1.setHomeScore(0);
        pbp1.setAwayScore(0);  // 无效：0:0

        PlayByPlay pbp2 = new PlayByPlay();
        pbp2.setGameId(gameId);
        pbp2.setPeriod(1);
        pbp2.setGameClock("6:00");
        pbp2.setHomeScore(15);
        pbp2.setAwayScore(12);  // 有效

        MatchRecord match = new MatchRecord();
        match.setNbaGameId(gameId);
        match.setHomeScore(110);
        match.setAwayScore(100);
        match.setStatus("FINISHED");
        match.setMatchDate(LocalDate.of(2026, 1, 15));

        when(gameIdMappingService.getNbaGameId(gameId)).thenReturn(gameId);
        when(playByPlayRepository.findByGameIdOrderByEventOrderAsc(gameId))
                .thenReturn(List.of(pbp1, pbp2));
        MatchDetailService service = new MatchDetailService(
                gameBoxScoreRepository,
                playByPlayRepository,
                gameIdMappingService,
                matchRecordRepository,
                gameNewsRepository,
                pythonScriptRunner,
                playerRepository,
                new PythonTimeoutProperties());

        List<QuarterScoreDto> result = service.getQuarterScores(gameId);

        // 应该使用有效事件的比分，而不是0:0
        assertThat(result).hasSize(1);
        assertThat(result.get(0).homeScore()).isEqualTo(15);
        assertThat(result.get(0).awayScore()).isEqualTo(12);
    }
}
