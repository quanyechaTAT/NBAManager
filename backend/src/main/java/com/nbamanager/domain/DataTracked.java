package com.nbamanager.domain;

import com.nbamanager.web.dto.DataMeta;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.json.JSONObject;
import org.springframework.util.ReflectionUtils;

/** Additive provenance columns; old values remain stored until a verified replacement is available. */
@MappedSuperclass
@Getter
@Setter
public abstract class DataTracked {
    @Column(name = "data_source", length = 64) private String dataSource;
    @Column(name = "data_source_url", length = 512) private String dataSourceUrl;
    @Column(name = "data_season", length = 16) private String dataSeason;
    @Column(name = "data_scope", length = 32) private String dataScope;
    @Column(name = "data_as_of") private Instant dataAsOf;
    @Column(name = "data_synced_at") private Instant dataSyncedAt;
    @Column(name = "data_quality", length = 16) private String dataQuality;
    @Column(name = "data_field_states", columnDefinition = "TEXT") private String dataFieldStates;
    @com.fasterxml.jackson.annotation.JsonIgnore @Transient private String parsedStatesJson;
    @com.fasterxml.jackson.annotation.JsonIgnore @Transient private Map<String, String> parsedStates = Map.of();

    @Transient
    public DataMeta getDataMeta() {
        if (!java.util.Objects.equals(parsedStatesJson, dataFieldStates)) {
            Map<String, String> states = new LinkedHashMap<>();
            if (dataFieldStates != null) {
                try { new JSONObject(dataFieldStates).toMap().forEach((key, value) -> states.put(key, String.valueOf(value))); }
                catch (RuntimeException ignored) { /* A corrupt legacy marker is not publication evidence. */ }
            }
            parsedStates = Map.copyOf(states);
            parsedStatesJson = dataFieldStates;
        }
        Integer sampleSize = null;
        if (("COMPLETE".equals(dataQuality) || "PARTIAL".equals(dataQuality))
                && java.util.Set.of("OBSERVED", "DERIVED").contains(parsedStates.getOrDefault("gamesPlayed", "MISSING"))) {
            var games = ReflectionUtils.findField(getClass(), "gamesPlayed");
            if (games != null) {
                ReflectionUtils.makeAccessible(games);
                var raw = ReflectionUtils.getField(games, this);
                if (raw instanceof Number count && count.intValue() >= 0) sampleSize = count.intValue();
            }
        }
        return new DataMeta(dataSource == null ? "UNKNOWN" : dataSource, dataSourceUrl, dataSeason, dataScope,
                dataAsOf, dataSyncedAt, dataQuality == null ? "UNVERIFIED" : dataQuality, sampleSize, parsedStates);
    }

    protected <T> T publishedValue(String field, T raw) {
        return canDisplay(field) ? raw : null;
    }

    /** Migration snapshots keep the original UI values without claiming they were verified. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean hasRetainedLegacyValues() {
        return "UNKNOWN".equals(dataSource) && "UNVERIFIED".equals(dataQuality)
                && dataFieldStates == null && dataSyncedAt == null;
    }

    public boolean canDisplay(String field) {
        return hasRetainedLegacyValues() || getDataMeta().publishes(field);
    }

    /** Read backing fields rather than publication getters when stamping a successful snapshot. */
    public void markData(String source, String season, String scope, String... fields) {
        Map<String, String> states = new LinkedHashMap<>();
        boolean missing = false;
        for (String name : fields) {
            var field = ReflectionUtils.findField(getClass(), name);
            Object value = null;
            if (field != null) { ReflectionUtils.makeAccessible(field); value = ReflectionUtils.getField(field, this); }
            boolean present = value != null && (!(value instanceof String text) || !text.isBlank());
            states.put(name, present ? "OBSERVED" : "MISSING");
            missing |= !present;
        }
        dataSource = source; dataSeason = season; dataScope = scope;
        dataSyncedAt = Instant.now(); dataAsOf = null;
        dataQuality = missing ? "PARTIAL" : "COMPLETE";
        dataFieldStates = new JSONObject(states).toString();
    }
}
