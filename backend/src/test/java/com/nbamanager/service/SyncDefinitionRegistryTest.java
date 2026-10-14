package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class SyncDefinitionRegistryTest {

    private final TeamSyncWorker teamSyncWorker = mock(TeamSyncWorker.class);
    private final PlayerSyncWorker playerSyncWorker = mock(PlayerSyncWorker.class);
    private final MatchSyncWorker matchSyncWorker = mock(MatchSyncWorker.class);
    private final TodayGamesSyncWorker todayGamesSyncWorker = mock(TodayGamesSyncWorker.class);
    private final NewsSyncWorker newsSyncWorker = mock(NewsSyncWorker.class);
    private final DraftSyncWorker draftSyncWorker = mock(DraftSyncWorker.class);
    private final PlayoffSyncWorker playoffSyncWorker = mock(PlayoffSyncWorker.class);
    private final HistoricalSyncWorker historicalSyncWorker = mock(HistoricalSyncWorker.class);
    private final TranslationSyncWorker translationSyncWorker = mock(TranslationSyncWorker.class);

    @Test
    void registryUsesOneWorkerForEveryTriggerSource() {
        SyncDefinitionRegistry registry = new SyncDefinitionRegistry(
                teamSyncWorker, playerSyncWorker, matchSyncWorker,
                todayGamesSyncWorker, newsSyncWorker, draftSyncWorker,
                playoffSyncWorker, historicalSyncWorker, translationSyncWorker);

        SyncDefinition news = registry.require("NEWS");
        assertThat(news.worker()).isSameAs(newsSyncWorker);
        assertThat(registry.fullSyncOrder()).containsExactly(
                "TEAMS", "PLAYERS", "MATCHES", "TODAY_GAMES", "NEWS",
                "DRAFT", "PLAYOFF", "HISTORICAL", "TRANSLATION");
    }

    @Test
    void requireThrowsForUnknownModule() {
        SyncDefinitionRegistry registry = new SyncDefinitionRegistry(
                teamSyncWorker, playerSyncWorker, matchSyncWorker,
                todayGamesSyncWorker, newsSyncWorker, draftSyncWorker,
                playoffSyncWorker, historicalSyncWorker, translationSyncWorker);

        assertThatThrownBy(() -> registry.require("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scheduledDefinitionsExcludesFullSync() {
        SyncDefinitionRegistry registry = new SyncDefinitionRegistry(
                teamSyncWorker, playerSyncWorker, matchSyncWorker,
                todayGamesSyncWorker, newsSyncWorker, draftSyncWorker,
                playoffSyncWorker, historicalSyncWorker, translationSyncWorker);

        assertThat(registry.scheduledDefinitions())
                .extracting(SyncDefinition::module)
                .doesNotContain("FULL_SYNC");
    }

    @Test
    void playoffScheduleRunsInAprilThroughJuneNotTheFourthThroughSixthDay() throws Exception {
        var registry = new SyncDefinitionRegistry(teamSyncWorker, playerSyncWorker, matchSyncWorker,
                todayGamesSyncWorker, newsSyncWorker, draftSyncWorker, playoffSyncWorker, historicalSyncWorker, translationSyncWorker);
        var schedule = registry.require("PLAYOFF").schedules().get(0);
        var april = java.time.ZonedDateTime.parse("2026-04-18T00:00:00+08:00[Asia/Shanghai]");
        var october = java.time.ZonedDateTime.parse("2026-10-04T00:00:00+08:00[Asia/Shanghai]");
        assertThat(schedule.isDue(april, april.plusHours(2))).isTrue();
        assertThat(schedule.isDue(october, october.plusDays(2))).isFalse();
        var annotation = DataSyncScheduler.class.getMethod("syncPlayoff")
                .getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);
        assertThat(org.springframework.scheduling.support.CronExpression.parse(annotation.cron()).next(october).getMonthValue()).isEqualTo(4);
    }
}
