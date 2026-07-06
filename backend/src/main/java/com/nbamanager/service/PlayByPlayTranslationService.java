package com.nbamanager.service;

import com.nbamanager.domain.Player;
import com.nbamanager.repository.PlayerRepository;
import com.nbamanager.service.mimo.MimoClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlayByPlayTranslationService {

    private static final int MAX_BATCH_SIZE = 40;
    private static final Pattern CJK_PATTERN = Pattern.compile("[\\u4e00-\\u9fff]");
    private static final Pattern TURNOVER_BY_PATTERN =
            Pattern.compile("(?i)^turnover\\s+by\\s+(.+)$");
    private static final Pattern TIMEOUT_PATTERN =
            Pattern.compile("(?i)^timeout\\s+(.+)$");

    private final MimoClient mimoClient;
    private final PlayerRepository playerRepository;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private Map<String, String> playerNameMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadPlayerNameMap();
    }

    private void loadPlayerNameMap() {
        try {
            List<Player> players = playerRepository.findAll();
            Map<String, String> map = new ConcurrentHashMap<>();
            for (Player p : players) {
                if (p.getNameEn() != null && !p.getNameEn().isBlank()
                        && p.getName() != null && !p.getName().isBlank()
                        && !p.getName().equals(p.getNameEn())) {
                    // 全名映射
                    map.put(p.getNameEn(), p.getName());
                    // 姓氏映射（取最后一个单词）
                    String[] parts = p.getNameEn().trim().split("\\s+");
                    if (parts.length >= 2) {
                        String lastName = parts[parts.length - 1];
                        if (lastName.length() >= 3 && !map.containsKey(lastName)) {
                            // 取中文名的最后部分作为姓氏
                            String cnName = p.getName();
                            String[] cnParts = cnName.split("[·\\s]+");
                            String cnLastName = cnParts[cnParts.length - 1];
                            map.put(lastName, cnLastName);
                        }
                    }
                }
            }
            playerNameMap = map;
            log.info("Loaded {} player name translations (including last names)", map.size());
        } catch (Exception e) {
            log.warn("Failed to load player name map: {}", e.getMessage());
        }
    }

    public List<String> translateTexts(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        Map<String, String> pending = new LinkedHashMap<>();
        for (String text : texts) {
            if (!shouldTranslate(text) || cache.containsKey(text)) {
                continue;
            }
            pending.put(text, text);
        }

        if (!pending.isEmpty()) {
            translatePending(new ArrayList<>(pending.keySet()));
        }

        List<String> result = new ArrayList<>(texts.size());
        for (String text : texts) {
            result.add(translateText(text));
        }
        return result;
    }

    public String translateText(String text) {
        if (!shouldTranslate(text)) {
            return text;
        }
        return cache.computeIfAbsent(text, this::fallbackTranslate);
    }

    private void translatePending(List<String> pendingTexts) {
        for (int start = 0; start < pendingTexts.size(); start += MAX_BATCH_SIZE) {
            int end = Math.min(start + MAX_BATCH_SIZE, pendingTexts.size());
            List<String> batch = pendingTexts.subList(start, end);

            MimoClient.ChatResponse response = mimoClient.chat(buildSystemPrompt(), buildUserPrompt(batch));
            if (!response.success()) {
                log.warn("Mimo play-by-play translation failed: {}", response.error());
                batch.forEach(text -> cache.putIfAbsent(text, fallbackTranslate(text)));
                continue;
            }

            List<String> translated = parseJsonArray(response.content(), batch.size());
            if (translated == null) {
                log.warn("Mimo play-by-play translation returned invalid JSON: {}", response.content());
                batch.forEach(text -> cache.putIfAbsent(text, fallbackTranslate(text)));
                continue;
            }

            for (int i = 0; i < batch.size(); i++) {
                String translatedText = translated.get(i);
                if (translatedText == null || translatedText.isBlank()) {
                    translatedText = fallbackTranslate(batch.get(i));
                }
                cache.put(batch.get(i), translatedText.trim());
            }
        }
    }

    private boolean shouldTranslate(String text) {
        return text != null
                && !text.isBlank()
                && !CJK_PATTERN.matcher(text).find()
                && containsLatin(text);
    }

    private boolean containsLatin(String text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) {
                return true;
            }
        }
        return false;
    }

    private String buildSystemPrompt() {
        return """
                你是 NBA Play-by-Play 数据翻译器。只把输入的逐回合事件文案翻译成简体中文。
                规则：
                1. 不新增任何比赛事实，不改比分、时间、节次。
                2. 球员名必须翻译为中文（如 LeBron James -> 勒布朗·詹姆斯，Wembanyama -> 文班亚马）。
                3. 球队名必须翻译为中文（如 Lakers -> 湖人，Spurs -> 马刺）。
                4. 篮球术语翻译（如 Layup -> 上篮，BLOCK -> 盖帽，REBOUND -> 篮板）。
                5. 保留原数组顺序，输出长度必须与输入一致。
                6. 只输出 JSON 字符串数组，不要 Markdown，不要解释。
                """;
    }

    private String buildUserPrompt(List<String> batch) {
        JSONArray input = new JSONArray(batch);
        return "请翻译以下 NBA 逐回合事件文案，返回 JSON 字符串数组：\n" + input;
    }

    private List<String> parseJsonArray(String content, int expectedSize) {
        if (content == null || content.isBlank()) {
            return null;
        }

        String json = content.trim();
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }

        try {
            JSONArray arr = new JSONArray(json);
            if (arr.length() != expectedSize) {
                return null;
            }
            List<String> result = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                result.add(arr.optString(i, ""));
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private String fallbackTranslate(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        Matcher turnoverMatcher = TURNOVER_BY_PATTERN.matcher(text.trim());
        if (turnoverMatcher.matches()) {
            String playerName = turnoverMatcher.group(1).trim();
            return translatePlayerName(playerName) + " 失误";
        }

        Matcher timeoutMatcher = TIMEOUT_PATTERN.matcher(text.trim());
        if (timeoutMatcher.matches()) {
            return timeoutMatcher.group(1).trim() + " 暂停";
        }

        String translated = text;
        // 先翻译球员名（按名字长度降序，避免短名误匹配长名）
        translated = translatePlayerNames(translated);
        // 再翻译篮球术语
        translated = replaceIgnoreCase(translated, "makes", "命中");
        translated = replaceIgnoreCase(translated, "misses", "投篮不中");
        translated = replaceIgnoreCase(translated, "3PT", "三分");
        translated = replaceIgnoreCase(translated, "2PT", "两分");
        translated = replaceIgnoreCase(translated, "Jump Shot", "跳投");
        translated = replaceIgnoreCase(translated, "Layup", "上篮");
        translated = replaceIgnoreCase(translated, "Dunk", "扣篮");
        translated = replaceIgnoreCase(translated, "Free Throw", "罚球");
        translated = replaceIgnoreCase(translated, "REBOUND", "篮板");
        translated = replaceIgnoreCase(translated, "Def:", "防守:");
        translated = replaceIgnoreCase(translated, "Off:", "进攻:");
        translated = replaceIgnoreCase(translated, "BLOCK", "盖帽");
        translated = replaceIgnoreCase(translated, "STEAL", "抢断");
        translated = replaceIgnoreCase(translated, "Turnover", "失误");
        translated = replaceIgnoreCase(translated, "Foul", "犯规");
        translated = replaceIgnoreCase(translated, "Timeout", "暂停");
        translated = translated.replaceAll("\\s+", " ").trim();
        return translated;
    }

    private String translatePlayerNames(String text) {
        if (playerNameMap.isEmpty()) return text;
        // 按名字长度降序排序，避免短名误匹配
        List<Map.Entry<String, String>> sorted = playerNameMap.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()))
                .toList();
        String result = text;
        for (Map.Entry<String, String> entry : sorted) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private String translatePlayerName(String name) {
        if (name == null) return name;
        return playerNameMap.getOrDefault(name, name);
    }

    private String replaceIgnoreCase(String input, String target, String replacement) {
        return input.replaceAll("(?i)" + Pattern.quote(target), Matcher.quoteReplacement(replacement));
    }
}
