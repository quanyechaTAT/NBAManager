package com.nbamanager.service;
import java.time.LocalDate;

/** Prefer the supplied season, then official game ID, then the game's date. */
public final class MatchSeasonResolver {
    private MatchSeasonResolver() {}
    public static String resolve(String season, String gameId, LocalDate date) {
        if (season != null && season.matches("\\d{4}-\\d{2}")) return season;
        Integer start = gameId != null && gameId.matches("00[1245]\\d{7}") ? 2000 + Integer.parseInt(gameId.substring(3, 5)) : null;
        if (start == null && date != null) start = date.getMonthValue() >= 10 ? date.getYear() : date.getYear() - 1;
        return start == null ? null : start + "-" + String.format("%02d", (start + 1) % 100);
    }
}
