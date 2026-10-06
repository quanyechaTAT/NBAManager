package com.nbamanager.service;

import com.nbamanager.service.deepseek.DeepSeekClient;
import com.nbamanager.domain.Player;
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
    void localTranslationCoversNbaDescriptionsWithoutCallingTheModel() {
        DeepSeekClient client = mock(DeepSeekClient.class);
        PlayerRepository players = mock(PlayerRepository.class);
        when(players.findAll()).thenReturn(List.of(
                player("Victor Wembanyama", "维克托·文班亚马"),
                player("Karl-Anthony Towns", "卡尔·安东尼·唐斯"),
                player("OG Anunoby", "OG·阿奴诺比"),
                player("Josh Hart", "约什·哈特"),
                player("Lonzo Ball", "朗佐·鲍尔")));
        PlayByPlayTranslationService service = new PlayByPlayTranslationService(client, players);
        service.init();

        assertEquals("文班亚马 与 唐斯 跳球，拨给 阿奴诺比",
                service.translateText("Jump Ball Wembanyama vs. Towns: Tip to Anunoby"));
        assertEquals("阿奴诺比 篮板 (进攻:0 防守:1)",
                service.translateText("Anunoby REBOUND (Off:0 Def:1)"));
        assertEquals("哈特 19英尺 转身打板跳投 未命中",
                service.translateText("MISS Hart 19' Turnaround Bank Shot"));
        assertEquals("鲍尔 丢球失误 (个人1次，球队1次)",
                service.translateText("Ball Lost Ball Turnover (P1.T1)"));
        assertEquals("鲍尔 违例:踢球 (S.福斯特)",
                service.translateText("Ball Violation:Kicked Ball (S.Foster)"));
        assertEquals("唐斯 18英尺 转身后仰跳投 (2 分)",
                service.translateText("Towns 18' Turnaround Fadeaway (2 PTS)"));
        verify(client, times(0)).chat(anyString(), anyString());
    }

    @Test
    void localTranslationHandlesMixedChineseAndEnglishWithoutPartialNameMatches() {
        PlayerRepository players = mock(PlayerRepository.class);
        when(players.findAll()).thenReturn(List.of(player("Josh Hart", "约什·哈特")));
        PlayByPlayTranslationService service = new PlayByPlayTranslationService(mock(DeepSeekClient.class), players);
        service.init();

        assertEquals("哈特 篮板 (防守:1)", service.translateText("哈特 REBOUND (Def:1)"));
        assertEquals("哈特曼 篮板", service.translateText("哈特曼 REBOUND"));
        assertEquals("Hartenstein 篮板", service.translateText("Hartenstein REBOUND"));
        assertEquals("第1节开始 (8:44 PM EST)", service.translateText("Start of 1st Period (8:44 PM EST)"));
        assertEquals("尼克斯 暂停:常规", service.translateText("Knicks Timeout:Regular"));
        assertEquals("哈特 罚球 2罚第1次 (3 分)", service.translateText("Hart Free Throw 1 of 2 (3 PTS)"));
        assertEquals("哈特 投篮犯规 (个人2次，球队1次)", service.translateText("Hart S.FOUL (P2.T1)"));
        assertEquals("即时回放第2节 (9:41 PM EST)", service.translateText("Instant Replay2nd Period (9:41 PM EST)"));
        assertEquals("哈特 1级恶意犯规 (个人1次，球队2次)", service.translateText("Hart FLAGRANT.FOUL.TYPE1 (P1.T2)"));
        assertEquals("哈斯金", service.translatePlayerName("Haskin", 321L));
    }

    private Player player(String english, String chinese) {
        Player player = new Player();
        player.setNameEn(english);
        player.setName(chinese);
        return player;
    }

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
        assertEquals("骑士 暂停", translated.get(2));
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
