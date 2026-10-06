package com.nbamanager.service;

import com.nbamanager.config.PythonTimeoutProperties;
import com.nbamanager.domain.Team;
import com.nbamanager.repository.SyncJobRepository;
import com.nbamanager.service.SyncJobStateService;
import com.nbamanager.repository.TeamRepository;
import com.nbamanager.web.dto.SyncResult;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TeamSyncWorker extends ModuleSyncWorker {

    private final PythonScriptRunner runner;
    private final TeamRepository teamRepository;
    private final PythonTimeoutProperties timeoutProperties;

    public TeamSyncWorker(
            SyncJobRepository repo,
            SyncJobStateService jobStateService,
            PythonScriptRunner runner,
            TeamRepository teamRepository,
            PythonTimeoutProperties timeoutProperties) {
        super(repo, jobStateService);
        this.runner = runner;
        this.teamRepository = teamRepository;
        this.timeoutProperties = timeoutProperties;
    }

    @Override
    protected SyncResult doSync(Long jobId) {
        updateProgress(jobId, 10);
        PythonScriptRunner.PythonResult pyResult = runner.runForJob(
                "nba_data_fetcher.py",
                new String[]{"teams"},
                timeoutProperties.getBulkSeconds(),
                jobId);
        if (!pyResult.success()) {
            return SyncResult.failed("TEAMS", "PYTHON_FAILED",
                    "exitCode=" + pyResult.exitCode() + " stderr=" + pyResult.stderr().substring(0, Math.min(200, pyResult.stderr().length())));
        }

        updateProgress(jobId, 40);
        if (isCancelled(jobId)) return SyncResult.cancelled("TEAMS");

        try {
            JSONObject data = new JSONObject(pyResult.stdout());
            if (!data.has("teams")) {
                return SyncResult.failed("TEAMS", "NO_DATA", "Python返回无球队数据");
            }

            JSONArray teams = data.getJSONArray("teams");
            if (teams.length() < 30) {
                return SyncResult.failed("TEAMS", "QUALITY_GATE",
                        "球队数量不足30支: " + teams.length());
            }

            updateProgress(jobId, 60);
            int inserted = 0, updated = 0;

            for (int i = 0; i < teams.length(); i++) {
                if (isCancelled(jobId)) return SyncResult.cancelled("TEAMS");
                JSONObject t = teams.getJSONObject(i);
                String name = t.getString("name");
                Team existing = teamRepository.findByName(name);
                if (existing != null) {
                    existing.setNameEn(t.optString("nameEn", ""));
                    existing.setAbbreviation(t.optString("abbreviation", ""));
                    existing.setCity(t.getString("city"));
                    existing.setConference(t.getString("conference"));
                    existing.setWins(nullableInteger(t, "wins"));
                    existing.setLosses(nullableInteger(t, "losses"));
                    existing.markData("NBA_API", PlayerSnapshotMapper.text(t, "season"), "REGULAR_SEASON", "wins", "losses");
                    existing.setDataSourceUrl("https://www.nba.com/standings");
                    teamRepository.save(existing);
                    updated++;
                } else {
                    Team team = new Team();
                    team.setName(name);
                    team.setNameEn(t.optString("nameEn", ""));
                    team.setAbbreviation(t.optString("abbreviation", ""));
                    team.setCity(t.getString("city"));
                    team.setConference(t.getString("conference"));
                    team.setWins(nullableInteger(t, "wins"));
                    team.setLosses(nullableInteger(t, "losses"));
                    team.markData("NBA_API", PlayerSnapshotMapper.text(t, "season"), "REGULAR_SEASON", "wins", "losses");
                    team.setDataSourceUrl("https://www.nba.com/standings");
                    teamRepository.save(team);
                    inserted++;
                }
            }

            updateProgress(jobId, 90);
            log.info("球队同步完成: 新增{}, 更新{}", inserted, updated);
            return SyncResult.success("TEAMS", teams.length(), inserted, updated, 0,
                    LocalDateTime.now(), "nba_api");
        } catch (Exception e) {
            log.error("球队数据处理失败", e);
            return SyncResult.failed("TEAMS", "PARSE_ERROR", e.getMessage());
        }
    }
    private static Integer nullableInteger(JSONObject data, String field) {
        Double value = PlayerSnapshotMapper.number(data, field);
        return value == null ? null : value.intValue();
    }

}
