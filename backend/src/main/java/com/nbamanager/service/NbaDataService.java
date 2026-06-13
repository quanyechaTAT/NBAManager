package com.nbamanager.service;

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

    /**
     * 获取季后赛数据并导入数据库
     */
    public Map<String, Object> fetchAndImportPlayoffData(String season) {
        Map<String, Object> result = new HashMap<>();

        try {
            String scriptPath = findScriptPath();
            if (scriptPath == null) {
                log.warn("Python脚本未找到");
                result.put("matchups", new ArrayList<>());
                result.put("games", new ArrayList<>());
                return result;
            }

            // 调用Python脚本获取季后赛数据（传递赛季参数）
            ProcessBuilder pb = new ProcessBuilder("python", scriptPath, "playoff", season);
            pb.redirectErrorStream(true);
            pb.directory(new File(scriptPath).getParentFile());
            pb.environment().put("PYTHONIOENCODING", "utf-8");

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Python脚本执行超时");
                result.put("matchups", new ArrayList<>());
                result.put("games", new ArrayList<>());
                return result;
            }

            // 从文件读取JSON
            File outputFile = new File(new File(scriptPath).getParentFile(), "output.json");
            if (outputFile.exists()) {
                String jsonStr = new String(java.nio.file.Files.readAllBytes(outputFile.toPath()), StandardCharsets.UTF_8);
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
                                matchup.put("team1Name", m.optString("team1Name", ""));
                                matchup.put("team1NameEn", m.optString("team1NameEn", ""));
                                matchup.put("team2Name", m.optString("team2Name", ""));
                                matchup.put("team2NameEn", m.optString("team2NameEn", ""));
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
            }

        } catch (Exception e) {
            log.error("获取季后赛数据失败", e);
        }

        return result;
    }

    private String findScriptPath() {
        String[] possiblePaths = {
                "backend/scripts/nba_data_fetcher.py",
                "scripts/nba_data_fetcher.py",
                "../scripts/nba_data_fetcher.py",
                "nba_data_fetcher.py"
        };

        File baseDir = new File(System.getProperty("user.dir"));
        for (String path : possiblePaths) {
            File file = new File(baseDir, path);
            if (file.exists()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }
}
