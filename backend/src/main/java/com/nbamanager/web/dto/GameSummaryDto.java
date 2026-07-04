package com.nbamanager.web.dto;

public record GameSummaryDto(
        String gameId,
        String summaryType,
        String content,
        String citedEventIdsJson,
        String citedStatKeysJson,
        String modelProvider,
        String modelName
) {}
