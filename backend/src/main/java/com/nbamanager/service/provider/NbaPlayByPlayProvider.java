package com.nbamanager.service.provider;

import com.nbamanager.config.EnvFileReader;
import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.domain.provider.PlayByPlayFetchOptions;
import com.nbamanager.domain.provider.PlayByPlayFetchResult;
import com.nbamanager.domain.provider.PlayByPlayProvider;
import com.nbamanager.domain.provider.ProviderType;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NbaPlayByPlayProvider implements PlayByPlayProvider {

    @Override
    public ProviderType type() {
        return ProviderType.NBA_PLAY_BY_PLAY;
    }

    @Override
    public boolean supports(String gameId) {
        return gameId != null && !gameId.isBlank();
    }

    @Override
    public PlayByPlayFetchResult fetch(String gameId, PlayByPlayFetchOptions options) {
        long startTime = System.currentTimeMillis();
        try {
            String scriptPath = findScriptPath();
            log.info("NbaPlayByPlayProvider: calling Python script for gameId={}", gameId);

            ProcessBuilder pb = new ProcessBuilder("python", scriptPath, "playbyplay", gameId);
            pb.redirectErrorStream(false);
            pb.directory(new File(scriptPath).getParentFile());
            pb.environment().put("PYTHONIOENCODING", "utf-8");

            // Proxy configuration (optional)
            String proxyHost = EnvFileReader.get("NBA_PROXY_HOST");
            String proxyPort = EnvFileReader.get("NBA_PROXY_PORT");
            if (proxyHost != null && !proxyHost.isEmpty()) {
                pb.environment().put("NBA_PROXY_HOST", proxyHost);
            }
            if (proxyPort != null && !proxyPort.isEmpty()) {
                pb.environment().put("NBA_PROXY_PORT", proxyPort);
            }

            // Translation API configuration
            String mimoApiKey = EnvFileReader.get("MIMO_API_KEY");
            String mimoBaseUrl = EnvFileReader.get("MIMO_BASE_URL");
            if (mimoApiKey != null && !mimoApiKey.isEmpty()) {
                pb.environment().put("MIMO_API_KEY", mimoApiKey);
            }
            if (mimoBaseUrl != null && !mimoBaseUrl.isEmpty()) {
                pb.environment().put("MIMO_BASE_URL", mimoBaseUrl);
            }

            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            StringBuilder errorOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                }
            }

            // Wait for process, max 60 seconds
            boolean finished = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                log.error("NbaPlayByPlayProvider: Python script timed out for gameId={}", gameId);
                process.destroyForcibly();
                long elapsed = System.currentTimeMillis() - startTime;
                return PlayByPlayFetchResult.failure(type(), "Python script timed out", elapsed);
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("NbaPlayByPlayProvider: Python script failed, exitCode={}, error={}",
                        exitCode, errorOutput.toString());
                long elapsed = System.currentTimeMillis() - startTime;
                return PlayByPlayFetchResult.failure(type(),
                        "Python script failed with exit code " + exitCode, elapsed);
            }

            String jsonStr = output.toString().trim();
            if (jsonStr.isEmpty()) {
                log.error("NbaPlayByPlayProvider: Python script returned empty output");
                long elapsed = System.currentTimeMillis() - startTime;
                return PlayByPlayFetchResult.failure(type(), "Empty output from Python script", elapsed);
            }

            // Parse the JSON output - expect a JSONArray directly or wrapped in an object
            JSONArray eventsArray;
            try {
                // Try parsing as a JSONArray directly
                eventsArray = new JSONArray(jsonStr);
            } catch (org.json.JSONException e1) {
                try {
                    // Try parsing as JSONObject with an "events" key
                    JSONObject root = new JSONObject(jsonStr);
                    if (root.has("events") && !root.isNull("events")) {
                        eventsArray = root.getJSONArray("events");
                    } else if (root.has("play_by_play") && !root.isNull("play_by_play")) {
                        eventsArray = root.getJSONArray("play_by_play");
                    } else {
                        long elapsed = System.currentTimeMillis() - startTime;
                        return PlayByPlayFetchResult.failure(type(),
                                "Unexpected JSON structure: no 'events' or 'play_by_play' key", elapsed);
                    }
                } catch (org.json.JSONException e2) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    return PlayByPlayFetchResult.failure(type(),
                            "Failed to parse Python output as JSON: " + e2.getMessage(), elapsed);
                }
            }

            List<PlayByPlayEvent> events = parseEvents(eventsArray, gameId);
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("NbaPlayByPlayProvider: fetched {} events for gameId={} in {}ms",
                    events.size(), gameId, elapsed);

            return PlayByPlayFetchResult.success(type(), events, jsonStr, elapsed);

        } catch (Exception e) {
            log.error("NbaPlayByPlayProvider: error fetching play-by-play for gameId={}", gameId, e);
            long elapsed = System.currentTimeMillis() - startTime;
            return PlayByPlayFetchResult.failure(type(), "Error: " + e.getMessage(), elapsed);
        }
    }

    /**
     * Convert JSONArray to List of PlayByPlayEvent.
     * Package-private for testing.
     */
    List<PlayByPlayEvent> parseEvents(JSONArray eventsArray, String gameId) {
        List<PlayByPlayEvent> events = new ArrayList<>();
        for (int i = 0; i < eventsArray.length(); i++) {
            JSONObject obj = eventsArray.getJSONObject(i);

            int period = obj.optInt("period", 0);
            String gameClock = obj.optString("gameClock", "");
            String description = obj.optString("description", "");
            int homeScore = obj.optInt("homeScore", 0);
            int awayScore = obj.optInt("awayScore", 0);
            String eventType = obj.optString("eventType", "");
            String playerName = obj.optString("playerName", "");
            Long playerId = obj.has("playerId") && !obj.isNull("playerId")
                    ? obj.getLong("playerId") : null;
            Long teamId = obj.has("teamId") && !obj.isNull("teamId")
                    ? obj.getLong("teamId") : null;

            int clockSeconds = gameClockToSeconds(gameClock);
            int eventOrder = period * 10000 + clockSeconds;
            int scoreMargin = homeScore - awayScore;
            boolean isScoringPlay = detectScoringPlay(eventType, description);
            boolean isHighlightCandidate = detectHighlight(eventType, description);
            String sourceEventId = gameId + "_" + period + "_" + gameClock + "_" + i;

            String rawPayloadJson = null;
            if (obj.length() > 0) {
                rawPayloadJson = obj.toString();
            }

            events.add(new PlayByPlayEvent(
                    sourceEventId,
                    period,
                    gameClock,
                    eventType,
                    null, // actionType not provided by NBA script
                    description,
                    playerName,
                    playerId,
                    null, // teamName not provided by NBA script
                    teamId,
                    homeScore,
                    awayScore,
                    scoreMargin,
                    isScoringPlay,
                    isHighlightCandidate,
                    eventOrder,
                    rawPayloadJson
            ));
        }
        return events;
    }

    /**
     * Convert game clock string "MM:SS" to total seconds.
     * Package-private for testing.
     */
    int gameClockToSeconds(String gameClock) {
        if (gameClock == null || gameClock.isBlank()) {
            return 0;
        }
        String[] parts = gameClock.split(":");
        if (parts.length == 2) {
            try {
                int minutes = Integer.parseInt(parts[0].trim());
                int seconds = Integer.parseInt(parts[1].trim());
                return minutes * 60 + seconds;
            } catch (NumberFormatException e) {
                log.warn("NbaPlayByPlayProvider: failed to parse gameClock '{}'", gameClock);
                return 0;
            }
        }
        return 0;
    }

    /**
     * Detect if a play is a scoring play based on event type and description.
     */
    boolean detectScoringPlay(String eventType, String description) {
        if (eventType == null || description == null) {
            return false;
        }
        boolean isScoringType = eventType.contains("Jump Shot")
                || eventType.contains("Layup")
                || eventType.contains("Dunk")
                || eventType.contains("Free Throw");
        boolean hasPts = description.contains("PTS");
        return isScoringType && hasPts;
    }

    /**
     * Detect if a play is a highlight-worthy play based on event type and description.
     */
    boolean detectHighlight(String eventType, String description) {
        if (eventType == null || description == null) {
            return false;
        }
        boolean isHighlightType = eventType.contains("Dunk")
                || eventType.contains("Block")
                || eventType.contains("3PT");
        boolean hasHighlightKeyword = description.contains("makes")
                || description.contains("BLOCK")
                || description.contains("STEAL");
        return isHighlightType && hasHighlightKeyword;
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

        return new File(baseDir, "backend/scripts/nba_data_fetcher.py").getAbsolutePath();
    }
}
