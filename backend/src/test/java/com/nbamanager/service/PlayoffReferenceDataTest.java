package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlayoffReferenceDataTest {

    private final PlayoffReferenceData data = new PlayoffReferenceData();

    @Test
    void providesCorrect2026PlayoffBracketFromReferenceImage() {
        List<Map<String, Object>> matchups = data.getMatchups("2025-26");

        assertThat(matchups).hasSize(15);
        assertMatchup(matchups, "East", 1, "活塞", 4, "魔术", 3, "活塞");
        assertMatchup(matchups, "East", 2, "活塞", 3, "骑士", 4, "骑士");
        assertMatchup(matchups, "East", 3, "骑士", 0, "尼克斯", 4, "尼克斯");
        assertMatchup(matchups, "West", 1, "马刺", 4, "开拓者", 1, "马刺");
        assertMatchup(matchups, "West", 2, "森林狼", 2, "马刺", 4, "马刺");
        assertMatchup(matchups, "West", 3, "雷霆", 3, "马刺", 4, "马刺");
        assertMatchup(matchups, "Finals", 4, "尼克斯", 4, "马刺", 1, "尼克斯");
    }

    private void assertMatchup(
            List<Map<String, Object>> matchups,
            String conference,
            int round,
            String team1,
            int team1Wins,
            String team2,
            int team2Wins,
            String winner) {
        assertThat(matchups)
                .anySatisfy(m -> {
                    assertThat(m.get("conference")).isEqualTo(conference);
                    assertThat(m.get("round")).isEqualTo(round);
                    assertThat(m.get("team1Name")).isEqualTo(team1);
                    assertThat(m.get("team1Wins")).isEqualTo(team1Wins);
                    assertThat(m.get("team2Name")).isEqualTo(team2);
                    assertThat(m.get("team2Wins")).isEqualTo(team2Wins);
                    assertThat(m.get("winner")).isEqualTo(winner);
                });
    }
}
