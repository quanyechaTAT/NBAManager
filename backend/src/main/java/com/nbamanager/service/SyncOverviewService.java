package com.nbamanager.service;

import com.nbamanager.repository.DraftPickRepository;
import com.nbamanager.repository.GameNewsRepository;
import com.nbamanager.repository.MatchRecordRepository;
import com.nbamanager.repository.PlayerRepository;
import com.nbamanager.repository.PlayerSeasonStatsRepository;
import com.nbamanager.repository.PlayoffMatchupRepository;
import com.nbamanager.repository.TeamRepository;
import com.nbamanager.repository.SyncJobStepRepository;
import com.nbamanager.web.dto.SyncModuleOverview;
import com.nbamanager.web.dto.SyncOverviewResponse;
import com.nbamanager.web.dto.SyncJobStepResponse;
import com.nbamanager.web.dto.SyncJobResponse;
import com.nbamanager.web.dto.SyncJobDetailResponse;
import com.nbamanager.domain.SyncJob;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SyncOverviewService {

    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final MatchRecordRepository matchRecordRepository;
    private final GameNewsRepository gameNewsRepository;
    private final DraftPickRepository draftPickRepository;
    private final PlayoffMatchupRepository playoffMatchupRepository;
    private final PlayerSeasonStatsRepository playerSeasonStatsRepository;
    private final com.nbamanager.repository.SyncJobRepository syncJobRepository;
    private final SyncJobStepRepository syncJobStepRepository;
    private final SeasonManagementService seasonManagementService;

    @Transactional(readOnly = true)
    public SyncOverviewResponse getOverview() {
        String currentSeason = seasonManagementService.getCurrentSeason();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);
        Map<String, SyncJob> latestJobs = jobsByModule(syncJobRepository.findLatestPerModule());
        Map<String, SyncJob> activeJobs = jobsByModule(syncJobRepository.findByStatusIn(List.of("QUEUED", "RUNNING", "CANCELLING")));
        Map<String, LocalDateTime> successfulJobs = syncJobRepository.findLatestSuccessfulPerModule().stream()
                .collect(Collectors.toMap(SyncJob::getModule, SyncJob::getFinishedAt, SyncOverviewService::later));
        SyncJob activeFull = activeJobs.get("FULL_SYNC");
        SyncJob latestFull = activeFull != null ? activeFull
                : latestJobs.get("FULL_SYNC");
        List<SyncJobStepResponse> fullSteps = latestFull == null ? List.of()
                : syncJobStepRepository.findByJobIdOrderByStepOrder(latestFull.getId()).stream()
                        .map(step -> SyncJobStepResponse.from(step, latestFull)).toList();
        Map<String, SyncJobStepResponse> latestFullSteps = fullSteps.stream()
                .collect(Collectors.toMap(SyncJobStepResponse::module, step -> step, (first, ignored) -> first));
        Map<String, SyncJobStepResponse> activeFullSteps = activeFull == null ? Map.of() : latestFullSteps;
        Map<String, LocalDateTime> successfulSteps = new HashMap<>();
        for (var step : syncJobStepRepository.findLatestSuccessfulSteps()) {
            if (step.getFinishedAt() != null) successfulSteps.put(step.getModule(), step.getFinishedAt());
        }
        for (var step : fullSteps) {
            if ("SUCCESS".equals(step.status()) && step.finishedAt() != null)
                successfulSteps.merge(step.module(), step.finishedAt(), SyncOverviewService::later);
        }
        Snapshot snapshot = new Snapshot(latestJobs, activeJobs, successfulJobs, successfulSteps,
                activeFullSteps, latestFullSteps, latestFull);

        return new SyncOverviewResponse(currentSeason, List.of(
                module("TEAMS", teamRepository.count(), snapshot),
                module("PLAYERS", playerRepository.count(), snapshot),
                module("MATCHES", matchRecordRepository.count(), snapshot),
                module(
                        "TODAY_GAMES",
                        gameNewsRepository.countTodayGames(todayStart, tomorrowStart), snapshot),
                module("NEWS", gameNewsRepository.count(), snapshot),
                module("DRAFT", draftPickRepository.count(), snapshot),
                module(
                        "PLAYOFF", playoffMatchupRepository.countBySeason(currentSeason), snapshot),
                module("HISTORICAL", playerSeasonStatsRepository.count(), snapshot),
                module(
                        "TRANSLATION",
                        playerRepository.countByTranslationStatus("UNTRANSLATED"), snapshot)),
                activeFull == null ? null : SyncJobResponse.from(activeFull),
                latestFull == null ? null : SyncJobDetailResponse.from(latestFull, fullSteps));
    }

    private SyncModuleOverview module(String key, long count, Snapshot snapshot) {
        SyncJob latest = snapshot.latestJobs().get(key);
        SyncJob latestFull = snapshot.latestFull();
        SyncJobStepResponse fullStep = snapshot.latestFullSteps().get(key);
        LocalDateTime fullTime = fullStep != null && fullStep.startedAt() != null
                ? fullStep.startedAt() : latestFull == null ? null : latestFull.getCreatedAt();
        if (latest != null && latest.getCreatedAt() != null
                && (fullTime == null || !fullTime.isAfter(latest.getCreatedAt()))) fullStep = null;
        return new SyncModuleOverview(
                key,
                count,
                later(snapshot.successfulJobs().get(key), snapshot.successfulSteps().get(key)),
                latest == null ? null : SyncJobResponse.from(latest),
                snapshot.activeJobs().get(key) == null ? null : SyncJobResponse.from(snapshot.activeJobs().get(key)),
                snapshot.activeFullSteps().get(key),
                fullStep
        );
    }

    private Map<String, SyncJob> jobsByModule(List<SyncJob> jobs) {
        return jobs.stream().collect(Collectors.toMap(SyncJob::getModule, job -> job,
                (first, second) -> first.getId() > second.getId() ? first : second));
    }

    private record Snapshot(Map<String, SyncJob> latestJobs, Map<String, SyncJob> activeJobs,
            Map<String, LocalDateTime> successfulJobs, Map<String, LocalDateTime> successfulSteps,
            Map<String, SyncJobStepResponse> activeFullSteps, Map<String, SyncJobStepResponse> latestFullSteps,
            SyncJob latestFull) {}

    private static LocalDateTime later(LocalDateTime first, LocalDateTime second) {
        if (first == null) return second;
        return second == null || first.isAfter(second) ? first : second;
    }
}
