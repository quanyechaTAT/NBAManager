package com.nbamanager.discovery;

import com.nbamanager.domain.MatchRecord;
import com.nbamanager.repository.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DiscoveryCatalogTest {
    @Test void scheduledSearchResultsFollowTheExistingScoreVisibilityContract() {
        var matches=mock(MatchRecordRepository.class);
        var catalog=new DiscoveryCatalog(mock(PlayerRepository.class),mock(TeamRepository.class),matches,
                mock(GameNewsRepository.class),mock(PostRepository.class),mock(JdbcTemplate.class));
        var match=new MatchRecord(); match.setId(1L); match.setHomeTeam("湖人"); match.setAwayTeam("勇士");
        match.setMatchDate(LocalDate.of(2026,10,15)); match.setSeason("2026-27"); match.setStatus("SCHEDULED");
        match.setHomeScore(120); match.setAwayScore(114);
        match.markData("MANUAL","2026-27","REGULAR_SEASON","homeScore","awayScore","status");
        when(matches.findAllById(any())).thenReturn(List.of(match));
        assertThat(catalog.hydrate(List.of("MATCH:1")).get("MATCH:1").summary()).contains("--:--");
        match.setStatus("FINISHED");
        assertThat(catalog.hydrate(List.of("MATCH:1")).get("MATCH:1").summary()).contains("120:114");
    }
}
