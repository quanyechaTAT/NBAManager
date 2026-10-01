package com.nbamanager.service;

import static com.nbamanager.domain.replay.ReplayQualityLevel.FULL;
import static com.nbamanager.domain.replay.ReplayQualityLevel.PARTIAL;
import static com.nbamanager.domain.replay.ReplayQualityLevel.SUMMARY_ONLY;
import static com.nbamanager.domain.replay.ReplayQualityLevel.UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;

import com.nbamanager.domain.MatchRecord;
import com.nbamanager.domain.PlayByPlay;
import com.nbamanager.web.dto.ReplayQualityDto;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReplayQualityServiceTest {

    private static final LocalDateTime SYNCED_AT = LocalDateTime.parse("2026-07-20T21:00:00");

    private final ReplayQualityService service =
            new ReplayQualityService(new ReplayFactsHashService());

    @Test
    void classifiesDenseVerifiedPlayByPlayAsFull() {
        List<PlayByPlay> events = completeEvents();
        MatchRecord match = finishedMatch(40, 30);

        ReplayQualityDto quality = service.evaluate(events, match, SYNCED_AT);

        assertThat(quality.level()).isEqualTo(FULL);
        assertThat(quality.eventCount()).isEqualTo(120);
        assertThat(quality.scoringEventCount()).isEqualTo(60);
        assertThat(quality.periodsCovered()).containsExactly(1, 2, 3, 4);
        assertThat(quality.source()).isEqualTo("NBA_PLAY_BY_PLAY");
        assertThat(quality.sourceLabel()).isEqualTo("NBA 官方逐回合");
        assertThat(quality.synthetic()).isFalse();
        assertThat(quality.finalScoreVerified()).isTrue();
        assertThat(quality.monotonicScores()).isTrue();
        assertThat(quality.lastSyncedAt()).isEqualTo(SYNCED_AT);
        assertThat(quality.warnings()).isEmpty();
        assertThat(quality.factsHash()).matches("[0-9a-f]{64}");
    }

    @Test
    void periodScoreOnlyIsAlwaysSummaryOnlyEvenWhenSourceClaimsPlayByPlay() {
        List<PlayByPlay> events = new ArrayList<>();
        for (int period = 1; period <= 4; period++) {
            PlayByPlay event = event(period, period, "00:00", "period_score",
                    period * 25, period * 23, false);
            event.setActionType("period_score");
            event.setSource("NBA_PLAY_BY_PLAY");
            events.add(event);
        }

        ReplayQualityDto quality = service.evaluate(events, finishedMatch(100, 92), SYNCED_AT);

        assertThat(quality.level()).isEqualTo(SUMMARY_ONLY);
        assertThat(quality.synthetic()).isTrue();
        assertThat(quality.warnings()).contains("SUMMARY_EVENTS_ONLY");
    }

    @Test
    void summerScoreboardSourceUsesAStablePublicLabel() {
        List<PlayByPlay> events = new ArrayList<>();
        for (int period = 1; period <= 4; period++) {
            PlayByPlay event = event(period, period, "00:00", "period_score",
                    period * 25, period * 23, false);
            event.setSource("ESPN_NBA_SUMMER_SCOREBOARD");
            events.add(event);
        }

        ReplayQualityDto quality = service.evaluate(events, finishedMatch(100, 92), SYNCED_AT);

        assertThat(quality.level()).isEqualTo(SUMMARY_ONLY);
        assertThat(quality.source()).isEqualTo("ESPN_NBA_SUMMER_SCOREBOARD");
        assertThat(quality.sourceLabel()).isEqualTo("ESPN 夏季联赛节末比分");
    }

    @Test
    void realButSparseEventsArePartial() {
        List<PlayByPlay> events = completeEvents().subList(0, 20);

        ReplayQualityDto quality = service.evaluate(events, finishedMatch(40, 30), SYNCED_AT);

        assertThat(quality.level()).isEqualTo(PARTIAL);
        assertThat(quality.warnings())
                .contains("INSUFFICIENT_PERIOD_COVERAGE", "LOW_EVENT_DENSITY", "FINAL_SCORE_NOT_VERIFIED");
    }

    @Test
    void emptyFactsAreUnavailable() {
        ReplayQualityDto quality = service.evaluate(List.of(), finishedMatch(100, 90), SYNCED_AT);

        assertThat(quality.level()).isEqualTo(UNAVAILABLE);
        assertThat(quality.eventCount()).isZero();
        assertThat(quality.periodsCovered()).isEmpty();
        assertThat(quality.factsHash()).isNull();
        assertThat(quality.warnings()).containsExactly("NO_REPLAY_EVENTS");
    }

    @Test
    void nonIncreasingEventOrderPreventsFullClassification() {
        List<PlayByPlay> events = completeEvents();
        PlayByPlay first = events.get(0);
        events.set(0, events.get(1));
        events.set(1, first);

        ReplayQualityDto quality = service.evaluate(events, finishedMatch(40, 30), SYNCED_AT);

        assertThat(quality.level()).isEqualTo(PARTIAL);
        assertThat(quality.monotonicScores()).isTrue();
        assertThat(quality.warnings()).contains("EVENT_ORDER_NOT_STRICTLY_INCREASING");
    }

    @Test
    void scoreRollbackIsReportedAndPreventsFullClassification() {
        List<PlayByPlay> events = completeEvents();
        events.get(60).setHomeScore(events.get(59).getHomeScore() - 1);

        ReplayQualityDto quality = service.evaluate(events, finishedMatch(40, 30), SYNCED_AT);

        assertThat(quality.level()).isEqualTo(PARTIAL);
        assertThat(quality.monotonicScores()).isFalse();
        assertThat(quality.warnings()).contains("SCORES_NOT_MONOTONIC");
    }

    @Test
    void liveMatchCannotClaimVerifiedFullCoverage() {
        MatchRecord match = finishedMatch(40, 30);
        match.setStatus("LIVE");

        ReplayQualityDto quality = service.evaluate(completeEvents(), match, SYNCED_AT);

        assertThat(quality.level()).isEqualTo(PARTIAL);
        assertThat(quality.finalScoreVerified()).isFalse();
        assertThat(quality.warnings()).contains("FINAL_SCORE_NOT_VERIFIED");
    }

    private List<PlayByPlay> completeEvents() {
        List<PlayByPlay> events = new ArrayList<>();
        int homeScore = 0;
        int awayScore = 0;
        for (int order = 1; order <= 120; order++) {
            boolean homeScored = order % 3 == 0;
            boolean awayScored = order % 4 == 0;
            if (homeScored) {
                homeScore++;
            }
            if (awayScored) {
                awayScore++;
            }
            int period = ((order - 1) / 30) + 1;
            PlayByPlay event = event(order, period, String.format("%02d:%02d", 12 - (order % 12), order % 60),
                    homeScored || awayScored ? "made_shot" : "rebound",
                    homeScore, awayScore, homeScored || awayScored);
            event.setSource("NBA_PLAY_BY_PLAY");
            events.add(event);
        }
        return events;
    }

    private PlayByPlay event(
            int order,
            int period,
            String clock,
            String type,
            int homeScore,
            int awayScore,
            boolean scoring) {
        PlayByPlay event = new PlayByPlay();
        event.setEventOrder(order);
        event.setPeriod(period);
        event.setGameClock(clock);
        event.setEventType(type);
        event.setHomeScore(homeScore);
        event.setAwayScore(awayScore);
        event.setIsScoringPlay(scoring);
        event.setSourceEventId("source-" + order);
        event.setSource("NBA_PLAY_BY_PLAY");
        return event;
    }

    private MatchRecord finishedMatch(int homeScore, int awayScore) {
        MatchRecord match = new MatchRecord();
        match.setStatus("FINISHED");
        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        return match;
    }
}
