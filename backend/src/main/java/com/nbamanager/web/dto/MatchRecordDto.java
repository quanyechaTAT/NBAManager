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
        String gameType,
        DataMeta dataMeta
) {
    public MatchRecordDto(Long id, String homeTeam, String awayTeam, Integer homeScore, Integer awayScore,
                          LocalDate matchDate, String season, String status, String nbaGameId, String gameType) {
        this(id, homeTeam, awayTeam, homeScore, awayScore, matchDate, season, status, nbaGameId, gameType, DataMeta.unverified());
    }
    public static MatchRecordDto from(MatchRecord m) {
        boolean started = !"SCHEDULED".equalsIgnoreCase(m.getStatus());
        return new MatchRecordDto(
                m.getId(),
                m.getHomeTeam(),
                m.getAwayTeam(),
                started && m.canDisplay("homeScore") ? m.getHomeScore() : null,
                started && m.canDisplay("awayScore") ? m.getAwayScore() : null,
                m.getMatchDate(),
                m.getSeason(),
                m.getStatus(),
                m.getNbaGameId(),
                MatchGameTypeResolver.resolve(
                        m.getGameType(), m.getNbaGameId(), m.getMatchDate()),
                m.getDataMeta()
        );
    }
}
