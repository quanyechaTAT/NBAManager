package com.nbamanager.service.provider;

import com.nbamanager.config.PythonTimeoutProperties;
import com.nbamanager.config.EnvFileReader;
import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.domain.provider.PlayByPlayFetchOptions;
import com.nbamanager.domain.provider.PlayByPlayFetchResult;
import com.nbamanager.domain.provider.PlayByPlayProvider;
import com.nbamanager.domain.provider.ProviderType;
import com.nbamanager.domain.provider.ReplayProviderOutcome;
import com.nbamanager.service.PythonScriptRunner;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
@Slf4j
@RequiredArgsConstructor
public class NbaPlayByPlayProvider implements PlayByPlayProvider {

    private static final String NBA_GAME_ID_PATTERN = "^00\\d{8}$";

    private final PythonScriptRunner pythonScriptRunner;
    private final PythonTimeoutProperties pythonTimeoutProperties;

    @Override
    public ProviderType type() {
        return ProviderType.NBA_PLAY_BY_PLAY;
    }

    @Override
    public boolean supports(String gameId) {
        return gameId != null && gameId.matches(NBA_GAME_ID_PATTERN);
    }

    @Override
    public PlayByPlayFetchResult fetch(String gameId, PlayByPlayFetchOptions options) {
        long startTime = System.currentTimeMillis();
        try {
            if (!supports(gameId)) {
                return PlayByPlayFetchResult.failure(
                        type(),
                        "UNSUPPORTED_GAME_ID",
                        "NBA provider requires a 10-digit game ID beginning with 00",
                        false,
                        null,
                        System.currentTimeMillis() - startTime
                );
            }

            log.info("NbaPlayByPlayProvider: calling Python script for gameId={}", gameId);
            PythonScriptRunner.PythonResult result = pythonScriptRunner.run(
                    "nba_data_fetcher.py", new String[]{"playbyplay", gameId},
                    pythonTimeoutProperties.getDetailSeconds());
            if (!result.success()) {
                log.error("NbaPlayByPlayProvider: Python script failed, exitCode={}, timedOut={}, error={}",
                        result.exitCode(), result.timedOut(), result.stderr());
                long elapsed = System.currentTimeMillis() - startTime;
                return PlayByPlayFetchResult.failure(
                        type(),
                        result.timedOut() ? "PROVIDER_TIMEOUT" : "PROVIDER_PROCESS_ERROR",
                        result.timedOut() ? "Python script timed out"
                                : "Python script failed with exit code " + result.exitCode(),
                        true,
                        null,
                        elapsed
                );
            }

            String jsonStr = result.stdout().trim();
            if (jsonStr.isEmpty()) {
                log.error("NbaPlayByPlayProvider: Python script returned empty output");
                long elapsed = System.currentTimeMillis() - startTime;
                return PlayByPlayFetchResult.failure(
                        type(), "EMPTY_PROCESS_OUTPUT", "Empty output from Python script",
                        true, null, elapsed);
            }

            PlayByPlayFetchResult.ProviderPayload payload =
                    PlayByPlayFetchResult.parseProviderPayload(jsonStr, type());
            long elapsed = System.currentTimeMillis() - startTime;
            if (payload.outcome() != ReplayProviderOutcome.SUCCESS) {
                log.warn("NbaPlayByPlayProvider: provider outcome={} for gameId={}, errorCode={}",
                        payload.outcome(), gameId, payload.errorCode());
                return PlayByPlayFetchResult.failure(type(), payload, jsonStr, elapsed);
            }

            List<PlayByPlayEvent> events = parseEvents(payload.events(), gameId);
            log.info("NbaPlayByPlayProvider: fetched {} events for gameId={} in {}ms",
                    events.size(), gameId, elapsed);

            return PlayByPlayFetchResult.success(
                    type(), events, jsonStr, payload.quality(), payload.providerSource(), elapsed);

        } catch (IllegalArgumentException | JSONException e) {
            log.error("NbaPlayByPlayProvider: invalid provider contract for gameId={}: {}",
                    gameId, e.getMessage());
            long elapsed = System.currentTimeMillis() - startTime;
            return PlayByPlayFetchResult.failure(
                    type(), "INVALID_PROVIDER_CONTRACT", e.getMessage(), false, null, elapsed);
        } catch (Exception e) {
            log.error("NbaPlayByPlayProvider: error fetching play-by-play for gameId={}", gameId, e);
            long elapsed = System.currentTimeMillis() - startTime;
            return PlayByPlayFetchResult.failure(
                    type(), "PROVIDER_RUNTIME_ERROR", "Error: " + e.getMessage(), true, null, elapsed);
        }
    }

    /**
     * Convert JSONArray to List of PlayByPlayEvent.
     * Package-private for testing.
     */
    List<PlayByPlayEvent> parseEvents(JSONArray eventsArray, String gameId) {
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

            // API may omit scores for non-scoring events — carry forward previous scores
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
            String rawPayloadJson = obj.length() > 0 ? obj.toString() : null;

            events.add(new PlayByPlayEvent(
                    sourceEventId, period, gameClock, eventType, actionType,
                    description, playerName, playerId, null, teamId,
                    homeScore, awayScore, scoreMargin,
                    isScoringPlay, isHighlightCandidate,
                    eventOrder, rawPayloadJson
            ));

            prevHomeScore = homeScore;
            prevAwayScore = awayScore;
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
