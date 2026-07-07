package com.nbamanager.service.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.domain.provider.ProviderType;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NbaPlayByPlayProviderTest {

    private NbaPlayByPlayProvider provider;

    @BeforeEach
    void setUp() {
        provider = new NbaPlayByPlayProvider();
    }

    @Test
    void type_returnsNbaPlayByPlay() {
        assertThat(provider.type()).isEqualTo(ProviderType.NBA_PLAY_BY_PLAY);
    }

    @Test
    void supports_returnsTrueForValidGameId() {
        assertThat(provider.supports("0042500226")).isTrue();
    }

    @Test
    void supports_returnsFalseForNull() {
        assertThat(provider.supports(null)).isFalse();
    }

    @Test
    void supports_returnsFalseForEmptyString() {
        assertThat(provider.supports("")).isFalse();
    }

    @Test
    void supports_returnsFalseForBlankString() {
        assertThat(provider.supports("   ")).isFalse();
    }

    @Test
    void parseEvents_mapsFieldsCorrectly() {
        JSONArray arr = new JSONArray();
        JSONObject event = new JSONObject();
        event.put("period", 2);
        event.put("gameClock", "08:52");
        event.put("description", "LeBron James 3PT Jump Shot: Made (24 PTS)");
        event.put("homeScore", 58);
        event.put("awayScore", 52);
        event.put("eventType", "3PT Jump Shot");
        event.put("playerName", "LeBron James");
        event.put("playerId", 2544L);
        event.put("teamId", 13L);
        arr.put(event);

        List<PlayByPlayEvent> events = provider.parseEvents(arr, "0022500226");

        assertThat(events).hasSize(1);
        PlayByPlayEvent e = events.get(0);
        assertThat(e.sourceEventId()).isEqualTo("0022500226_2_08:52_0");
        assertThat(e.period()).isEqualTo(2);
        assertThat(e.gameClock()).isEqualTo("08:52");
        assertThat(e.description()).isEqualTo("LeBron James 3PT Jump Shot: Made (24 PTS)");
        assertThat(e.homeScore()).isEqualTo(58);
        assertThat(e.awayScore()).isEqualTo(52);
        assertThat(e.eventType()).isEqualTo("3PT Jump Shot");
        assertThat(e.playerName()).isEqualTo("LeBron James");
        assertThat(e.playerId()).isEqualTo(2544L);
        assertThat(e.teamId()).isEqualTo(13L);
        assertThat(e.scoreMargin()).isEqualTo(6); // 58 - 52
        assertThat(e.rawPayloadJson()).isNotNull();
    }

    @Test
    void parseEvents_usesSequentialEventOrderWhenSourceOrderMissing() {
        JSONArray arr = new JSONArray();
        JSONObject event = new JSONObject();
        event.put("period", 3);
        event.put("gameClock", "05:30");
        event.put("description", "Free Throw 1 of 2");
        event.put("eventType", "Free Throw");
        arr.put(event);

        List<PlayByPlayEvent> events = provider.parseEvents(arr, "0022500226");

        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventOrder()).isEqualTo(1);
    }

    @Test
    void parseEvents_detectsScoringPlay() {
        JSONArray arr = new JSONArray();

        // Scoring play: Jump Shot with PTS
        JSONObject scoring = new JSONObject();
        scoring.put("period", 1);
        scoring.put("gameClock", "10:00");
        scoring.put("description", "Stephen Curry 3PT Jump Shot: Made (12 PTS)");
        scoring.put("eventType", "3PT Jump Shot");
        arr.put(scoring);

        // Non-scoring play: no PTS
        JSONObject nonScoring = new JSONObject();
        nonScoring.put("period", 1);
        nonScoring.put("gameClock", "09:45");
        nonScoring.put("description", "Stephen Curry misses 3PT Jump Shot");
        nonScoring.put("eventType", "3PT Jump Shot");
        arr.put(nonScoring);

        List<PlayByPlayEvent> events = provider.parseEvents(arr, "0022500226");

        assertThat(events.get(0).isScoringPlay()).isTrue();
        assertThat(events.get(1).isScoringPlay()).isFalse();
    }

    @Test
    void parseEvents_detectsScoringPlay_layup() {
        JSONArray arr = new JSONArray();
        JSONObject event = new JSONObject();
        event.put("period", 1);
        event.put("gameClock", "07:15");
        event.put("description", "Giannis Antetokounmpo Layup: Made (8 PTS)");
        event.put("eventType", "Layup");
        arr.put(event);

        List<PlayByPlayEvent> events = provider.parseEvents(arr, "0022500226");
        assertThat(events.get(0).isScoringPlay()).isTrue();
    }

    @Test
    void parseEvents_detectsScoringPlay_dunk() {
        JSONArray arr = new JSONArray();
        JSONObject event = new JSONObject();
        event.put("period", 1);
        event.put("gameClock", "06:30");
        event.put("description", "Ja Morant Dunk: Made (6 PTS)");
        event.put("eventType", "Dunk");
        arr.put(event);

        List<PlayByPlayEvent> events = provider.parseEvents(arr, "0022500226");
        assertThat(events.get(0).isScoringPlay()).isTrue();
    }

    @Test
    void parseEvents_detectsScoringPlay_freeThrow() {
        JSONArray arr = new JSONArray();
        JSONObject event = new JSONObject();
        event.put("period", 4);
        event.put("gameClock", "01:00");
        event.put("description", "Nikola Jokic Free Throw 1 of 1: Made (30 PTS)");
        event.put("eventType", "Free Throw");
        arr.put(event);

        List<PlayByPlayEvent> events = provider.parseEvents(arr, "0022500226");
        assertThat(events.get(0).isScoringPlay()).isTrue();
    }

    @Test
    void parseEvents_detectsHighlight_dunk() {
        JSONArray arr = new JSONArray();
        JSONObject event = new JSONObject();
        event.put("period", 2);
        event.put("gameClock", "04:20");
        event.put("description", "Ja Morant makes Dunk");
        event.put("eventType", "Dunk");
        arr.put(event);

        List<PlayByPlayEvent> events = provider.parseEvents(arr, "0022500226");
        assertThat(events.get(0).isHighlightCandidate()).isTrue();
    }

    @Test
    void parseEvents_detectsHighlight_block() {
        JSONArray arr = new JSONArray();
        JSONObject event = new JSONObject();
        event.put("period", 3);
        event.put("gameClock", "02:10");
        event.put("description", "Rudy Gobert BLOCK on LeBron James");
        event.put("eventType", "Block");
        arr.put(event);

        List<PlayByPlayEvent> events = provider.parseEvents(arr, "0022500226");
        assertThat(events.get(0).isHighlightCandidate()).isTrue();
    }

    @Test
    void parseEvents_detectsHighlight_3pt() {
        JSONArray arr = new JSONArray();
        JSONObject event = new JSONObject();
        event.put("period", 1);
        event.put("gameClock", "09:00");
        event.put("description", "Stephen Curry makes 3PT Jump Shot");
        event.put("eventType", "3PT Jump Shot");
        arr.put(event);

        List<PlayByPlayEvent> events = provider.parseEvents(arr, "0022500226");
        assertThat(events.get(0).isHighlightCandidate()).isTrue();
    }

    @Test
    void parseEvents_notHighlight_forRegularPlay() {
        JSONArray arr = new JSONArray();
        JSONObject event = new JSONObject();
        event.put("period", 1);
        event.put("gameClock", "08:30");
        event.put("description", "LeBron James misses Jump Shot");
        event.put("eventType", "Jump Shot");
        arr.put(event);

        List<PlayByPlayEvent> events = provider.parseEvents(arr, "0022500226");
        assertThat(events.get(0).isHighlightCandidate()).isFalse();
    }

    @Test
    void gameClockToSeconds_convertsCorrectly() {
        assertThat(provider.gameClockToSeconds("08:52")).isEqualTo(532);
        assertThat(provider.gameClockToSeconds("12:00")).isEqualTo(720);
        assertThat(provider.gameClockToSeconds("00:30")).isEqualTo(30);
        assertThat(provider.gameClockToSeconds("00:00")).isEqualTo(0);
    }

    @Test
    void gameClockToSeconds_handlesNull() {
        assertThat(provider.gameClockToSeconds(null)).isEqualTo(0);
    }

    @Test
    void gameClockToSeconds_handlesBlank() {
        assertThat(provider.gameClockToSeconds("")).isEqualTo(0);
        assertThat(provider.gameClockToSeconds("  ")).isEqualTo(0);
    }

    @Test
    void gameClockToSeconds_handlesInvalidFormat() {
        assertThat(provider.gameClockToSeconds("invalid")).isEqualTo(0);
    }

    @Test
    void parseEvents_multipleEvents_preservesOrder() {
        JSONArray arr = new JSONArray();

        JSONObject e1 = new JSONObject();
        e1.put("period", 1);
        e1.put("gameClock", "11:55");
        e1.put("description", "Jump ball");
        e1.put("eventType", "Jump Ball");
        arr.put(e1);

        JSONObject e2 = new JSONObject();
        e2.put("period", 1);
        e2.put("gameClock", "11:50");
        e2.put("description", "Free throw");
        e2.put("eventType", "Free Throw");
        arr.put(e2);

        List<PlayByPlayEvent> events = provider.parseEvents(arr, "0022500226");

        assertThat(events).hasSize(2);
        assertThat(events.get(0).eventOrder()).isEqualTo(1);
        assertThat(events.get(1).eventOrder()).isEqualTo(2);
    }

    @Test
    void parseEvents_emptyArray_returnsEmptyList() {
        JSONArray arr = new JSONArray();
        List<PlayByPlayEvent> events = provider.parseEvents(arr, "0022500226");
        assertThat(events).isEmpty();
    }
}
