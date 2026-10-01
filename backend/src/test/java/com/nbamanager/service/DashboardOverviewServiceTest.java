package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nbamanager.service.RAGService.RAGStats;
import com.nbamanager.web.dto.DashboardStatsDto;
import com.nbamanager.web.dto.GameNewsDto;
import com.nbamanager.web.dto.PostDto;
import com.nbamanager.web.dto.TeamRankDto;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class DashboardOverviewServiceTest {

    @Mock
    private DashboardService dashboardService;

    @Mock
    private GameNewsService gameNewsService;

    @Mock
    private TeamService teamService;

    @Mock
    private PostService postService;

    @Mock
    private RAGService ragService;

    @Test
    void buildOverviewUsesCachedLocalSourcesForFastHomePageSnapshot() {
        DashboardStatsDto stats = new DashboardStatsDto(
                30,
                450,
                List.of(new DashboardStatsDto.TeamWinRow("Thunder", 68, 14)),
                List.of(new DashboardStatsDto.TopScorerRow(7L, "Shai Gilgeous-Alexander", 32.7, "Thunder", 1628983L)));
        GameNewsDto game = new GameNewsDto(
                11L,
                "Thunder vs Lakers",
                "",
                "",
                "Thunder",
                "Lakers",
                110,
                102,
                LocalDateTime.now(),
                LocalDateTime.now().plusHours(2),
                "LIVE",
                LocalDateTime.now(),
                "0022500001",
                "game",
                null,
                null,
                0,
                0,
                false);
        TeamRankDto west = new TeamRankDto("Thunder", 68, 14, 0.0, "Western");
        TeamRankDto east = new TeamRankDto("Celtics", 61, 21, 0.0, "Eastern");
        PostDto post = new PostDto(
                3L,
                2L,
                "fan",
                null,
                "Finals preview",
                "content",
                "general",
                "",
                99,
                10,
                4,
                2,
                false,
                false,
                false,
                false,
                null,
                LocalDateTime.now());

        when(dashboardService.buildStats()).thenReturn(stats);
        when(gameNewsService.getTodayGames()).thenReturn(List.of(game));
        when(teamService.getConferenceRankings()).thenReturn(Map.of("western", List.of(west), "eastern", List.of(east)));
        when(postService.hot(PageRequest.of(0, 5))).thenReturn(new PageImpl<>(List.of(post), PageRequest.of(0, 5), 1));
        when(ragService.getStatsSnapshot()).thenReturn(new RAGStats(128, "emb", "llm"));

        DashboardOverviewService service =
                new DashboardOverviewService(dashboardService, gameNewsService, teamService, postService, ragService);

        var overview = service.buildOverview();

        assertThat(overview.stats()).isSameAs(stats);
        assertThat(overview.todayGames()).containsExactly(game);
        assertThat(overview.westStandings()).containsExactly(west);
        assertThat(overview.eastStandings()).containsExactly(east);
        assertThat(overview.hotPosts()).containsExactly(post);
        assertThat(overview.docCount()).isEqualTo(128);
        assertThat(overview.partialErrors()).isEmpty();
        verify(gameNewsService).getTodayGames();
        verify(ragService).getStatsSnapshot();
        verify(ragService, never()).getStats();
    }
}
