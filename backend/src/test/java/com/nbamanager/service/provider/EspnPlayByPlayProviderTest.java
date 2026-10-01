package com.nbamanager.service.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.nbamanager.config.PythonTimeoutProperties;
import com.nbamanager.domain.provider.PlayByPlayFetchOptions;
import com.nbamanager.domain.provider.PlayByPlayFetchResult;
import com.nbamanager.domain.provider.ReplayProviderOutcome;
import com.nbamanager.domain.provider.ReplayProviderQuality;
import com.nbamanager.service.GameIdMappingService;
import com.nbamanager.service.PythonScriptRunner;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EspnPlayByPlayProviderTest {

    @Mock
    private GameIdMappingService gameIdMappingService;

    @Mock
    private PythonScriptRunner pythonScriptRunner;

    private EspnPlayByPlayProvider provider;

    @BeforeEach
    void setUp() {
        PythonTimeoutProperties timeoutProperties = new PythonTimeoutProperties();
        timeoutProperties.setDetailSeconds(120);
        provider = new EspnPlayByPlayProvider(
                gameIdMappingService, pythonScriptRunner, timeoutProperties);
    }

    @Test
    void supports_directEspnIdWithoutCallingMappingService() {
        assertThat(provider.supports("401881722")).isTrue();
        verifyNoInteractions(gameIdMappingService);
    }

    @Test
    void supports_mappedNbaId() {
        when(gameIdMappingService.getEspnGameId("0042500405")).thenReturn("401705342");

        assertThat(provider.supports("0042500405")).isTrue();
    }

    @Test
    void supports_rejectsNbaIdWithoutMapping() {
        when(gameIdMappingService.getEspnGameId("0042500405")).thenReturn("0042500405");

        assertThat(provider.supports("0042500405")).isFalse();
    }

    @Test
    void supports_rejectsMalformedIdsWithoutMappingLookup() {
        assertThat(provider.supports(null)).isFalse();
        assertThat(provider.supports("not-a-game")).isFalse();
        verifyNoInteractions(gameIdMappingService);
    }

    @Test
    void fetch_directEspnIdDoesNotCallNbaMappingApi() {
        when(pythonScriptRunner.run(
                "nba_data_fetcher.py", new String[]{"espn_playbyplay", "401881722"}, 120))
                .thenReturn(pythonResult(envelope("SUCCESS", "FULL", "ESPN_PLAY_BY_PLAY", events(),
                        null, null, false, 1)));

        PlayByPlayFetchResult result = provider.fetch(
                "401881722", PlayByPlayFetchOptions.defaults());

        assertThat(result.success()).isTrue();
        assertThat(result.outcome()).isEqualTo(ReplayProviderOutcome.SUCCESS);
        assertThat(result.quality()).isEqualTo(ReplayProviderQuality.FULL);
        verifyNoInteractions(gameIdMappingService);

        ArgumentCaptor<String[]> args = ArgumentCaptor.forClass(String[].class);
        verify(pythonScriptRunner).run(
                org.mockito.ArgumentMatchers.eq("nba_data_fetcher.py"),
                args.capture(),
                org.mockito.ArgumentMatchers.eq(120));
        assertThat(args.getValue()).containsExactly("espn_playbyplay", "401881722");
    }

    @Test
    void fetch_usesMappingForNbaId() {
        when(gameIdMappingService.getEspnGameId("0042500405")).thenReturn("401705342");
        when(pythonScriptRunner.run(
                "nba_data_fetcher.py", new String[]{"espn_playbyplay", "401705342"}, 120))
                .thenReturn(pythonResult(envelope("SUCCESS", "FULL", "ESPN_PLAY_BY_PLAY", events(),
                        null, null, false, 1)));

        PlayByPlayFetchResult result = provider.fetch(
                "0042500405", PlayByPlayFetchOptions.defaults());

        assertThat(result.success()).isTrue();
        assertThat(result.events()).hasSize(1);
    }

    @Test
    void fetch_preservesSummaryOnlyQuality() {
        JSONArray summaryEvents = new JSONArray().put(new JSONObject()
                .put("period", 1)
                .put("gameClock", "0:00")
                .put("description", "End of first period")
                .put("eventType", "period_score")
                .put("homeScore", 25)
                .put("awayScore", 21));
        when(pythonScriptRunner.run(
                "nba_data_fetcher.py", new String[]{"espn_playbyplay", "401881722"}, 120))
                .thenReturn(pythonResult(envelope(
                        "SUCCESS", "SUMMARY_ONLY", "ESPN_SCOREBOARD_SUMMARY", summaryEvents,
                        null, null, false, 1)));

        PlayByPlayFetchResult result = provider.fetch(
                "401881722", PlayByPlayFetchOptions.defaults());

        assertThat(result.success()).isTrue();
        assertThat(result.quality()).isEqualTo(ReplayProviderQuality.SUMMARY_ONLY);
        assertThat(result.providerSource()).isEqualTo("ESPN_SCOREBOARD_SUMMARY");
    }

    @Test
    void fetch_treatsEmptyAndErrorEnvelopesAsFailures() {
        when(pythonScriptRunner.run(
                "nba_data_fetcher.py", new String[]{"espn_playbyplay", "401881722"}, 120))
                .thenReturn(pythonResult(envelope(
                        "EMPTY", "UNAVAILABLE", "ESPN_PLAY_BY_PLAY", new JSONArray(),
                        null, null, false, 0)));

        PlayByPlayFetchResult empty = provider.fetch(
                "401881722", PlayByPlayFetchOptions.defaults());

        assertThat(empty.success()).isFalse();
        assertThat(empty.outcome()).isEqualTo(ReplayProviderOutcome.EMPTY);
        assertThat(empty.events()).isEmpty();

        when(pythonScriptRunner.run(
                "nba_data_fetcher.py", new String[]{"espn_playbyplay", "401881722"}, 120))
                .thenReturn(pythonResult(envelope(
                        "ERROR", "UNAVAILABLE", "ESPN_PLAY_BY_PLAY", new JSONArray(),
                        "ESPN_API_ERROR", "service unavailable", true, 0)));

        PlayByPlayFetchResult error = provider.fetch(
                "401881722", PlayByPlayFetchOptions.defaults());

        assertThat(error.success()).isFalse();
        assertThat(error.errorCode()).isEqualTo("ESPN_API_ERROR");
        assertThat(error.errorMessage()).isEqualTo("service unavailable");
        assertThat(error.retryable()).isTrue();
    }

    @Test
    void fetch_rejectsSourceThatDoesNotBelongToEspnProvider() {
        when(pythonScriptRunner.run(
                "nba_data_fetcher.py", new String[]{"espn_playbyplay", "401881722"}, 120))
                .thenReturn(pythonResult(envelope(
                        "SUCCESS", "FULL", "NBA_PLAY_BY_PLAY", events(),
                        null, null, false, 1)));

        PlayByPlayFetchResult result = provider.fetch(
                "401881722", PlayByPlayFetchOptions.defaults());

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_PROVIDER_CONTRACT");
    }

    @Test
    void fetch_doesNotRunScriptWhenNbaIdHasNoMapping() {
        when(gameIdMappingService.getEspnGameId("0042500405")).thenReturn("0042500405");

        PlayByPlayFetchResult result = provider.fetch(
                "0042500405", PlayByPlayFetchOptions.defaults());

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("NO_ESPN_MAPPING");
        verify(pythonScriptRunner, never()).run(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(String[].class),
                org.mockito.ArgumentMatchers.anyInt());
    }

    private PythonScriptRunner.PythonResult pythonResult(String stdout) {
        return new PythonScriptRunner.PythonResult(0, stdout, "", false, 10);
    }

    private String envelope(String outcome, String quality, String source, JSONArray events,
                            String errorCode, String errorMessage, boolean retryable, int eventCount) {
        JSONObject metadata = new JSONObject()
                .put("schemaVersion", "replay-provider/v1")
                .put("outcome", outcome)
                .put("quality", quality)
                .put("source", source)
                .put("eventCount", eventCount)
                .put("errorCode", errorCode == null ? JSONObject.NULL : errorCode)
                .put("errorMessage", errorMessage == null ? JSONObject.NULL : errorMessage)
                .put("retryable", retryable);
        metadata.put("events", events);
        metadata.put("playByPlay", events);
        return metadata.toString();
    }

    private JSONArray events() {
        return new JSONArray().put(new JSONObject()
                .put("period", 1)
                .put("gameClock", "10:00")
                .put("description", "Made shot")
                .put("eventType", "Jump Shot")
                .put("homeScore", 2)
                .put("awayScore", 0));
    }
}
