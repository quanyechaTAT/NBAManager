package com.nbamanager.web.dto;

import com.nbamanager.domain.MatchRecord;
import com.nbamanager.service.MatchGameTypeResolver;
import java.time.LocalDate;

public record MatchRecordDto(
        Long id,
        String homeTeam,
        String awayTeam,
        Integer homeScore,
        Integer awayScore,
        LocalDate matchDate,
        String season,
        String status,
        String nbaGameId,
        String gameType
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
                m.getStatus(),
                m.getNbaGameId(),
                MatchGameTypeResolver.resolve(
                        m.getGameType(), m.getNbaGameId(), m.getMatchDate())
        );
    }
}
