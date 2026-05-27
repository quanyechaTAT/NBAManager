package com.nbamanager.web.dto;

public record PlayerDto(
        Long id,
        String name,
        Long teamId,
        String teamName,
        String position,
        Double pointsPerGame,
        Double reboundsPerGame,
        Double assistsPerGame,
        Double stealsPerGame) {}
