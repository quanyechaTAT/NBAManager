package com.nbamanager.service;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SyncDefinitionRegistry {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final Map<String, SyncDefinition> definitions;

    public SyncDefinitionRegistry(
            TeamSyncWorker teamSyncWorker,
            PlayerSyncWorker playerSyncWorker,
            MatchSyncWorker matchSyncWorker,
            TodayGamesSyncWorker todayGamesSyncWorker,
            NewsSyncWorker newsSyncWorker,
            DraftSyncWorker draftSyncWorker,
            PlayoffSyncWorker playoffSyncWorker,
            HistoricalSyncWorker historicalSyncWorker,
            TranslationSyncWorker translationSyncWorker
    ) {
        definitions = new LinkedHashMap<>();

        // Full sync order: TEAMS(1), PLAYERS(2), MATCHES(3), TODAY_GAMES(4), NEWS(5), DRAFT(6), PLAYOFF(7), HISTORICAL(8), TRANSLATION(9)
        definitions.put("TEAMS", new SyncDefinition("TEAMS", teamSyncWorker, 1, List.of()));
        definitions.put("PLAYERS", new SyncDefinition("PLAYERS", playerSyncWorker, 2, List.of()));
        definitions.put("MATCHES", new SyncDefinition("MATCHES", matchSyncWorker, 3, List.of()));

        // TODAY_GAMES: every 5 minutes
        definitions.put("TODAY_GAMES", new SyncDefinition("TODAY_GAMES", todayGamesSyncWorker, 4,
                List.of(SyncSchedulePolicy.fixed(Duration.ofMinutes(5)))));

        // NEWS: every hour
        definitions.put("NEWS", new SyncDefinition("NEWS", newsSyncWorker, 5,
                List.of(SyncSchedulePolicy.fixed(Duration.ofHours(1)))));

        // DRAFT: three cron schedules
        definitions.put("DRAFT", new SyncDefinition("DRAFT", draftSyncWorker, 6,
                List.of(
                        SyncSchedulePolicy.cron("0 */30 * 15-30 6 ?", ZONE),
                        SyncSchedulePolicy.cron("0 */30 * 1-10 7 ?", ZONE),
                        SyncSchedulePolicy.cron("0 20 4 * * ?", ZONE)
                )));

        // PLAYOFF: April-June every 2 hours
        definitions.put("PLAYOFF", new SyncDefinition("PLAYOFF", playoffSyncWorker, 7,
                List.of(SyncSchedulePolicy.cron("0 0 */2 * 4-6 ?", ZONE))));

        // HISTORICAL: daily at 4:00
        definitions.put("HISTORICAL", new SyncDefinition("HISTORICAL", historicalSyncWorker, 8,
                List.of(SyncSchedulePolicy.cron("0 0 4 * * ?", ZONE))));

        // TRANSLATION: every hour at minute 10
        definitions.put("TRANSLATION", new SyncDefinition("TRANSLATION", translationSyncWorker, 9,
                List.of(SyncSchedulePolicy.cron("0 10 * * * *", ZONE))));
    }

    public SyncDefinition require(String module) {
        SyncDefinition definition = definitions.get(module);
        if (definition == null) throw new IllegalArgumentException("未知模块: " + module);
        return definition;
    }

    public List<String> fullSyncOrder() {
        return definitions.values().stream()
                .sorted(Comparator.comparingInt(SyncDefinition::fullSyncOrder))
                .map(SyncDefinition::module)
                .toList();
    }

    public List<SyncDefinition> scheduledDefinitions() {
        return definitions.values().stream()
                .filter(definition -> !definition.schedules().isEmpty())
                .toList();
    }

    public SyncDefinition fullSyncDefinition() {
        // Full sync is not a module in the registry, but we need a definition for catchup
        // Return the first module as reference for schedule checking
        return require("TEAMS");
    }
}
