package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nbamanager.repository.DraftPickRepository;
import com.nbamanager.repository.GameNewsRepository;
import com.nbamanager.repository.MatchRecordRepository;
import com.nbamanager.repository.PlayerRepository;
import com.nbamanager.repository.PlayerSeasonStatsRepository;
import com.nbamanager.repository.PlayoffMatchupRepository;
import com.nbamanager.repository.TeamRepository;
import com.nbamanager.repository.SyncJobRepository;
import com.nbamanager.repository.SyncJobStepRepository;
import com.nbamanager.domain.SyncJob;
import com.nbamanager.domain.SyncJobStep;
import com.nbamanager.web.dto.SyncModuleOverview;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyncOverviewServiceTest {

    @Test
    void completedFullSyncStillSuppliesTheModuleSuccessfulTime() {
        when(seasonManagementService.getCurrentSeason()).thenReturn("2025-26");
        SyncJob full = new SyncJob();
        full.setId(90L);
        full.setModule("FULL_SYNC");
        full.setStatus("SUCCESS");
        full.setCreatedAt(LocalDateTime.of(2026, 10, 5, 12, 0));
        SyncJobStep step = new SyncJobStep();
        step.setId(901L);
        step.setJobId(90L);
        step.setModule("TEAMS");
        step.setStatus("SUCCESS");
        step.setFinishedAt(LocalDateTime.of(2026, 10, 5, 12, 5));
        when(syncJobRepository.findLatestPerModule()).thenReturn(java.util.List.of(full));
        when(syncJobStepRepository.findByJobIdOrderByStepOrder(90L))
                .thenReturn(java.util.List.of(step));
        SyncModuleOverview teams = service.getOverview().modules().stream()
                .filter(module -> module.key().equals("TEAMS")).findFirst().orElseThrow();
        assertThat(teams.lastSuccessfulAt()).isEqualTo(step.getFinishedAt());
        assertThat(teams.latestFullStep().status()).isEqualTo("SUCCESS");
        assertThat(service.getOverview().latestFullJob().jobId()).isEqualTo(90L);
    }

    @Mock private TeamRepository teamRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private MatchRecordRepository matchRecordRepository;
    @Mock private GameNewsRepository gameNewsRepository;
    @Mock private DraftPickRepository draftPickRepository;
    @Mock private PlayoffMatchupRepository playoffMatchupRepository;
    @Mock private PlayerSeasonStatsRepository playerSeasonStatsRepository;
    @Mock private SyncJobRepository syncJobRepository;
    @Mock private SyncJobStepRepository syncJobStepRepository;
    @Mock private SeasonManagementService seasonManagementService;

    @InjectMocks private SyncOverviewService service;

    @Test
    void getOverview_returnsRealRepositoryCountsForEveryModule() {
        when(seasonManagementService.getCurrentSeason()).thenReturn("2025-26");
        when(teamRepository.count()).thenReturn(30L);
        when(playerRepository.count()).thenReturn(582L);
        when(matchRecordRepository.count()).thenReturn(88L);
        when(gameNewsRepository.count()).thenReturn(96L);
        when(gameNewsRepository.countTodayGames(
                        any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(7L);
        when(draftPickRepository.count()).thenReturn(293L);
        when(playoffMatchupRepository.countBySeason("2025-26")).thenReturn(15L);
        when(playerSeasonStatsRepository.count()).thenReturn(1200L);
        when(playerRepository.countByTranslationStatus("UNTRANSLATED")).thenReturn(9L);

        Map<String, SyncModuleOverview> modules = service.getOverview().modules().stream()
                .collect(Collectors.toMap(SyncModuleOverview::key, Function.identity()));

        assertThat(modules).hasSize(9);
        assertThat(modules.get("TEAMS").count()).isEqualTo(30L);
        assertThat(modules.get("PLAYERS").count()).isEqualTo(582L);
        assertThat(modules.get("MATCHES").count()).isEqualTo(88L);
        assertThat(modules.get("TODAY_GAMES").count()).isEqualTo(7L);
        assertThat(modules.get("NEWS").count()).isEqualTo(96L);
        assertThat(modules.get("DRAFT").count()).isEqualTo(293L);
        assertThat(modules.get("PLAYOFF").count()).isEqualTo(15L);
        assertThat(modules.get("HISTORICAL").count()).isEqualTo(1200L);
        assertThat(modules.get("TRANSLATION").count()).isEqualTo(9L);
        assertThat(service.getOverview().currentSeason()).isEqualTo("2025-26");
    }

    @Test
    void getOverviewIncludesActiveFullSyncStepForModule() {
        when(seasonManagementService.getCurrentSeason()).thenReturn("2025-26");
        SyncJob fullSync = new SyncJob();
        fullSync.setId(90L);
        fullSync.setModule("FULL_SYNC");
        fullSync.setStatus("RUNNING");
        SyncJobStep teamsStep = new SyncJobStep();
        teamsStep.setId(901L);
        teamsStep.setJobId(90L);
        teamsStep.setModule("TEAMS");
        teamsStep.setStepOrder(0);
        teamsStep.setStatus("RUNNING");
        teamsStep.setProgress(30);
        when(syncJobRepository.findByStatusIn(java.util.List.of("QUEUED", "RUNNING", "CANCELLING")))
                .thenReturn(java.util.List.of(fullSync));
        when(syncJobStepRepository.findByJobIdOrderByStepOrder(90L)).thenReturn(java.util.List.of(teamsStep));

        SyncModuleOverview teams = service.getOverview().modules().stream()
                .filter(module -> module.key().equals("TEAMS"))
                .findFirst().orElseThrow();

        assertThat(teams.activeFullStep()).isNotNull();
        assertThat(teams.activeFullStep().module()).isEqualTo("TEAMS");
        assertThat(teams.activeFullStep().progress()).isEqualTo(30);
        assertThat(service.getOverview().activeFullJob().jobId()).isEqualTo(90L);
    }

    @Test
    void newerSingleJobSupersedesFinishedFullStepButDoesNotEraseItsSuccessTime() {
        SyncJob full = new SyncJob(); full.setId(90L); full.setModule("FULL_SYNC"); full.setStatus("SUCCESS");
        full.setCreatedAt(LocalDateTime.of(2026, 10, 5, 12, 0));
        SyncJob single = new SyncJob(); single.setId(91L); single.setModule("TEAMS"); single.setStatus("FAILED");
        single.setCreatedAt(full.getCreatedAt().plusHours(1));
        SyncJobStep step = new SyncJobStep(); step.setId(901L); step.setJobId(90L); step.setModule("TEAMS");
        step.setStatus("SUCCESS"); step.setStartedAt(full.getCreatedAt()); step.setFinishedAt(full.getCreatedAt().plusMinutes(1));
        when(syncJobRepository.findLatestPerModule()).thenReturn(java.util.List.of(full, single));
        when(syncJobStepRepository.findByJobIdOrderByStepOrder(90L)).thenReturn(java.util.List.of(step));
        var teams = service.getOverview().modules().get(0);
        assertThat(teams.latestJob().status()).isEqualTo("FAILED");
        assertThat(teams.latestFullStep()).isNull();
        assertThat(teams.lastSuccessfulAt()).isEqualTo(step.getFinishedAt());
    }

    @Test
    void interruptedFullParentCannotLeaveQueuedStepsLookingActive() {
        SyncJob full = new SyncJob(); full.setId(90L); full.setModule("FULL_SYNC"); full.setStatus("INTERRUPTED");
        full.setCreatedAt(LocalDateTime.of(2026, 10, 5, 12, 0)); full.setFinishedAt(full.getCreatedAt().plusMinutes(5));
        SyncJobStep step = new SyncJobStep(); step.setId(901L); step.setJobId(90L); step.setModule("TEAMS"); step.setStatus("QUEUED");
        when(syncJobRepository.findLatestPerModule()).thenReturn(java.util.List.of(full));
        when(syncJobStepRepository.findByJobIdOrderByStepOrder(90L)).thenReturn(java.util.List.of(step));
        var result = service.getOverview();
        assertThat(result.activeFullJob()).isNull();
        assertThat(result.modules().get(0).latestFullStep().status()).isEqualTo("INTERRUPTED");
        assertThat(result.latestFullJob().steps().get(0).finishedAt()).isEqualTo(full.getFinishedAt());
        assertThat(step.getStatus()).isEqualTo("QUEUED");
    }
}
