package com.nbamanager.service;

import com.nbamanager.domain.Player;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PlayerSnapshotMapperTest {
    @Test void preservesLegitimateUsageAndZeroWithoutInventingMissingFields() {
        Player player = new Player(); player.setId(7L); player.setNbaPlayerId(202L);
        PlayerSnapshotMapper.apply(player, new JSONObject("{\"usgPct\":20,\"ppg\":0,\"fgPct\":0.5,\"E_OFF_RATING\":110}"), "2025-26");
        assertThat(player.getId()).isEqualTo(7L); assertThat(player.getNbaPlayerId()).isEqualTo(202L);
        assertThat(player.getUsagePct()).isEqualTo(20); assertThat(player.getPointsPerGame()).isZero();
        assertThat(player.getTrueShootingPct()).isNull(); assertThat(player.getPer()).isNull();
        assertThat(player.getGamesPlayed()).isNull(); assertThat(player.getHeight()).isNull();
        assertThat(player.getWeight()).isNull(); assertThat(player.getCountry()).isNull();
        assertThat(player.getDataMeta().fieldStates()).containsEntry("usagePct", "OBSERVED").containsEntry("per", "MISSING");
    }
    @Test void successfulSnapshotRemovesStaleFieldWhileKeepingPlayerIdentity() {
        Player player = new Player(); player.setId(7L);
        PlayerSnapshotMapper.apply(player, new JSONObject("{\"usgPct\":20,\"tsPct\":0.6,\"gp\":65}"), "2024-25");
        PlayerSnapshotMapper.apply(player, new JSONObject("{\"ppg\":11,\"offensiveRating\":110}"), "2025-26");
        assertThat(player.getId()).isEqualTo(7L); assertThat(player.getUsagePct()).isNull();
        assertThat(player.getTrueShootingPct()).isNull(); assertThat(player.getGamesPlayed()).isNull();
        assertThat(player.getOffensiveRating()).isEqualTo(110); assertThat(player.getPer()).isNull();
    }
    @Test void legacyValuesRemainStoredButCannotBePublishedWithoutEvidence() {
        Player player = new Player(); player.setUsagePct(20.0); player.setPointsPerGame(0.0);
        assertThat(player.getUsagePct()).isNull(); assertThat(player.getPointsPerGame()).isNull();
        player.markData("MANUAL", "2025-26", "REGULAR_SEASON", "usagePct", "pointsPerGame");
        assertThat(player.getUsagePct()).isEqualTo(20); assertThat(player.getPointsPerGame()).isZero();
        player.setDataFieldStates("{\"usagePct\":\"UNVERIFIED\",\"pointsPerGame\":\"OBSERVED\"}");
        assertThat(player.getUsagePct()).isNull(); assertThat(player.getPointsPerGame()).isZero();
    }
    @Test void ignoresInvalidNumericValues() {
        assertThat(PlayerSnapshotMapper.number(new JSONObject("{\"x\":\"NaN\"}"), "x")).isNull();
        assertThat(PlayerSnapshotMapper.number(new JSONObject("{\"x\":null}"), "x")).isNull();
        assertThat(PlayerSnapshotMapper.number(new JSONObject("{\"x\":true}"), "x")).isNull();
    }
}
