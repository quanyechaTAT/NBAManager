package com.nbamanager.service;

import com.nbamanager.domain.MatchRecord;
import com.nbamanager.domain.PlayByPlay;
import com.nbamanager.domain.replay.ReplayQualityLevel;
import com.nbamanager.web.dto.ReplayQualityDto;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReplayQualityService {

    private static final int MIN_FULL_EVENT_COUNT = 100;
    private static final int MIN_FULL_SCORING_EVENT_COUNT = 20;
    private static final Set<Integer> REGULATION_PERIODS = Set.of(1, 2, 3, 4);

    private final ReplayFactsHashService factsHashService;

    public ReplayQualityDto evaluate(
            List<PlayByPlay> replayEvents,
            MatchRecord match,
            LocalDateTime lastSyncedAt) {
        List<PlayByPlay> events = replayEvents == null
                ? List.of()
                : replayEvents.stream().filter(Objects::nonNull).toList();
        if (events.isEmpty()) {
            return new ReplayQualityDto(
                    ReplayQualityLevel.UNAVAILABLE,
                    0,
                    0,
                    List.of(),
                    null,
                    "暂无回放数据",
                    false,
                    false,
                    true,
                    lastSyncedAt,
                    List.of("NO_REPLAY_EVENTS"),
                    null);
        }

        List<Integer> periods = events.stream()
                .map(PlayByPlay::getPeriod)
                .filter(period -> period != null && period > 0)
                .distinct()
                .sorted()
                .toList();
        int scoringEventCount = (int) events.stream()
                .filter(event -> Boolean.TRUE.equals(event.getIsScoringPlay()))
                .count();

        SourceAssessment source = assessSource(events);
        boolean summaryOnly = events.stream().allMatch(this::isSummaryEvent);
        boolean synthetic = events.stream().anyMatch(this::isSummaryEvent);
        boolean eventOrdersMonotonic = hasStrictlyIncreasingEventOrders(events);
        boolean monotonicScores = hasMonotonicScores(events);
        boolean finalScoreVerified = isFinalScoreVerified(events, match);
        LocalDateTime dataAsOf = lastSyncedAt != null ? lastSyncedAt : latestEventTimestamp(events);

        LinkedHashSet<String> warnings = new LinkedHashSet<>();
        if (summaryOnly) {
            warnings.add("SUMMARY_EVENTS_ONLY");
        }
        if (!eventOrdersMonotonic) {
            warnings.add("EVENT_ORDER_NOT_STRICTLY_INCREASING");
        }
        if (!monotonicScores) {
            warnings.add("SCORES_NOT_MONOTONIC");
        }
        if (!finalScoreVerified) {
            warnings.add("FINAL_SCORE_NOT_VERIFIED");
        }
        if (!periods.containsAll(REGULATION_PERIODS)) {
            warnings.add("INSUFFICIENT_PERIOD_COVERAGE");
        }
        if (events.size() < MIN_FULL_EVENT_COUNT) {
            warnings.add("LOW_EVENT_DENSITY");
        }
        if (!summaryOnly && scoringEventCount < MIN_FULL_SCORING_EVENT_COUNT) {
            warnings.add("LOW_SCORING_EVENT_DENSITY");
        }
        if (source.mixed()) {
            warnings.add("MIXED_SOURCES");
        }
        if (!source.known()) {
            warnings.add("UNKNOWN_SOURCE");
        }

        ReplayQualityLevel level;
        if (summaryOnly) {
            level = ReplayQualityLevel.SUMMARY_ONLY;
        } else if (!synthetic
                && eventOrdersMonotonic
                && monotonicScores
                && finalScoreVerified
                && periods.containsAll(REGULATION_PERIODS)
                && events.size() >= MIN_FULL_EVENT_COUNT
                && scoringEventCount >= MIN_FULL_SCORING_EVENT_COUNT
                && source.known()
                && !source.mixed()) {
            level = ReplayQualityLevel.FULL;
        } else {
            level = ReplayQualityLevel.PARTIAL;
        }

        if (level == ReplayQualityLevel.FULL) {
            warnings.clear();
        }

        return new ReplayQualityDto(
                level,
                events.size(),
                scoringEventCount,
                periods,
                source.value(),
                source.label(),
                synthetic,
                finalScoreVerified,
                monotonicScores,
                dataAsOf,
                new ArrayList<>(warnings),
                factsHashService.hash(events));
    }

    private boolean hasStrictlyIncreasingEventOrders(List<PlayByPlay> events) {
        Integer previous = null;
        for (PlayByPlay event : events) {
            Integer current = event.getEventOrder();
            if (current == null || (previous != null && current <= previous)) {
                return false;
            }
            previous = current;
        }
        return true;
    }

    private boolean hasMonotonicScores(List<PlayByPlay> events) {
        Integer previousHome = null;
        Integer previousAway = null;
        for (PlayByPlay event : events) {
            Integer home = event.getHomeScore();
            Integer away = event.getAwayScore();
            if (home == null || away == null || home < 0 || away < 0) {
                return false;
            }
            if (previousHome != null && (home < previousHome || away < previousAway)) {
                return false;
            }
            previousHome = home;
            previousAway = away;
        }
        return true;
    }

    private boolean isFinalScoreVerified(List<PlayByPlay> events, MatchRecord match) {
        if (match == null
                || !"FINISHED".equalsIgnoreCase(match.getStatus())
                || match.getHomeScore() == null
                || match.getAwayScore() == null) {
            return false;
        }
        PlayByPlay last = events.stream()
                .filter(event -> event.getHomeScore() != null && event.getAwayScore() != null)
                .max(Comparator
                        .comparing(PlayByPlay::getEventOrder, Comparator.nullsFirst(Integer::compareTo))
                        .thenComparing(PlayByPlay::getPeriod, Comparator.nullsFirst(Integer::compareTo)))
                .orElse(null);
        return last != null
                && match.getHomeScore().equals(last.getHomeScore())
                && match.getAwayScore().equals(last.getAwayScore());
    }

    private LocalDateTime latestEventTimestamp(List<PlayByPlay> events) {
        return events.stream()
                .flatMap(event -> java.util.stream.Stream.of(event.getUpdatedAt(), event.getCreatedAt()))
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    private boolean isSummaryEvent(PlayByPlay event) {
        return isPeriodScore(event.getEventType())
                || isPeriodScore(event.getActionType())
                || containsSummaryMarker(event.getSource());
    }

    private boolean isPeriodScore(String value) {
        return value != null && "period_score".equalsIgnoreCase(value.trim());
    }

    private boolean containsSummaryMarker(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.contains("SCOREBOARD") || normalized.contains("SUMMARY");
    }

    private SourceAssessment assessSource(List<PlayByPlay> events) {
        Set<String> sources = events.stream()
                .map(PlayByPlay::getSource)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(source -> !source.isEmpty())
                .map(source -> source.toUpperCase(Locale.ROOT))
                .collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
        if (sources.isEmpty()) {
            return new SourceAssessment(null, "未知来源", false, false);
        }
        if (sources.size() > 1) {
            return new SourceAssessment("MIXED", "多来源合并", false, true);
        }
        String source = sources.iterator().next();
        return switch (source) {
            case "NBA_PLAY_BY_PLAY" -> new SourceAssessment(source, "NBA 官方逐回合", true, false);
            case "ESPN_PLAY_BY_PLAY" -> new SourceAssessment(source, "ESPN 逐回合", true, false);
            case "ESPN_SCOREBOARD_SUMMARY" -> new SourceAssessment(source, "ESPN 节末比分", true, false);
            case "ESPN_NBA_SUMMER_SCOREBOARD" ->
                    new SourceAssessment(source, "ESPN 夏季联赛节末比分", true, false);
            case "NBA_SCOREBOARD_SUMMARY" -> new SourceAssessment(source, "NBA 节末比分", true, false);
            default -> new SourceAssessment(source, source, false, false);
        };
    }

    private record SourceAssessment(String value, String label, boolean known, boolean mixed) {}
}
