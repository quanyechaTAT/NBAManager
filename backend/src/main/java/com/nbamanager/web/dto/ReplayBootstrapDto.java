package com.nbamanager.web.dto;

import com.nbamanager.domain.replay.ReplayPageState;
import java.util.List;
import java.util.Objects;

public record ReplayBootstrapDto(
        String gameId,
        ReplayPageState state,
        ReplayQualityDto quality,
        List<TimelineEventDto> timeline,
        List<MomentumDto> momentum,
        List<HighlightDto> highlights,
        List<TurningPointDto> turningPoints,
        GameSummaryDto cachedSummary,
        ReplayErrorDto error) {

    public ReplayBootstrapDto {
        Objects.requireNonNull(gameId, "gameId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(quality, "quality");
        timeline = timeline == null ? List.of() : List.copyOf(timeline);
        momentum = momentum == null ? List.of() : List.copyOf(momentum);
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
        turningPoints = turningPoints == null ? List.of() : List.copyOf(turningPoints);
    }
}
