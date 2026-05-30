package com.nbamanager.web.dto;

import com.nbamanager.domain.MatchRecord;
import java.time.LocalDate;

public record MatchRecordDto(
        Long id,
        String homeTeam,
        String awayTeam,
        Integer homeScore,
        Integer awayScore,
        LocalDate matchDate,
        String season,
        String status
) {
    public static MatchRecordDto from(MatchRecord m) {
        return new MatchRecordDto(
                m.getId(),
                m.getHomeTeam(),
                m.getAwayTeam(),
                m.getHomeScore(),
                m.getAwayScore(),
                m.getMatchDate(),
                m.getSeason(),
                m.getStatus()
        );
    }
}
