package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.GameNews;
import com.nbamanager.repository.GameNewsRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SmartDataServiceUpdateTest {

    @InjectMocks
    private SmartDataService smartDataService;

    @Mock
    private GameNewsRepository gameNewsRepository;

    private GameNews existingScheduled;

    @BeforeEach
    void setup() {
        existingScheduled = new GameNews();
        existingScheduled.setId(1L);
        existingScheduled.setHomeTeam("魔术");
        existingScheduled.setAwayTeam("热火");
        existingScheduled.setHomeScore(null);
        existingScheduled.setAwayScore(null);
        existingScheduled.setStatus("SCHEDULED");
        existingScheduled.setNbaGameId("401881800");
        existingScheduled.setCategory("summer_game");
        existingScheduled.setSourceUrl("ESPN_NBA_SUMMER");
        existingScheduled.setGameStartTime(LocalDateTime.of(2026, 7, 12, 3, 30));
        existingScheduled.setTitle("热火 vs 魔术");
        existingScheduled.setSummary("NBA夏季联赛: 热火 vs 魔术, 未开始");
        existingScheduled.setContent("NBA夏季联赛: 热火 vs 魔术\n状态: 未开始");
    }

    @Test
    void scheduledToFinished_updatesContentWithoutUnstartedText() {
        when(gameNewsRepository.findByNbaGameId("401881800")).thenReturn(List.of(existingScheduled));
        when(gameNewsRepository.save(any(GameNews.class))).thenAnswer(inv -> inv.getArgument(0));

        smartDataService.upsertGameNews("魔术", "热火", 92, 105, "FINISHED", "401881800",
                "summer_game", "ESPN_NBA_SUMMER", LocalDateTime.of(2026, 7, 12, 3, 30));

        ArgumentCaptor<GameNews> captor = ArgumentCaptor.forClass(GameNews.class);
        org.mockito.Mockito.verify(gameNewsRepository).save(captor.capture());
        GameNews saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo("FINISHED");
        assertThat(saved.getHomeScore()).isEqualTo(92);
        assertThat(saved.getAwayScore()).isEqualTo(105);
        assertThat(saved.getTitle()).contains("105").contains("92");
        assertThat(saved.getSummary()).doesNotContain("未开始");
        assertThat(saved.getContent()).doesNotContain("未开始");
        assertThat(saved.getContent()).contains("已结束");
    }

    @Test
    void scheduledKeepsNullScores() {
        when(gameNewsRepository.findByNbaGameId("401881800")).thenReturn(List.of(existingScheduled));
        when(gameNewsRepository.save(any(GameNews.class))).thenAnswer(inv -> inv.getArgument(0));

        smartDataService.upsertGameNews("魔术", "热火", 0, 0, "SCHEDULED", "401881800",
                "summer_game", "ESPN_NBA_SUMMER", LocalDateTime.of(2026, 7, 12, 3, 30));

        ArgumentCaptor<GameNews> captor = ArgumentCaptor.forClass(GameNews.class);
        org.mockito.Mockito.verify(gameNewsRepository).save(captor.capture());
        GameNews saved = captor.getValue();

        assertThat(saved.getHomeScore()).isNull();
        assertThat(saved.getAwayScore()).isNull();
        assertThat(saved.getTitle()).isEqualTo("热火 vs 魔术");
        assertThat(saved.getContent()).contains("未开始");
    }

    @Test
    void summerGameCategory_preservesLabel() {
        when(gameNewsRepository.findByNbaGameId("401881800")).thenReturn(List.of(existingScheduled));
        when(gameNewsRepository.save(any(GameNews.class))).thenAnswer(inv -> inv.getArgument(0));

        smartDataService.upsertGameNews("魔术", "热火", 92, 105, "FINISHED", "401881800",
                "summer_game", "ESPN_NBA_SUMMER", LocalDateTime.of(2026, 7, 12, 3, 30));

        ArgumentCaptor<GameNews> captor = ArgumentCaptor.forClass(GameNews.class);
        org.mockito.Mockito.verify(gameNewsRepository).save(captor.capture());
        GameNews saved = captor.getValue();

        assertThat(saved.getSummary()).contains("NBA夏季联赛");
        assertThat(saved.getCategory()).isEqualTo("summer_game");
        assertThat(saved.getSourceUrl()).isEqualTo("ESPN_NBA_SUMMER");
    }

    @Test
    void liveWithScores_showsInProgress() {
        when(gameNewsRepository.findByNbaGameId("401881800")).thenReturn(List.of(existingScheduled));
        when(gameNewsRepository.save(any(GameNews.class))).thenAnswer(inv -> inv.getArgument(0));

        smartDataService.upsertGameNews("魔术", "热火", 45, 50, "LIVE", "401881800",
                "summer_game", "ESPN_NBA_SUMMER", LocalDateTime.of(2026, 7, 12, 3, 30));

        ArgumentCaptor<GameNews> captor = ArgumentCaptor.forClass(GameNews.class);
        org.mockito.Mockito.verify(gameNewsRepository).save(captor.capture());
        GameNews saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo("LIVE");
        assertThat(saved.getHomeScore()).isEqualTo(45);
        assertThat(saved.getAwayScore()).isEqualTo(50);
        assertThat(saved.getContent()).contains("进行中");
        assertThat(saved.getContent()).doesNotContain("未开始");
    }

    @Test
    void liveWithoutScores_showsInProgressNoFakeScore() {
        when(gameNewsRepository.findByNbaGameId("401881800")).thenReturn(List.of(existingScheduled));
        when(gameNewsRepository.save(any(GameNews.class))).thenAnswer(inv -> inv.getArgument(0));

        smartDataService.upsertGameNews("魔术", "热火", 0, 0, "LIVE", "401881800",
                "summer_game", "ESPN_NBA_SUMMER", LocalDateTime.of(2026, 7, 12, 3, 30));

        ArgumentCaptor<GameNews> captor = ArgumentCaptor.forClass(GameNews.class);
        org.mockito.Mockito.verify(gameNewsRepository).save(captor.capture());
        GameNews saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo("LIVE");
        assertThat(saved.getContent()).contains("进行中");
        assertThat(saved.getContent()).doesNotContain("0 : 0");
    }
}
