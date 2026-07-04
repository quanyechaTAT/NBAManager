package com.nbamanager.web.dto;

public record MomentumDto(
        Long id,
        Integer eventOrder,
        Integer period,
        String gameClock,
        Integer homeScore,
        Integer awayScore,
        Integer scoreMargin
) {}
