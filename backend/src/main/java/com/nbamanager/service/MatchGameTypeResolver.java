package com.nbamanager.service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

public final class MatchGameTypeResolver {

    public static final String SUMMER_LEAGUE = "SUMMER_LEAGUE";
    public static final String REGULAR_SEASON = "REGULAR_SEASON";
    public static final String PLAYOFFS = "PLAYOFFS";
    public static final String NBA_CUP = "NBA_CUP";

    private static final Set<String> SUPPORTED = Set.of(
            SUMMER_LEAGUE, REGULAR_SEASON, PLAYOFFS, NBA_CUP);

    private MatchGameTypeResolver() {}

    public static String resolve(String explicitType, String nbaGameId, LocalDate matchDate) {
        String normalized = normalize(explicitType);
        if (SUPPORTED.contains(normalized)) {
            return normalized;
        }

        String gameId = nbaGameId == null ? "" : nbaGameId.trim();
        if (gameId.startsWith("004")) {
            return PLAYOFFS;
        }
        if (gameId.startsWith("006")) {
            return NBA_CUP;
        }
        if (gameId.startsWith("401") && matchDate != null
                && matchDate.getMonthValue() >= 6 && matchDate.getMonthValue() <= 8) {
            return SUMMER_LEAGUE;
        }
        return REGULAR_SEASON;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "SUMMER", "SUMMER_GAME", "SUMMER LEAGUE" -> SUMMER_LEAGUE;
            case "REGULAR", "REGULAR SEASON" -> REGULAR_SEASON;
            case "PLAYOFF", "POSTSEASON" -> PLAYOFFS;
            case "CUP", "NBA CUP", "IN_SEASON_TOURNAMENT" -> NBA_CUP;
            default -> value.trim().toUpperCase(Locale.ROOT);
        };
    }
}
