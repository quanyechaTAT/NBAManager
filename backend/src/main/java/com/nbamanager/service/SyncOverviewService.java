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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
        Map<String, SyncJobStepResponse> activeFullSteps =
                syncJobRepository.findActiveByModule("FULL_SYNC")
                        .map(job -> syncJobStepRepository.findByJobIdOrderByStepOrder(job.getId()).stream()
                                .collect(Collectors.toMap(
                                        com.nbamanager.domain.SyncJobStep::getModule,
                                        SyncJobStepResponse::from,
                                        (first, ignored) -> first)))
                        .orElseGet(Map::of);

        return new SyncOverviewResponse(currentSeason, List.of(
                module("TEAMS", teamRepository.count(), activeFullSteps),
                module("PLAYERS", playerRepository.count(), activeFullSteps),
                module("MATCHES", matchRecordRepository.count(), activeFullSteps),
                module(
                        "TODAY_GAMES",
                        gameNewsRepository.countTodayGames(todayStart, tomorrowStart), activeFullSteps),
                module("NEWS", gameNewsRepository.count(), activeFullSteps),
                module("DRAFT", draftPickRepository.count(), activeFullSteps),
                module(
                        "PLAYOFF", playoffMatchupRepository.countBySeason(currentSeason), activeFullSteps),
                module("HISTORICAL", playerSeasonStatsRepository.count(), activeFullSteps),
                module(
                        "TRANSLATION",
                        playerRepository.countByTranslationStatus("UNTRANSLATED"), activeFullSteps)));
    }

    private SyncModuleOverview module(
            String key, long count, Map<String, SyncJobStepResponse> activeFullSteps) {
        return new SyncModuleOverview(
                key,
                count,
                syncJobRepository.findTopByModuleAndStatusOrderByFinishedAtDesc(key, "SUCCESS")
                        .map(j -> j.getFinishedAt())
                        .orElse(null),
                syncJobRepository.findTopByModuleOrderByCreatedAtDesc(key)
                        .map(com.nbamanager.web.dto.SyncJobResponse::from)
                        .orElse(null),
                syncJobRepository.findActiveByModule(key)
                        .map(com.nbamanager.web.dto.SyncJobResponse::from)
                        .orElse(null),
                activeFullSteps.get(key)
        );
    }
}
