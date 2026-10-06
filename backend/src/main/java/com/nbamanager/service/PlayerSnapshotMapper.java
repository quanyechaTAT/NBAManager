package com.nbamanager.service;

import com.nbamanager.domain.Player;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;
import org.springframework.beans.BeanWrapperImpl;

/** The provider-to-domain boundary. Each successful season snapshot replaces all supported fields. */
public final class PlayerSnapshotMapper {
    private PlayerSnapshotMapper() {}
    public static final Map<String, String> FIELDS = fields();
    private static Map<String, String> fields() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("pointsPerGame", "ppg"); map.put("reboundsPerGame", "rpg"); map.put("assistsPerGame", "apg");
        map.put("stealsPerGame", "spg"); map.put("blocksPerGame", "bpg"); map.put("turnoversPerGame", "tpg");
        map.put("gamesPlayed", "gp"); map.put("minutesPerGame", "mpg");
        map.put("fieldGoalPct", "fgPct"); map.put("threePointPct", "threePct"); map.put("freeThrowPct", "ftPct");
        map.put("trueShootingPct", "tsPct"); map.put("usagePct", "usgPct"); map.put("efficiency", "efficiency");
        map.put("per", "per"); map.put("winShares", "winShares"); map.put("vorp", "vorp"); map.put("bpm", "bpm");
        map.put("offensiveRating", "offensiveRating"); map.put("defensiveRating", "defensiveRating");
        map.put("position", "position"); map.put("jerseyNumber", "jersey"); map.put("height", "height"); map.put("weight", "weight"); map.put("country", "country");
        return java.util.Collections.unmodifiableMap(map);
    }
    public static Double number(JSONObject data, String key) {
        if (!data.has(key) || data.isNull(key)) return null;
        Object raw = data.opt(key);
        try { double value = raw instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(raw)); return Double.isFinite(value) ? value : null; }
        catch (NumberFormatException ignored) { return null; }
    }
    public static String text(JSONObject data, String key) {
        return data.has(key) && !data.isNull(key) && !data.optString(key).isBlank() ? data.optString(key).trim() : null;
    }
    public static void apply(Player player, JSONObject data, String season) {
        BeanWrapperImpl bean = new BeanWrapperImpl(player);
        FIELDS.forEach((field, key) -> {
            Class<?> type = bean.getPropertyType(field);
            Object value;
            if (type == String.class) value = text(data, key);
            else {
                Double number = number(data, key);
                if (type == Integer.class) value = number == null ? null : Integer.valueOf(number.intValue());
                else value = number;
            }
            bean.setPropertyValue(field, value);
        });
        player.setNameEn(text(data, "nameEn"));
        player.setTranslationStatus(text(data, "translationStatus"));
        player.markData("NBA_API", season, "REGULAR_SEASON", FIELDS.keySet().toArray(String[]::new));
        player.setDataSourceUrl("https://www.nba.com/stats/players/traditional");
    }
}
