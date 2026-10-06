package com.nbamanager.service;

import com.nbamanager.domain.MatchRecord;
import com.nbamanager.repository.MatchRecordRepository;
import com.nbamanager.repository.SyncJobRepository;
import com.nbamanager.service.SyncJobStateService;
import com.nbamanager.web.dto.SyncResult;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MatchSyncWorker extends ModuleSyncWorker {

    private final PythonScriptRunner runner;
    private final MatchRecordRepository matchRecordRepository;

    public MatchSyncWorker(SyncJobRepository repo, SyncJobStateService jobStateService, PythonScriptRunner runner, MatchRecordRepository matchRecordRepository) {
        super(repo, jobStateService);
        this.runner = runner;
        this.matchRecordRepository = matchRecordRepository;
    }

    @Override
    protected SyncResult doSync(Long jobId) {
        updateProgress(jobId, 10);
        PythonScriptRunner.PythonResult pyResult = runner.runForJob("nba_data_fetcher.py", new String[]{"all"}, 120, jobId);
        if (!pyResult.success()) {
            return SyncResult.failed("MATCHES", "PYTHON_FAILED", pyResult.stderr().substring(0, Math.min(200, pyResult.stderr().length())));
        }

        updateProgress(jobId, 40);
        if (isCancelled(jobId)) return SyncResult.cancelled("MATCHES");

        try {
            JSONObject data = new JSONObject(pyResult.stdout());
            if (!data.has("games")) {
                return SyncResult.failed("MATCHES", "NO_DATA", "Python返回无比赛数据");
            }

            JSONArray games = data.getJSONArray("games");
            updateProgress(jobId, 60);
            int inserted = 0, updated = 0, skipped = 0;

            for (int i = 0; i < games.length(); i++) {
                if (isCancelled(jobId)) return SyncResult.cancelled("MATCHES");
                JSONObject g = games.getJSONObject(i);
                String homeTeam = g.getString("homeTeam");
                String awayTeam = g.getString("awayTeam");
                String dateStr = g.getString("date");
                String nbaGameId = g.optString("gameId", null);
                String status = g.optString("status", "FINISHED");
                int homeScore = g.getInt("homeScore");
                int awayScore = g.getInt("awayScore");
                LocalDate matchDate = LocalDate.parse(dateStr);

                // Upsert by nbaGameId first
                MatchRecord existing = null;
                if (nbaGameId != null && !nbaGameId.isEmpty()) {
                    existing = matchRecordRepository.findByNbaGameId(nbaGameId).orElse(null);
                }
                // Fallback to team+date
                if (existing == null) {
                    existing = matchRecordRepository.findByHomeTeamAndAwayTeamAndMatchDate(homeTeam, awayTeam, matchDate).orElse(null);
                }

                if (existing != null) {
                    existing.setHomeScore(homeScore);
                    existing.setAwayScore(awayScore);
                    existing.setStatus(status);
                    if (nbaGameId != null && !nbaGameId.isEmpty()) existing.setNbaGameId(nbaGameId);
                    existing.setGameType(MatchGameTypeResolver.resolve(
                            g.optString("gameType", existing.getGameType()), nbaGameId, matchDate));
                    existing.setSeason(MatchSeasonResolver.resolve(g.optString("season", null), nbaGameId, matchDate));
                    existing.markData("NBA_API", existing.getSeason(), existing.getGameType(), "homeScore", "awayScore", "status");
                    matchRecordRepository.save(existing);
                    updated++;
                } else {
                    MatchRecord record = new MatchRecord();
                    record.setHomeTeam(homeTeam);
                    record.setHomeTeamEn(g.optString("homeTeamEn", ""));
                    record.setAwayTeam(awayTeam);
                    record.setAwayTeamEn(g.optString("awayTeamEn", ""));
                    record.setHomeScore(homeScore);
                    record.setAwayScore(awayScore);
                    record.setMatchDate(matchDate);
                    record.setSeason(MatchSeasonResolver.resolve(g.optString("season", null), nbaGameId, matchDate));
                    record.setStatus(status);
                    record.setNbaGameId(nbaGameId);
                    record.setGameType(MatchGameTypeResolver.resolve(
                            g.optString("gameType", null), nbaGameId, matchDate));
                    record.markData("NBA_API", record.getSeason(), record.getGameType(), "homeScore", "awayScore", "status");
                    matchRecordRepository.save(record);
                    inserted++;
                }
            }

            updateProgress(jobId, 90);
            log.info("比赛同步完成: 新增{}, 更新{}, 跳过{}", inserted, updated, skipped);
            return SyncResult.success("MATCHES", games.length(), inserted, updated, skipped,
                    LocalDateTime.now(), "nba_api");
        } catch (Exception e) {
            log.error("比赛数据处理失败", e);
            return SyncResult.failed("MATCHES", "PARSE_ERROR", e.getMessage());
        }
    }

}
