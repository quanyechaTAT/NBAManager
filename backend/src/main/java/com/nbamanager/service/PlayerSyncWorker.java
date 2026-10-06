package com.nbamanager.service;

import com.nbamanager.repository.SyncJobRepository;
import com.nbamanager.service.SyncJobStateService;
import com.nbamanager.web.dto.SyncResult;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PlayerSyncWorker extends ModuleSyncWorker {

    private final PythonScriptRunner runner;
    private final com.nbamanager.repository.PlayerRepository playerRepository;
    private final com.nbamanager.repository.TeamRepository teamRepository;

    public PlayerSyncWorker(SyncJobRepository repo, SyncJobStateService jobStateService, PythonScriptRunner runner,
                            com.nbamanager.repository.PlayerRepository playerRepository,
                            com.nbamanager.repository.TeamRepository teamRepository) {
        super(repo, jobStateService);
        this.runner = runner;
        this.playerRepository = playerRepository;
        this.teamRepository = teamRepository;
    }

    @Override
    protected SyncResult doSync(Long jobId) {
        updateProgress(jobId, 10);
        PythonScriptRunner.PythonResult pyResult = runner.runForJob("nba_data_fetcher.py", new String[]{"all"}, 120, jobId);
        if (!pyResult.success()) {
            return SyncResult.failed("PLAYERS", "PYTHON_FAILED", pyResult.stderr().substring(0, Math.min(200, pyResult.stderr().length())));
        }

        updateProgress(jobId, 40);
        if (isCancelled(jobId)) return SyncResult.cancelled("PLAYERS");

        try {
            JSONObject data = new JSONObject(pyResult.stdout());
            if (!data.has("players")) {
                return SyncResult.failed("PLAYERS", "NO_DATA", "Python返回无球员数据");
            }

            JSONArray players = data.getJSONArray("players");
            if (players.length() < 100) {
                return SyncResult.failed("PLAYERS", "QUALITY_GATE", "球员数量过少: " + players.length());
            }

            updateProgress(jobId, 60);
            int inserted = 0, updated = 0, skipped = 0;

            for (int i = 0; i < players.length(); i++) {
                if (isCancelled(jobId)) return SyncResult.cancelled("PLAYERS");
                if (i % 50 == 0) updateProgress(jobId, 60 + (i * 30 / players.length()));
                JSONObject p = players.getJSONObject(i);
                long nbaPlayerId = p.optLong("nbaPlayerId", 0);
                String name = p.getString("name");
                String teamName = p.getString("team");

                var team = teamRepository.findByName(teamName);
                if (team == null) { skipped++; continue; }

                var existing = nbaPlayerId > 0 ? playerRepository.findByNbaPlayerId(nbaPlayerId) : null;
                if (existing == null) existing = playerRepository.findByNameAndTeamId(name, team.getId());
                if (existing == null && nbaPlayerId <= 0) existing = playerRepository.findByName(name);

                var player = existing != null ? existing : new com.nbamanager.domain.Player();
                player.setNbaPlayerId(nbaPlayerId > 0 ? nbaPlayerId : player.getNbaPlayerId());
                player.setName(name); player.setTeam(team);
                String season = p.optString("season", data.optString("season", null));
                PlayerSnapshotMapper.apply(player, p, season);
                playerRepository.save(player);
                if (existing == null) inserted++; else updated++;

            }

            updateProgress(jobId, 95);
            log.info("球员同步完成: 新增{}, 更新{}, 跳过{}", inserted, updated, skipped);
            return SyncResult.success("PLAYERS", players.length(), inserted, updated, skipped,
                    LocalDateTime.now(), "nba_api");
        } catch (Exception e) {
            log.error("球员数据处理失败", e);
            return SyncResult.failed("PLAYERS", "PARSE_ERROR", e.getMessage());
        }
    }
}
