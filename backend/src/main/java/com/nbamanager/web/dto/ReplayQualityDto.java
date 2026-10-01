package com.nbamanager.web.dto;

import com.nbamanager.domain.replay.ReplayQualityLevel;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public record ReplayQualityDto(
        ReplayQualityLevel level,
        int eventCount,
        int scoringEventCount,
        List<Integer> periodsCovered,
        String source,
        String sourceLabel,
        boolean synthetic,
        boolean finalScoreVerified,
        boolean monotonicScores,
        LocalDateTime lastSyncedAt,
        List<String> warnings,
        String factsHash) {

    public ReplayQualityDto {
        Objects.requireNonNull(level, "level");
        periodsCovered = periodsCovered == null ? List.of() : List.copyOf(periodsCovered);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
