package com.nbamanager.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PlayoffReferenceData {

    public boolean supports(String season) {
        return "2025-26".equals(season);
    }

    public List<Map<String, Object>> getMatchups(String season) {
        if (!supports(season)) {
            return List.of();
        }

        List<Map<String, Object>> matchups = new ArrayList<>();

        matchups.add(matchup("2026E101", "East", 1, "活塞", 4, "魔术", 3, "活塞", 1, 8));
        matchups.add(matchup("2026E102", "East", 1, "骑士", 4, "猛龙", 3, "骑士", 4, 5));
        matchups.add(matchup("2026E103", "East", 1, "尼克斯", 4, "老鹰", 2, "尼克斯", 2, 7));
        matchups.add(matchup("2026E104", "East", 1, "凯尔特人", 3, "76人", 4, "76人", 3, 6));
        matchups.add(matchup("2026E201", "East", 2, "活塞", 3, "骑士", 4, "骑士", null, null));
        matchups.add(matchup("2026E202", "East", 2, "尼克斯", 4, "76人", 0, "尼克斯", null, null));
        matchups.add(matchup("2026E301", "East", 3, "骑士", 0, "尼克斯", 4, "尼克斯", null, null));

        matchups.add(matchup("2026W101", "West", 1, "雷霆", 4, "太阳", 0, "雷霆", 1, 8));
        matchups.add(matchup("2026W102", "West", 1, "湖人", 4, "火箭", 2, "湖人", 4, 5));
        matchups.add(matchup("2026W103", "West", 1, "掘金", 2, "森林狼", 4, "森林狼", 2, 7));
        matchups.add(matchup("2026W104", "West", 1, "马刺", 4, "开拓者", 1, "马刺", 3, 6));
        matchups.add(matchup("2026W201", "West", 2, "雷霆", 4, "湖人", 0, "雷霆", null, null));
        matchups.add(matchup("2026W202", "West", 2, "森林狼", 2, "马刺", 4, "马刺", null, null));
        matchups.add(matchup("2026W301", "West", 3, "雷霆", 3, "马刺", 4, "马刺", null, null));

        matchups.add(matchup("2026F001", "Finals", 4, "尼克斯", 4, "马刺", 1, "尼克斯", null, null));

        return matchups;
    }

    private Map<String, Object> matchup(
            String seriesId,
            String conference,
            int round,
            String team1Name,
            int team1Wins,
            String team2Name,
            int team2Wins,
            String winner,
            Integer team1Seed,
            Integer team2Seed) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("seriesId", seriesId);
        m.put("conference", conference);
        m.put("round", round);
        m.put("team1Name", team1Name);
        m.put("team1NameEn", "");
        m.put("team1Seed", team1Seed);
        m.put("team1Wins", team1Wins);
        m.put("team2Name", team2Name);
        m.put("team2NameEn", "");
        m.put("team2Seed", team2Seed);
        m.put("team2Wins", team2Wins);
        m.put("status", "COMPLETED");
        m.put("winner", winner);
        return m;
    }
}
