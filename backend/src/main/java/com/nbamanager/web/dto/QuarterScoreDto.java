package com.nbamanager.web.dto;

public record QuarterScoreDto(
        Integer period,
        Integer homeScore,
        Integer awayScore
) {}
