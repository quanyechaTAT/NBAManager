package com.nbamanager.service;

import com.nbamanager.domain.MatchRecord;
import com.nbamanager.repository.MatchRecordRepository;
import com.nbamanager.web.dto.MatchRecordDto;
import java.time.LocalDate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MatchRecordPublicationTest {
    private MatchRecord match(int id) {
        var row = new MatchRecord(); row.setId((long) id); row.setHomeTeam("A"); row.setAwayTeam("B");
        row.setHomeScore(0); row.setAwayScore(110); row.setMatchDate(LocalDate.of(2020, 1, 1).plusDays(id));
        row.setSeason("2019-20"); row.setStatus("FINISHED"); row.setGameType("REGULAR_SEASON");
        return row;
    }
    @Test void unverifiedScoresStayStoredButAreNotPublishedAndObservedZeroIsPreserved() {
        var row = match(1);
        assertThat(MatchRecordDto.from(row).homeScore()).isNull();
        assertThat(row.getHomeScore()).isZero(); assertThat(row.getAwayScore()).isEqualTo(110);
        row.markData("NBA_API", row.getSeason(), row.getGameType(), "homeScore", "awayScore", "status");
        assertThat(MatchRecordDto.from(row).homeScore()).isZero();
        assertThat(MatchRecordDto.from(row).dataMeta().source()).isEqualTo("NBA_API");
    }
    @Test void teamRecordsRetainAllRecordedSeasonsAndMoreThanTwentyFiveGames() {
        var repository = mock(MatchRecordRepository.class);
        var records = IntStream.rangeClosed(1, 30).mapToObj(this::match).toList();
        when(repository.findByTeamName("A")).thenReturn(records);
        var result = new MatchRecordService(repository).getByTeam("A");
        assertThat(result).hasSize(30); assertThat(result.get(0).id()).isEqualTo(30L);
        assertThat(result.get(0).homeScore()).isNull();
    }
    @Test void cleanupDoesNotDeleteScheduledOrUnverifiedRecords() {
        var repository = mock(MatchRecordRepository.class);
        var scheduled = match(1); scheduled.setStatus("SCHEDULED");
        when(repository.findAll()).thenReturn(java.util.List.of(scheduled, match(2)));
        assertThat(new MatchRecordService(repository).cleanInvalidData()).isZero();
        verify(repository).deleteAll(java.util.List.of());
    }
    @Test void scheduledMatchCannotPublishOldScoresEvenWhenMarkedObserved() {
        var row = match(1);
        row.setStatus("SCHEDULED"); row.setHomeScore(114); row.setAwayScore(144);
        row.markData("NBA_API", row.getSeason(), row.getGameType(), "homeScore", "awayScore", "status");
        var result = MatchRecordDto.from(row);
        assertThat(result.homeScore()).isNull();
        assertThat(result.awayScore()).isNull();
        assertThat(row.getHomeScore()).isEqualTo(114);
    }
}
