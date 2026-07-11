package com.nbamanager.service.provider;

import com.nbamanager.config.EnvFileReader;
import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.domain.provider.PlayByPlayFetchOptions;
import com.nbamanager.domain.provider.PlayByPlayFetchResult;
import com.nbamanager.domain.provider.PlayByPlayProvider;
import com.nbamanager.domain.provider.ProviderType;
import com.nbamanager.service.GameIdMappingService;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

/**
 * Play-by-play provider that fetches from the ESPN API via Python script.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EspnPlayByPlayProvider implements PlayByPlayProvider {

    private final GameIdMappingService gameIdMappingService;

    @Override
    public ProviderType type() {
        return ProviderType.ESPN_PLAY_BY_PLAY;
    }

    @Override
    public boolean supports(String gameId) {
        if (gameId == null || gameId.isBlank()) {
            return false;
        }
        String espnGameId = gameIdMappingService.getEspnGameId(gameId);
        return espnGameId != null && !espnGameId.equals(gameId);
    }

    @Override
    public PlayByPlayFetchResult fetch(String gameId, PlayByPlayFetchOptions options) {
        long startTime = System.currentTimeMillis();
        try {
            String espnGameId = gameIdMappingService.getEspnGameId(gameId);
            if (espnGameId == null || espnGameId.equals(gameId)) {
                long elapsed = System.currentTimeMillis() - startTime;
                return PlayByPlayFetchResult.failure(type(),
                        "No ESPN game ID mapping found for gameId: " + gameId, elapsed);
            }

            String scriptPath = findScriptPath();
            log.info("EspnPlayByPlayProvider: calling Python script for espnGameId={}", espnGameId);

            ProcessBuilder pb = new ProcessBuilder("python", scriptPath, "espn_playbyplay", espnGameId);
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
                log.error("EspnPlayByPlayProvider: Python script timed out for espnGameId={}", espnGameId);
                process.destroyForcibly();
                long elapsed = System.currentTimeMillis() - startTime;
                return PlayByPlayFetchResult.failure(type(), "Python script timed out", elapsed);
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("EspnPlayByPlayProvider: Python script failed, exitCode={}, error={}",
                        exitCode, errorOutput.toString());
                long elapsed = System.currentTimeMillis() - startTime;
                return PlayByPlayFetchResult.failure(type(),
                        "Python script failed with exit code " + exitCode, elapsed);
            }

            String jsonStr = output.toString().trim();
            if (jsonStr.isEmpty()) {
                log.error("EspnPlayByPlayProvider: Python script returned empty output");
                long elapsed = System.currentTimeMillis() - startTime;
                return PlayByPlayFetchResult.failure(type(), "Empty output from Python script", elapsed);
            }

            // Parse the JSON output - expect JSONObject with "playByPlay" JSONArray
            JSONArray eventsArray;
            try {
                JSONObject root = new JSONObject(jsonStr);
                if (root.has("playByPlay") && !root.isNull("playByPlay")) {
                    eventsArray = root.getJSONArray("playByPlay");
                } else {
                    long elapsed = System.currentTimeMillis() - startTime;
                    return PlayByPlayFetchResult.failure(type(),
                            "Unexpected JSON structure: no 'playByPlay' key", elapsed);
                }
            } catch (org.json.JSONException e) {
                // Try as a direct JSONArray
                try {
                    eventsArray = new JSONArray(jsonStr);
                } catch (org.json.JSONException e2) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    return PlayByPlayFetchResult.failure(type(),
                            "Failed to parse Python output as JSON: " + e2.getMessage(), elapsed);
                }
            }

            List<PlayByPlayEvent> events = parseEvents(eventsArray, gameId);
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("EspnPlayByPlayProvider: fetched {} events for gameId={} in {}ms",
                    events.size(), gameId, elapsed);

            return PlayByPlayFetchResult.success(type(), events, jsonStr, elapsed);

        } catch (Exception e) {
            log.error("EspnPlayByPlayProvider: error fetching play-by-play for gameId={}", gameId, e);
            long elapsed = System.currentTimeMillis() - startTime;
            return PlayByPlayFetchResult.failure(type(), "Error: " + e.getMessage(), elapsed);
        }
    }

    /**
     * Convert JSONArray to List of PlayByPlayEvent.
     */
    private List<PlayByPlayEvent> parseEvents(JSONArray eventsArray, String gameId) {
        List<PlayByPlayEvent> events = new ArrayList<>();
        int prevHomeScore = 0;
        int prevAwayScore = 0;

        for (int i = 0; i < eventsArray.length(); i++) {
            JSONObject obj = eventsArray.getJSONObject(i);

            int period = obj.optInt("period", 0);
            String gameClock = obj.optString("gameClock", "");
            String description = obj.optString("description", "");
            int homeScore = obj.optInt("homeScore", 0);
            int awayScore = obj.optInt("awayScore", 0);

            // ESPN may omit scores for non-scoring events (substitutions, fouls, etc.)
            // Carry forward the previous event's scores to avoid margin dropping to 0
            if (homeScore == 0 && awayScore == 0 && i > 0) {
                homeScore = prevHomeScore;
                awayScore = prevAwayScore;
            }

            String eventType = obj.optString("eventType", "");
            String actionType = obj.optString("actionType", "");
            String playerName = obj.optString("playerName", "");
            Long playerId = obj.has("playerId") && !obj.isNull("playerId")
                    ? obj.getLong("playerId") : null;
            Long teamId = obj.has("teamId") && !obj.isNull("teamId")
                    ? obj.getLong("teamId") : null;

            int eventOrder = obj.has("eventOrder") && !obj.isNull("eventOrder")
                    ? obj.optInt("eventOrder", i + 1)
                    : i + 1;
            int scoreMargin = homeScore - awayScore;

            // Use unified classifier with score change detection
            boolean isScoringPlay = PlayByPlayEventClassifier.isScoringPlay(
                    eventType, actionType, description,
                    homeScore, awayScore, prevHomeScore, prevAwayScore);
            boolean isHighlightCandidate = PlayByPlayEventClassifier.isHighlightCandidate(
                    eventType, actionType, description, isScoringPlay,
                    homeScore, awayScore, prevHomeScore, prevAwayScore,
                    period, gameClock);

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
                    actionType,
                    description,
                    playerName,
                    playerId,
                    null,
                    teamId,
                    homeScore,
                    awayScore,
                    scoreMargin,
                    isScoringPlay,
                    isHighlightCandidate,
                    eventOrder,
                    rawPayloadJson
            ));

            prevHomeScore = homeScore;
            prevAwayScore = awayScore;
        }
        return events;
    }

    /**
     * Convert game clock string "MM:SS" to total seconds.
     */
    static int gameClockToSeconds(String gameClock) {
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
                log.warn("EspnPlayByPlayProvider: failed to parse gameClock '{}'", gameClock);
                return 0;
            }
        }
        return 0;
    }

    /**
     * Detect if a play is a scoring play based on event type and description.
     */
    static boolean detectScoringPlay(String eventType, String description) {
        if (eventType == null || description == null) {
            return false;
        }
        boolean isScoringType = eventType.contains("Jump Shot")
                || eventType.contains("Layup")
                || eventType.contains("Dunk")
                || eventType.contains("Free Throw")
                || eventType.contains("Two Point")
                || eventType.contains("Three Point");
        boolean hasPts = description.contains("PTS");
        return isScoringType && hasPts;
    }

    /**
     * Detect if a play is a highlight-worthy play based on event type and description.
     */
    static boolean detectHighlight(String eventType, String description) {
        if (eventType == null || description == null) {
            return false;
        }
        boolean isHighlightType = eventType.contains("Dunk")
                || eventType.contains("Block")
                || eventType.contains("3PT")
                || eventType.contains("Three Point");
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
