package com.nbamanager.web.dto;

public record EventExplainDto(
        String gameId,
        Long eventId,
        TimelineEventDto event,
        String explanation,
        String citedEventIdsJson,
        String citedStatKeysJson,
        String modelProvider,
        String modelName
) {}
