package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.DraftPick;
import com.nbamanager.repository.DraftPickRepository;
import com.nbamanager.web.dto.DraftPickDto;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DraftServiceTest {

    @InjectMocks
    private DraftService draftService;

    @Mock
    private DraftPickRepository draftPickRepository;

    @Test
    void importDraftPicksUpdatesExistingPlaceholderPick() {
        DraftPick placeholder = new DraftPick();
        placeholder.setYear(2026);
        placeholder.setRound(1);
        placeholder.setPickNumber(1);
        placeholder.setTeamName("待定");
        placeholder.setNotes("占位");
        when(draftPickRepository.findByYearAndRoundAndPickNumber(2026, 1, 1))
                .thenReturn(Optional.of(placeholder));
        when(draftPickRepository.save(any(DraftPick.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DraftPickDto officialPick = new DraftPickDto(
                null,
                2026,
                1,
                1,
                "奇才",
                "Washington Wizards",
                "AJ Dybantsa",
                "AJ Dybantsa",
                12345L,
                "选秀顺位第1位",
                null);

        int imported = draftService.importDraftPicks(List.of(officialPick));

        assertThat(imported).isEqualTo(1);
        assertThat(placeholder.getTeamName()).isEqualTo("奇才");
        assertThat(placeholder.getTeamNameEn()).isEqualTo("Washington Wizards");
        assertThat(placeholder.getPlayerName()).isEqualTo("AJ Dybantsa");
        assertThat(placeholder.getNbaPlayerId()).isEqualTo(12345L);
        assertThat(placeholder.getNotes()).isEqualTo("选秀顺位第1位");
    }
}
