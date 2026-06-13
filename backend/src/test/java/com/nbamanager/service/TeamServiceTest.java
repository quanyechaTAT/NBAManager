package com.nbamanager.service;

import com.nbamanager.domain.Team;
import com.nbamanager.repository.TeamRepository;
import com.nbamanager.web.dto.TeamDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @InjectMocks
    private TeamService teamService;

    private Team testTeam;

    @BeforeEach
    void setUp() {
        testTeam = new Team();
        testTeam.setId(1L);
        testTeam.setName("湖人");
        testTeam.setNameEn("Los Angeles Lakers");
        testTeam.setAbbreviation("LAL");
        testTeam.setCity("洛杉矶");
        testTeam.setConference("西部");
        testTeam.setWins(53);
        testTeam.setLosses(29);
    }

    @Test
    void testList() {
        Page<Team> page = new PageImpl<>(Arrays.asList(testTeam));
        when(teamRepository.findAll(any(PageRequest.class))).thenReturn(page);

        Page<TeamDto> result = teamService.list(null, PageRequest.of(0, 10));

        assertEquals(1, result.getContent().size());
        assertEquals("湖人", result.getContent().get(0).name());
        assertEquals("Los Angeles Lakers", result.getContent().get(0).nameEn());
        assertEquals("LAL", result.getContent().get(0).abbreviation());
        assertEquals(53, result.getContent().get(0).wins());
        assertEquals(29, result.getContent().get(0).losses());
    }

    @Test
    void testGet() {
        when(teamRepository.findById(1L)).thenReturn(Optional.of(testTeam));

        TeamDto result = teamService.get(1L);

        assertNotNull(result);
        assertEquals("湖人", result.name());
        assertEquals("Los Angeles Lakers", result.nameEn());
    }

    @Test
    void testGetNotFound() {
        when(teamRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(Exception.class, () -> teamService.get(999L));
    }
}
