package com.nbamanager.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbamanager.domain.replay.ReplayPageState;
import com.nbamanager.domain.replay.ReplayQualityLevel;
import com.nbamanager.domain.replay.ReplaySyncState;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class ReplayContractTest {

    @Test
    void qualityAndPageStateEnumsHaveStableWireValues() {
        assertThat(ReplayQualityLevel.values())
                .containsExactly(
                        ReplayQualityLevel.FULL,
                        ReplayQualityLevel.PARTIAL,
                        ReplayQualityLevel.SUMMARY_ONLY,
                        ReplayQualityLevel.UNAVAILABLE);
        assertThat(ReplayPageState.values())
                .containsExactly(
                        ReplayPageState.NOT_STARTED,
                        ReplayPageState.READY,
                        ReplayPageState.PARTIAL,
                        ReplayPageState.PREPARING,
                        ReplayPageState.UNAVAILABLE);
    }

    @Test
    void syncStateUsesOptimisticVersioningAndAUniqueGameId() throws Exception {
        assertThat(ReplaySyncState.class.getDeclaredField("version").getAnnotation(Version.class))
                .isNotNull();

        Table table = ReplaySyncState.class.getAnnotation(Table.class);
        assertThat(table).isNotNull();
        assertThat(table.uniqueConstraints())
                .anySatisfy(constraint -> assertThat(constraint.columnNames()).containsExactly("game_id"));
    }

    @Test
    void bootstrapSerializesTheStableReadModelShape() throws Exception {
        ReplayQualityDto quality = new ReplayQualityDto(
                ReplayQualityLevel.FULL,
                554,
                181,
                List.of(1, 2, 3, 4),
                "NBA_PLAY_BY_PLAY",
                "NBA 官方逐回合",
                false,
                true,
                true,
                LocalDateTime.parse("2026-07-20T21:00:00"),
                List.of(),
                "abc123");
        ReplayBootstrapDto bootstrap = new ReplayBootstrapDto(
                "0042500405",
                ReplayPageState.READY,
                quality,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null);

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        JsonNode json = mapper.valueToTree(bootstrap);
        Set<String> fields = StreamSupport.stream(
                        ((Iterable<String>) json::fieldNames).spliterator(), false)
                .collect(Collectors.toSet());

        assertThat(fields).containsExactlyInAnyOrder(
                "gameId",
                "state",
                "quality",
                "timeline",
                "momentum",
                "highlights",
                "turningPoints",
                "cachedSummary",
                "error");
        assertThat(json.path("quality").path("factsHash").asText()).isEqualTo("abc123");
    }
}
