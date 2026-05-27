package com.nbamanager.web.dto;

public record TeamRankDto(
        String teamName,
        Integer wins,
        Integer losses,
        Double gamesBehind,
        String conference) {}
