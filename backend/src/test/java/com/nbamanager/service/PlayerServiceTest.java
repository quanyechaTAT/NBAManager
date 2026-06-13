package com.nbamanager.service;

import com.nbamanager.domain.Player;
import com.nbamanager.domain.Team;
import com.nbamanager.repository.PlayerRepository;
import com.nbamanager.repository.TeamRepository;
import com.nbamanager.web.dto.PlayerDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayerServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private TeamRepository teamRepository;

    @InjectMocks
    private PlayerService playerService;

    private Player testPlayer;
    private Team testTeam;

    @BeforeEach
    void setUp() {
        testTeam = new Team();
        testTeam.setId(1L);
        testTeam.setName("湖人");
        testTeam.setNameEn("Los Angeles Lakers");
        testTeam.setAbbreviation("LAL");

        testPlayer = new Player();
        testPlayer.setId(1L);
        testPlayer.setNbaPlayerId(2544L);
        testPlayer.setName("勒布朗·詹姆斯");
        testPlayer.setNameEn("LeBron James");
        testPlayer.setTranslationStatus("MAPPED");
        testPlayer.setTeam(testTeam);
        testPlayer.setPosition("小前锋");
        testPlayer.setPointsPerGame(24.7);
        testPlayer.setReboundsPerGame(6.8);
        testPlayer.setAssistsPerGame(7.1);
        testPlayer.setStealsPerGame(1.1);
        testPlayer.setGamesPlayed(70);
        testPlayer.setMinutesPerGame(34.2);
    }

    @Test
    void testGet() {
        when(playerRepository.findById(1L)).thenReturn(Optional.of(testPlayer));

        PlayerDto result = playerService.get(1L);

        assertNotNull(result);
        assertEquals("勒布朗·詹姆斯", result.name());
        assertEquals("LeBron James", result.nameEn());
        assertEquals("MAPPED", result.translationStatus());
        assertEquals(24.7, result.pointsPerGame());
    }

    @Test
    void testGetNotFound() {
        when(playerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(Exception.class, () -> playerService.get(999L));
    }
}
