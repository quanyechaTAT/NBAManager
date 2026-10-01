package com.nbamanager.service;

import com.nbamanager.config.PythonTimeoutProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class NbaDataService {

    private final PlayoffService playoffService;
    private final PlayoffReferenceData playoffReferenceData;
    private final PythonScriptRunner pythonScriptRunner;
    private final PythonTimeoutProperties pythonTimeoutProperties;

    /**
     * 获取季后赛数据并导入数据库
     */
    public Map<String, Object> fetchAndImportPlayoffData(String season) {
        return fetchAndImportPlayoffData(season, null);
    }

    public Map<String, Object> fetchAndImportPlayoffData(String season, Long jobId) {
        Map<String, Object> result = new HashMap<>();

        try {
            if (playoffReferenceData.supports(season)) {
                List<Map<String, Object>> matchups = playoffReferenceData.getMatchups(season);
                int imported = playoffService.clearAndImportPlayoffData(matchups, season);
                result.put("matchups", matchups);
                result.put("imported", imported);
                result.put("source", "reference");
                return result;
            }

            PythonScriptRunner.PythonResult pythonResult = jobId == null
                    ? pythonScriptRunner.run("nba_data_fetcher.py", new String[]{"playoff", season},
                            pythonTimeoutProperties.getPlayoffSeconds())
                    : pythonScriptRunner.runForJob(
                            "nba_data_fetcher.py", new String[]{"playoff", season},
                            pythonTimeoutProperties.getPlayoffSeconds(), jobId);
            if (!pythonResult.success()) {
                log.warn("季后赛脚本执行失败: exitCode={}, error={}",
                        pythonResult.exitCode(), pythonResult.stderr());
                result.put("matchups", new ArrayList<>());
                result.put("games", new ArrayList<>());
                return result;
            }

            String jsonStr = pythonResult.stdout().trim();
            if (!jsonStr.isEmpty()) {
                    JSONObject data = new JSONObject(jsonStr);
                    JSONObject playoff = data.optJSONObject("playoff");

                    if (playoff != null) {
                        JSONArray matchupsArray = playoff.optJSONArray("matchups");
                        JSONArray gamesArray = playoff.optJSONArray("games");

                        List<Map<String, Object>> matchups = new ArrayList<>();
                        if (matchupsArray != null) {
                            for (int i = 0; i < matchupsArray.length(); i++) {
                                JSONObject m = matchupsArray.getJSONObject(i);
                                Map<String, Object> matchup = new HashMap<>();
                                matchup.put("seriesId", m.optString("seriesId", ""));
                                matchup.put("team1Name", m.optString("team1Name", ""));
                                matchup.put("team1NameEn", m.optString("team1NameEn", ""));
                                matchup.put("team2Name", m.optString("team2Name", ""));
                                matchup.put("team2NameEn", m.optString("team2NameEn", ""));
                                matchup.put("team1Seed", m.optInt("team1Seed", 0));
                                matchup.put("team2Seed", m.optInt("team2Seed", 0));
                                matchup.put("round", m.optInt("round", 1));
                                matchup.put("conference", m.optString("conference", "Unknown"));
                                matchup.put("team1Wins", m.optInt("team1Wins", 0));
                                matchup.put("team2Wins", m.optInt("team2Wins", 0));
                                matchup.put("status", m.optString("status", "SCHEDULED"));
                                matchup.put("winner", m.optString("winner", null));
                                matchups.add(matchup);
                            }
                        }

                        // 使用原子操作：清除旧数据并导入新数据（在同一事务中）
                        int imported = playoffService.clearAndImportPlayoffData(matchups, season);
                        result.put("matchups", matchups);
                        result.put("imported", imported);
                    }
            }

        } catch (Exception e) {
            log.error("获取季后赛数据失败", e);
        }

        return result;
    }

}
