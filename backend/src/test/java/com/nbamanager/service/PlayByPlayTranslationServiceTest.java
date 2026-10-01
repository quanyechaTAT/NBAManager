package com.nbamanager.service;

import com.nbamanager.service.deepseek.DeepSeekClient;
import com.nbamanager.repository.PlayerRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayByPlayTranslationServiceTest {

    @Test
    void translateTexts_usesDeepSeekJsonArrayAndCachesResult() {
        DeepSeekClient deepSeekClient = mock(DeepSeekClient.class);
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        when(playerRepository.findAll()).thenReturn(List.of());
        when(deepSeekClient.chat(anyString(), anyString()))
                .thenReturn(new DeepSeekClient.ChatResponse(
                        "[\"勒布朗詹姆斯命中 2 分跳投\", \"戴维斯抢到防守篮板\"]",
                        "deepseek-v4-flash",
                        true,
                        null
                ));

        PlayByPlayTranslationService service = new PlayByPlayTranslationService(deepSeekClient, playerRepository);

        List<String> translated = service.translateTexts(List.of(
                "LeBron James makes 2-foot jump shot",
                "Davis REBOUND (Def: 1)"
        ));
        List<String> cached = service.translateTexts(List.of("LeBron James makes 2-foot jump shot"));

        assertEquals("勒布朗詹姆斯命中 2 分跳投", translated.get(0));
        assertEquals("戴维斯抢到防守篮板", translated.get(1));
        assertEquals("勒布朗詹姆斯命中 2 分跳投", cached.get(0));
        verify(deepSeekClient, times(1)).chat(anyString(), anyString());
    }

    @Test
    void translateTexts_fallsBackToLocalBasketballTermsWhenDeepSeekFails() {
        DeepSeekClient deepSeekClient = mock(DeepSeekClient.class);
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        when(playerRepository.findAll()).thenReturn(List.of());
        when(deepSeekClient.chat(anyString(), anyString()))
                .thenReturn(new DeepSeekClient.ChatResponse(null, "deepseek-v4-flash", false, "missing key"));

        PlayByPlayTranslationService service = new PlayByPlayTranslationService(deepSeekClient, playerRepository);

        List<String> translated = service.translateTexts(List.of(
                "Mitchell makes 3PT Jump Shot",
                "Turnover by Thompson",
                "Timeout Cleveland Cavaliers"
        ));

        assertEquals("Mitchell 命中 三分 跳投", translated.get(0));
        assertEquals("Thompson 失误", translated.get(1));
        assertEquals("Cleveland Cavaliers 暂停", translated.get(2));
    }

    @Test
    void translateTexts_keepsAlreadyChineseText() {
        DeepSeekClient deepSeekClient = mock(DeepSeekClient.class);
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        when(playerRepository.findAll()).thenReturn(List.of());
        PlayByPlayTranslationService service = new PlayByPlayTranslationService(deepSeekClient, playerRepository);

        List<String> translated = service.translateTexts(List.of("米切尔命中三分"));

        assertEquals("米切尔命中三分", translated.get(0));
        verify(deepSeekClient, times(0)).chat(anyString(), anyString());
    }
}
