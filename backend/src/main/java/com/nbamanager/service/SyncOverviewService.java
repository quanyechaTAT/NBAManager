package com.nbamanager.service;

import com.nbamanager.repository.DraftPickRepository;
import com.nbamanager.repository.GameNewsRepository;
import com.nbamanager.repository.MatchRecordRepository;
import com.nbamanager.repository.PlayerRepository;
import com.nbamanager.repository.PlayerSeasonStatsRepository;
import com.nbamanager.repository.PlayoffMatchupRepository;
import com.nbamanager.repository.TeamRepository;
import com.nbamanager.web.dto.SyncModuleOverview;
import com.nbamanager.web.dto.SyncOverviewResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
    private final SeasonManagementService seasonManagementService;

    @Transactional(readOnly = true)
    public SyncOverviewResponse getOverview() {
        String currentSeason = seasonManagementService.getCurrentSeason();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime tomorrowStart = todayStart.plusDays(1);

        return new SyncOverviewResponse(currentSeason, List.of(
                module("TEAMS", teamRepository.count()),
                module("PLAYERS", playerRepository.count()),
                module("MATCHES", matchRecordRepository.count()),
                module(
                        "TODAY_GAMES",
                        gameNewsRepository.countTodayGames(todayStart, tomorrowStart)),
                module("NEWS", gameNewsRepository.count()),
                module("DRAFT", draftPickRepository.count()),
                module(
                        "PLAYOFF", playoffMatchupRepository.countBySeason(currentSeason)),
                module("HISTORICAL", playerSeasonStatsRepository.count()),
                module(
                        "TRANSLATION",
                        playerRepository.countByTranslationStatus("UNTRANSLATED"))));
    }

    private SyncModuleOverview module(String key, long count) {
        com.nbamanager.web.dto.SyncJobStepResponse activeFullStep = null;

        // Check if there's an active full sync with a step for this module
        syncJobRepository.findActiveByModule("FULL_SYNC").ifPresent(fullSyncJob -> {
            // This is handled at the overview level, not per-module
        });

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
                activeFullStep
        );
    }
}
