package com.nbamanager.service.provider;

import java.util.Locale;

/**
 * Unified event classification for Play-by-Play events.
 * Uses score change as primary signal, text matching as fallback.
 */
public final class PlayByPlayEventClassifier {

    private PlayByPlayEventClassifier() {}

    /**
     * Determine if an event is a scoring play.
     * Priority: score change > text keywords > description patterns.
     */
    public static boolean isScoringPlay(
            String eventType,
            String actionType,
            String description,
            int homeScore,
            int awayScore,
            int previousHomeScore,
            int previousAwayScore
    ) {
        String text = normalize(eventType + " " + actionType + " " + description);

        boolean scoreChanged = homeScore != previousHomeScore || awayScore != previousAwayScore;
        boolean missed = text.contains("miss") || text.contains("missed") || text.contains("不中");

        // Most reliable: score actually changed and it's not a miss
        if (scoreChanged && !missed) {
            return true;
        }

        // Fallback: text-based detection for cases where score data is delayed
        boolean hasPts = text.contains("pts") || text.contains("得分") || text.contains("命中");
        boolean madeKeyword = text.contains("made") || text.contains("makes")
                || text.contains("make") || text.contains("命中");

        boolean scoringAction = text.contains("freethrow") || text.contains("free throw")
                || text.contains("2pt") || text.contains("3pt")
                || text.contains("two point") || text.contains("three point")
                || text.contains("jump shot") || text.contains("layup")
                || text.contains("dunk") || text.contains("hook shot")
                || text.contains("tip shot") || text.contains("tip in")
                || text.contains("floating jump shot") || text.contains("driving")
                || text.contains("pullup") || text.contains("putback")
                || text.contains("上篮") || text.contains("扣篮")
                || text.contains("跳投") || text.contains("罚球")
                || text.contains("三分") || text.contains("补篮");

        return scoringAction && (hasPts || madeKeyword) && !missed;
    }

    /**
     * Determine if an event is a highlight candidate.
     * NOT all scoring plays are highlights. Only significant moments.
     */
    public static boolean isHighlightCandidate(
            String eventType,
            String actionType,
            String description,
            boolean isScoringPlay,
            int homeScore,
            int awayScore,
            int previousHomeScore,
            int previousAwayScore,
            int period,
            String gameClock
    ) {
        String text = normalize(eventType + " " + actionType + " " + description);

        // Always-highlight types
        boolean isDunk = text.contains("dunk") || text.contains("扣篮");
        boolean isThree = text.contains("3pt") || text.contains("three point") || text.contains("三分");
        boolean isBlock = text.contains("block") || text.contains("盖帽");
        boolean isSteal = text.contains("steal") || text.contains("抢断");

        if (isDunk || isBlock || isSteal) return true;
        if (isThree && isScoringPlay) return true;

        // Game state analysis
        int prevMargin = previousHomeScore - previousAwayScore;
        int margin = homeScore - awayScore;

        // Tie or lead change
        boolean tieOrLeadChange = isScoringPlay && (
                margin == 0 || (prevMargin != 0 && Integer.signum(prevMargin) != Integer.signum(margin))
        );

        // Clutch time: last 5 minutes of Q4/OT, close game
        int clockSeconds = clockToSeconds(gameClock);
        boolean clutchTime = period >= 4 && clockSeconds <= 300;
        boolean clutchScore = isScoringPlay && clutchTime && Math.abs(margin) <= 8;

        // Double-digit lead swing
        boolean doubleDigitSwing = isScoringPlay && Math.abs(margin) >= 10 && Math.abs(prevMargin) < 10;

        return tieOrLeadChange || clutchScore || doubleDigitSwing;
    }

    private static int clockToSeconds(String gameClock) {
        if (gameClock == null || gameClock.isEmpty()) return 999;
        try {
            String[] parts = gameClock.split(":");
            return Integer.parseInt(parts[0].trim()) * 60 + Integer.parseInt(parts[1].trim());
        } catch (Exception e) {
            return 999;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace("-", " ");
    }
}
