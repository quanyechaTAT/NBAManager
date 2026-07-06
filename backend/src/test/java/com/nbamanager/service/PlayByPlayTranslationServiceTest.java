package com.nbamanager.service;

import com.nbamanager.service.mimo.MimoClient;
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
    void translateTexts_usesMimoJsonArrayAndCachesResult() {
        MimoClient mimoClient = mock(MimoClient.class);
        when(mimoClient.chat(anyString(), anyString()))
                .thenReturn(new MimoClient.ChatResponse(
                        "[\"勒布朗詹姆斯命中 2 分跳投\", \"戴维斯抢到防守篮板\"]",
                        "mimo-v2.5-pro",
                        true,
                        null
                ));

        PlayByPlayTranslationService service = new PlayByPlayTranslationService(mimoClient);

        List<String> translated = service.translateTexts(List.of(
                "LeBron James makes 2-foot jump shot",
                "Davis REBOUND (Def: 1)"
        ));
        List<String> cached = service.translateTexts(List.of("LeBron James makes 2-foot jump shot"));

        assertEquals("勒布朗詹姆斯命中 2 分跳投", translated.get(0));
        assertEquals("戴维斯抢到防守篮板", translated.get(1));
        assertEquals("勒布朗詹姆斯命中 2 分跳投", cached.get(0));
        verify(mimoClient, times(1)).chat(anyString(), anyString());
    }

    @Test
    void translateTexts_fallsBackToLocalBasketballTermsWhenMimoFails() {
        MimoClient mimoClient = mock(MimoClient.class);
        when(mimoClient.chat(anyString(), anyString()))
                .thenReturn(new MimoClient.ChatResponse(null, "mimo-v2.5-pro", false, "missing key"));

        PlayByPlayTranslationService service = new PlayByPlayTranslationService(mimoClient);

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
        MimoClient mimoClient = mock(MimoClient.class);
        PlayByPlayTranslationService service = new PlayByPlayTranslationService(mimoClient);

        List<String> translated = service.translateTexts(List.of("米切尔命中三分"));

        assertEquals("米切尔命中三分", translated.get(0));
        verify(mimoClient, times(0)).chat(anyString(), anyString());
    }
}
