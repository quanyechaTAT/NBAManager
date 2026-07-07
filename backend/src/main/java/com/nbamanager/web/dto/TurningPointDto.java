package com.nbamanager.web.dto;

public record TurningPointDto(
        Long id,
        Integer period,
        String gameClock,
        String type,
        Double confidence,
        String factualReason,
        String aiExplanation,
        String citedEventIdsJson,
        Integer startEventOrder,
        Integer endEventOrder,
        Integer startHomeScore,
        Integer startAwayScore,
        Integer endHomeScore,
        Integer endAwayScore,
        Integer scoreSwing,
        Double impactScore,
        String impactLevel,
        String teamName
) {}
