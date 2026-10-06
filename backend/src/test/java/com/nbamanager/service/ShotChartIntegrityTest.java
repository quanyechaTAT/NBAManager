package com.nbamanager.service;

import com.nbamanager.domain.Player;
import com.nbamanager.domain.PlayerShotChart;
import com.nbamanager.repository.PlayerRepository;
import com.nbamanager.repository.PlayerShotChartRepository;
import com.nbamanager.repository.GameShotChartRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ShotChartIntegrityTest {
    private JSONArray shots() { return new JSONArray("[{\"x\":0,\"y\":20,\"made\":true,\"gameId\":\"g1\",\"shotType\":\"2PT Field Goal\"},{\"x\":30,\"y\":40,\"made\":false,\"gameId\":\"g1\",\"shotType\":\"3PT Field Goal\"}]"); }
    @Test void missingCoordinatesNeverProducePointsAndMissingDatesStayMissing() {
        JSONArray input = shots(); input.put(new JSONObject("{\"y\":40,\"made\":true}")); input.put(new JSONObject("{\"x\":10,\"y\":40}"));
        var rows = ShotChartService.parsePlayerShots(7L, 202L, "2025-26", input);
        assertThat(rows).hasSize(2); assertThat(rows).allSatisfy(row -> { assertThat(row.getGameDate()).isNull(); assertThat(row.getPlayerId()).isEqualTo(7); });
        assertThat(rows.get(0).getX()).isZero(); assertThat(rows.get(0).getShotType()).isEqualTo("2PT Field Goal");
    }
    @Test void repeatedFullSnapshotsReplaceTheCollectionInsteadOfAppending() {
        var repo = mock(PlayerShotChartRepository.class); var players = mock(PlayerRepository.class); var runner = mock(PythonScriptRunner.class);
        Player player = new Player(); player.setId(7L); player.setNbaPlayerId(202L);
        when(players.findById(7L)).thenReturn(Optional.of(player));
        List<PlayerShotChart> persisted = new ArrayList<>();
        when(repo.findByNbaPlayerIdAndSeason(202L, "2025-26")).thenAnswer(inv -> List.copyOf(persisted));
        doAnswer(inv -> { persisted.clear(); return null; }).when(repo).deleteByNbaPlayerIdAndSeason(202L, "2025-26");
        when(repo.saveAll(any())).thenAnswer(inv -> { ((Iterable<PlayerShotChart>) inv.getArgument(0)).forEach(persisted::add); return persisted; });
        when(runner.execute(eq("shot_chart"), any(String[].class), eq(60))).thenAnswer(inv -> new JSONObject().put("source", "NBA_SHOTCHARTDETAIL").put("status", "OK").put("season", "2025-26").put("shots", shots()));
        for (int i = 0; i < 2; i++) {
            persisted.forEach(row -> row.setDataSyncedAt(Instant.now().minusSeconds(900)));
            var service = new ShotChartService(repo, players, runner, mock(GameShotChartRepository.class));
            var result = service.getPlayerShots(7L, "2025-26");
            assertThat((List<?>) result.get("shots")).hasSize(2);
            assertThat(((java.util.Map<?, ?>) result.get("summary")).get("totalShots")).isEqualTo(2);
            assertThat(((java.util.Map<?, ?>) result.get("summary")).get("made")).isEqualTo(1);
        }
        assertThat(persisted).hasSize(2); verify(repo, times(2)).deleteByNbaPlayerIdAndSeason(202L, "2025-26");
    }
    @Test void sourceFailureKeepsVerifiedCacheAndItsOriginalTimestamp() {
        var repo = mock(PlayerShotChartRepository.class); var players = mock(PlayerRepository.class); var runner = mock(PythonScriptRunner.class);
        Player player = new Player(); player.setId(7L); player.setNbaPlayerId(202L); when(players.findById(7L)).thenReturn(Optional.of(player));
        var rows = ShotChartService.parsePlayerShots(7L, 202L, "2025-26", shots());
        Instant fetched = Instant.now().minusSeconds(900); rows.forEach(row -> row.setDataSyncedAt(fetched));
        when(repo.findByNbaPlayerIdAndSeason(202L, "2025-26")).thenReturn(rows);
        when(runner.execute(eq("shot_chart"), any(String[].class), eq(60))).thenReturn(new JSONObject().put("status", "FAILED"));
        var result = new ShotChartService(repo, players, runner, mock(GameShotChartRepository.class)).getPlayerShots(7L, "2025-26");
        assertThat(result.get("status")).isEqualTo("STALE"); assertThat((List<?>)result.get("shots")).hasSize(2);
        assertThat(((com.nbamanager.web.dto.DataMeta) result.get("dataMeta")).syncedAt()).isEqualTo(fetched);
        verify(repo, never()).deleteByNbaPlayerIdAndSeason(anyLong(), anyString()); verify(repo, never()).saveAll(any());
    }
    @Test void transactionRollbackCannotPublishAnUncommittedMemorySnapshot() {
        var repo = mock(PlayerShotChartRepository.class); var players = mock(PlayerRepository.class); var runner = mock(PythonScriptRunner.class);
        Player player = new Player(); player.setNbaPlayerId(202L); when(players.findById(7L)).thenReturn(Optional.of(player));
        when(repo.findByNbaPlayerIdAndSeason(202L, "2025-26")).thenReturn(List.of());
        when(runner.execute(eq("shot_chart"), any(String[].class), eq(60))).thenAnswer(inv -> new JSONObject().put("source", "NBA_SHOTCHARTDETAIL").put("season", "2025-26").put("shots", shots()));
        var service = new ShotChartService(repo, players, runner, mock(GameShotChartRepository.class));
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try { service.getPlayerShots(7L, "2025-26");
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
        } finally { org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization(); }
        service.getPlayerShots(7L, "2025-26");
        verify(runner, times(2)).execute(eq("shot_chart"), any(String[].class), eq(60));
    }
}
