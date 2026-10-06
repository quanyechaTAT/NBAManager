package com.nbamanager.service;

import com.nbamanager.domain.MatchRecord;
import com.nbamanager.web.dto.DataMeta;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/** Season-scoped sample statistics. A streak ending never stops aggregation. */
public record TeamMatchStatistics(String homeRecord, String awayRecord, Double pointsPerGame,
                                  Double oppPointsPerGame, Double pointDifferential, String streak, DataMeta dataMeta) {
    public static TeamMatchStatistics calculate(String team, String season, List<MatchRecord> records) {
        var sample = records.stream().filter(m -> "FINISHED".equals(m.getStatus()))
                .filter(m -> season != null && season.equals(m.getSeason()) && "REGULAR_SEASON".equals(m.getGameType()))
                .filter(m -> m.canDisplay("homeScore") && m.canDisplay("awayScore"))
                .filter(m -> m.getHomeScore() != null && m.getAwayScore() != null && m.getMatchDate() != null)
                .filter(m -> m.getHomeScore() >= 0 && m.getAwayScore() >= 0 && !m.getHomeScore().equals(m.getAwayScore()))
                .filter(m -> team.equals(m.getHomeTeam()) || team.equals(m.getAwayTeam()))
                .sorted(Comparator.comparing(MatchRecord::getMatchDate).reversed()).toList();
        int hw = 0, hl = 0, aw = 0, al = 0, scored = 0, allowed = 0, streak = 0;
        boolean streakEnded = false;
        for (var match : sample) {
            boolean home = team.equals(match.getHomeTeam());
            int own = home ? match.getHomeScore() : match.getAwayScore();
            int opponent = home ? match.getAwayScore() : match.getHomeScore();
            boolean won = own > opponent;
            scored += own; allowed += opponent;
            if (home) { if (won) hw++; else hl++; } else { if (won) aw++; else al++; }
            if (!streakEnded) {
                if (streak == 0 || won == (streak > 0)) streak += won ? 1 : -1;
                else streakEnded = true;
            }
        }
        int n = sample.size();
        var states = new LinkedHashMap<String, String>();
        for (String field : List.of("homeRecord", "awayRecord", "pointsPerGame", "oppPointsPerGame", "pointDifferential", "streak"))
            states.put(field, n == 0 ? "MISSING" : "DERIVED");
        var fetched = sample.stream().map(MatchRecord::getDataSyncedAt).filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        var meta = new DataMeta("LOCAL_MATCH_SAMPLE", null, season, "REGULAR_SEASON_SAMPLE", null,
                fetched, "PARTIAL", n, states);
        return new TeamMatchStatistics(n == 0 ? null : hw + "-" + hl, n == 0 ? null : aw + "-" + al,
                n == 0 ? null : round((double) scored / n), n == 0 ? null : round((double) allowed / n),
                n == 0 ? null : round((double) (scored - allowed) / n),
                n == 0 ? null : Math.abs(streak) + (streak > 0 ? "连胜" : "连败"), meta);
    }
    private static double round(double value) { return Math.round(value * 10.0) / 10.0; }
}
