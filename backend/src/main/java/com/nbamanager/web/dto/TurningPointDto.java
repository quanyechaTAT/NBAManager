package com.nbamanager.web.dto;

public record TurningPointDto(
        Long id,
        Integer period,
        String gameClock,
        String type,
        Double confidence,
        String factualReason,
        String aiExplanation,
        String citedEventIdsJson
) {}
