package com.nbamanager.service;

import com.nbamanager.domain.DraftPick;
import com.nbamanager.repository.DraftPickRepository;
import com.nbamanager.web.dto.DraftPickDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DraftServiceTest {

    @Mock
    private DraftPickRepository draftPickRepository;

    @InjectMocks
    private DraftService draftService;

    private DraftPick testDraftPick;
    private DraftPickDto testDraftPickDto;

    @BeforeEach
    void setUp() {
        testDraftPick = new DraftPick();
        testDraftPick.setId(1L);
        testDraftPick.setYear(2025);
        testDraftPick.setRound(1);
        testDraftPick.setPickNumber(1);
        testDraftPick.setTeamName("独行侠");
        testDraftPick.setTeamNameEn("Dallas Mavericks");
        testDraftPick.setPlayerName("库珀·弗拉格");
        testDraftPick.setPlayerNameEn("Cooper Flagg");
        testDraftPick.setCreateTime(LocalDateTime.now());

        testDraftPickDto = new DraftPickDto(
                1L,
                2025,
                1,
                1,
                "独行侠",
                "Dallas Mavericks",
                "库珀·弗拉格",
                "Cooper Flagg",
                "选秀顺位第1位",
                LocalDateTime.now()
        );
    }

    @Test
    void testListByYear() {
        when(draftPickRepository.findByYear(2025)).thenReturn(Arrays.asList(testDraftPick));

        List<DraftPickDto> result = draftService.listByYear(2025);

        assertEquals(1, result.size());
        assertEquals("独行侠", result.get(0).teamName());
        assertEquals("库珀·弗拉格", result.get(0).playerName());
        verify(draftPickRepository).findByYear(2025);
    }

    @Test
    void testListByTeam() {
        when(draftPickRepository.findByTeamName("独行侠")).thenReturn(Arrays.asList(testDraftPick));

        List<DraftPickDto> result = draftService.listByTeam("独行侠");

        assertEquals(1, result.size());
        assertEquals("独行侠", result.get(0).teamName());
        verify(draftPickRepository).findByTeamName("独行侠");
    }

    @Test
    void testImportDraftPicks() {
        when(draftPickRepository.existsByYearAndRoundAndPickNumber(2025, 1, 1)).thenReturn(false);

        List<DraftPickDto> picks = Arrays.asList(testDraftPickDto);
        int imported = draftService.importDraftPicks(picks);

        assertEquals(1, imported);
        verify(draftPickRepository).save(any());
    }

    @Test
    void testImportDraftPicksDuplicate() {
        when(draftPickRepository.existsByYearAndRoundAndPickNumber(2025, 1, 1)).thenReturn(true);

        List<DraftPickDto> picks = Arrays.asList(testDraftPickDto);
        int imported = draftService.importDraftPicks(picks);

        assertEquals(0, imported);
        verify(draftPickRepository, never()).save(any());
    }
}
