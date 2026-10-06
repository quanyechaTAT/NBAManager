package com.nbamanager.service;

import com.nbamanager.domain.Player;
import com.nbamanager.repository.PlayerRepository;
import com.nbamanager.service.deepseek.DeepSeekClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private static final Pattern JUMP_BALL_PATTERN =
            Pattern.compile("(?i)^jump ball\\s+(.+?)\\s+vs\\.?\\s+(.+?):\\s*tip to\\s+(.+)$");
    private static final Pattern PERIOD_PATTERN =
            Pattern.compile("(?i)\\b(start|end) of (?:the )?(\\d+)(?:st|nd|rd|th)? (period|quarter|overtime)\\b");
    // 官方事件中的裁判署名不属于球员表，保留原来的姓名缩写。
    private static final Map<String, String> OFFICIAL_NAMES = Map.of(
            "s.foster", "S.福斯特", "j.capers", "J.卡珀斯", "t.ford", "T.福特", "haskin", "哈斯金");
    private static final Map<String, String> TEAM_NAMES = Map.ofEntries(
            Map.entry("Atlanta Hawks", "老鹰"), Map.entry("Boston Celtics", "凯尔特人"),
            Map.entry("Brooklyn Nets", "篮网"), Map.entry("Charlotte Hornets", "黄蜂"),
            Map.entry("Chicago Bulls", "公牛"), Map.entry("Cleveland Cavaliers", "骑士"),
            Map.entry("Dallas Mavericks", "独行侠"), Map.entry("Denver Nuggets", "掘金"),
            Map.entry("Detroit Pistons", "活塞"), Map.entry("Golden State Warriors", "勇士"),
            Map.entry("Houston Rockets", "火箭"), Map.entry("Indiana Pacers", "步行者"),
            Map.entry("Los Angeles Clippers", "快船"), Map.entry("LA Clippers", "快船"),
            Map.entry("Los Angeles Lakers", "湖人"), Map.entry("Memphis Grizzlies", "灰熊"),
            Map.entry("Miami Heat", "热火"), Map.entry("Milwaukee Bucks", "雄鹿"),
            Map.entry("Minnesota Timberwolves", "森林狼"), Map.entry("New Orleans Pelicans", "鹈鹕"),
            Map.entry("New York Knicks", "尼克斯"), Map.entry("Oklahoma City Thunder", "雷霆"),
            Map.entry("Orlando Magic", "魔术"), Map.entry("Philadelphia 76ers", "76人"),
            Map.entry("Phoenix Suns", "太阳"), Map.entry("Portland Trail Blazers", "开拓者"),
            Map.entry("Sacramento Kings", "国王"), Map.entry("San Antonio Spurs", "马刺"),
            Map.entry("Toronto Raptors", "猛龙"), Map.entry("Utah Jazz", "爵士"),
            Map.entry("Washington Wizards", "奇才"));
    private static final Map<String, String> TERMS = Map.ofEntries(
            Map.entry("Turnaround Fadeaway Bank Jump Shot", "转身后仰打板跳投"),
            Map.entry("Turnaround Bank Shot", "转身打板跳投"),
            Map.entry("Turnaround Fadeaway Shot", "转身后仰跳投"),
            Map.entry("Turnaround Fadeaway", "转身后仰跳投"),
            Map.entry("Turnaround Jump Shot", "转身跳投"),
            Map.entry("Driving Floating Bank Jump Shot", "突破打板抛投"),
            Map.entry("Driving Floating Jump Shot", "突破抛投"),
            Map.entry("Driving Finger Roll Layup", "突破挑篮"),
            Map.entry("Running Finger Roll Layup", "行进间挑篮"),
            Map.entry("Cutting Finger Roll Layup Shot", "空切挑篮"),
            Map.entry("Driving Reverse Layup", "突破反手上篮"),
            Map.entry("Running Reverse Layup", "行进间反手上篮"),
            Map.entry("Reverse Layup", "反手上篮"),
            Map.entry("Alley Oop Dunk Shot", "空接扣篮"),
            Map.entry("Alley Oop Layup shot", "空接上篮"),
            Map.entry("Alley Oop Dunk", "空接扣篮"),
            Map.entry("Putback Dunk Shot", "补扣"),
            Map.entry("Putback Layup Shot", "补篮"),
            Map.entry("Putback Layup", "补篮"),
            Map.entry("Tip Dunk Shot", "补扣"),
            Map.entry("Tip Layup Shot", "补篮"),
            Map.entry("Step Back Jump Shot", "后撤步跳投"),
            Map.entry("Fadeaway Jump Shot", "后仰跳投"),
            Map.entry("Fadeaway Shot", "后仰跳投"),
            Map.entry("Fadeaway Jumper", "后仰跳投"),
            Map.entry("Running Pull-Up Jump Shot", "行进间急停跳投"),
            Map.entry("Running Jump Shot", "行进间跳投"),
            Map.entry("Driving Bank Hook Shot", "突破打板勾手"),
            Map.entry("Heave", "超远投篮"),
            Map.entry("Pullup Jump Shot", "急停跳投"),
            Map.entry("Floating Jump Shot", "抛投"),
            Map.entry("Jump Bank Shot", "打板跳投"),
            Map.entry("Hook Bank Shot", "打板勾手"),
            Map.entry("Driving Hook Shot", "突破勾手"),
            Map.entry("Jump Hook Shot", "跳起勾手"),
            Map.entry("Hook Shot", "勾手投篮"),
            Map.entry("Driving Layup", "突破上篮"),
            Map.entry("Running Layup", "行进间上篮"),
            Map.entry("Cutting Layup Shot", "空切上篮"),
            Map.entry("Driving Dunk", "突破扣篮"),
            Map.entry("Running Dunk", "行进间扣篮"),
            Map.entry("Cutting Dunk Shot", "空切扣篮"),
            Map.entry("Dunk Shot", "扣篮"),
            Map.entry("Jump Shot", "跳投"),
            Map.entry("Layup Shot", "上篮"),
            Map.entry("Layup", "上篮"),
            Map.entry("Dunk", "扣篮"),
            Map.entry("Free Throw", "罚球"),
            Map.entry("Jump Ball", "跳球"),
            Map.entry("Bad Pass Turnover", "传球失误"),
            Map.entry("Lost Ball Turnover", "丢球失误"),
            Map.entry("Shot Clock Turnover", "进攻超时失误"),
            Map.entry("Shot Clock", "进攻超时"),
            Map.entry("Out of Bounds Lost Ball Turnover", "出界失误"),
            Map.entry("Out of Bounds", "出界"),
            Map.entry("Offensive Foul Turnover", "进攻犯规失误"),
            Map.entry("Traveling Turnover", "走步失误"),
            Map.entry("Double Dribble Turnover", "二次运球失误"),
            Map.entry("Shooting Foul", "投篮犯规"),
            Map.entry("Personal Foul", "个人犯规"),
            Map.entry("Personal Take Foul", "战术犯规"),
            Map.entry("Loose Ball Foul", "争抢球犯规"),
            Map.entry("Offensive Foul", "进攻犯规"),
            Map.entry("Technical Foul", "技术犯规"),
            Map.entry("L.B.FOUL", "争抢球犯规"),
            Map.entry("S.FOUL", "投篮犯规"),
            Map.entry("P.FOUL", "个人犯规"),
            Map.entry("OFF.FOUL", "进攻犯规"),
            Map.entry("T.FOUL", "技术犯规"),
            Map.entry("Kicked Ball", "踢球"),
            Map.entry("Defensive Goaltending", "防守干扰球"),
            Map.entry("Offensive Goaltending", "进攻干扰球"),
            Map.entry("Offensive interference", "进攻干扰球"),
            Map.entry("Violation", "违例"),
            Map.entry("Timeout", "暂停"),
            Map.entry("Regular", "常规"),
            Map.entry("Reg", "常规"),
            Map.entry("Short", "短"),
            Map.entry("Full", "长"),
            Map.entry("Challenge", "挑战"),
            Map.entry("Instant Replay", "即时回放"),
            Map.entry("Second", "秒"),
            Map.entry("Overturned", "改判"),
            Map.entry("Stands", "维持原判"),
            Map.entry("SUB", "换人"),
            Map.entry("FOR", "替换"),
            Map.entry("REBOUND", "篮板"),
            Map.entry("BLOCK", "盖帽"),
            Map.entry("STEAL", "抢断"),
            Map.entry("Turnover", "失误"),
            Map.entry("Foul", "犯规"),
            Map.entry("3PT", "三分"),
            Map.entry("2PT", "两分"),
            Map.entry("PTS", "分"),
            Map.entry("AST", "助攻"),
            Map.entry("BLK", "盖帽"),
            Map.entry("STL", "抢断"),
            Map.entry("Off", "进攻"),
            Map.entry("Def", "防守"),
            Map.entry("Flagrant", "恶意犯规"),
            Map.entry("Technical", "技术犯规"),
            Map.entry("makes", "命中"),
            Map.entry("misses", "投篮不中"),
            Map.entry("MISS", "未命中"),
            Map.entry("Game", "比赛"),
            Map.entry("Start", "开始"),
            Map.entry("End", "结束"));
    private static final Pattern TERM_PATTERN = Pattern.compile(
            "(?<![A-Za-z])(?:" + TERMS.keySet().stream()
                    .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                    .map(Pattern::quote).collect(java.util.stream.Collectors.joining("|"))
                    + ")(?![A-Za-z])", Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> TERMS_BY_KEY = TERMS.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(e -> e.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue));

    private final DeepSeekClient deepSeekClient;
    private final PlayerRepository playerRepository;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private Map<String, String> playerNameMap = new ConcurrentHashMap<>(OFFICIAL_NAMES);
    private Map<Long, String> playerNamesById = Map.of();
    private final Map<String, String> teamNameMap = buildTeamNameMap();
    private final Pattern teamNamePattern = compileNamePattern(teamNameMap);
    private volatile int consecutiveDeepSeekFailures = 0;
    private static final int MAX_CONSECUTIVE_FAILURES = 1;

    @PostConstruct
    public void init() {
        loadPlayerNameMap();
    }

    private void loadPlayerNameMap() {
        try {
            List<Player> players = playerRepository.findAll();
            Map<String, String> map = new ConcurrentHashMap<>(OFFICIAL_NAMES);
            Map<Long, String> byId = new java.util.HashMap<>();
            for (Player p : players) {
                if (p.getNameEn() != null && !p.getNameEn().isBlank()
                        && p.getName() != null && !p.getName().isBlank()
                        && CJK_PATTERN.matcher(p.getName()).find()) {
                    // 全名映射
                    map.put(p.getNameEn().toLowerCase(Locale.ROOT), p.getName());
                    if (p.getNbaPlayerId() != null) byId.put(p.getNbaPlayerId(), p.getName());
                    // 姓氏映射（取最后一个单词）
                    String[] parts = p.getNameEn().trim().split("\\s+");
                    if (parts.length >= 2) {
                        int lastIndex = parts.length - 1;
                        if (parts[lastIndex].matches("(?i)Jr\\.?|Sr\\.?|II|III|IV")) lastIndex--;
                        String lastName = parts[lastIndex].toLowerCase(Locale.ROOT);
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
            playerNamesById = Map.copyOf(byId);
            playerNamePattern = null;
            sortedPlayerNames = null;
            cache.clear();
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

            // 如果 DeepSeek 连续失败，直接用 fallback
            if (consecutiveDeepSeekFailures >= MAX_CONSECUTIVE_FAILURES) {
                batch.forEach(text -> cache.putIfAbsent(text, fallbackTranslate(text)));
                continue;
            }

            DeepSeekClient.ChatResponse response = deepSeekClient.chat(buildSystemPrompt(), buildUserPrompt(batch));
            if (!response.success()) {
                log.warn("DeepSeek translation failed: {}", response.error());
                consecutiveDeepSeekFailures++;
                batch.forEach(text -> cache.putIfAbsent(text, fallbackTranslate(text)));
                continue;
            }

            List<String> translated = parseJsonArray(response.content(), batch.size());
            if (translated == null) {
                log.warn("DeepSeek translation returned invalid JSON");
                consecutiveDeepSeekFailures++;
                batch.forEach(text -> cache.putIfAbsent(text, fallbackTranslate(text)));
                continue;
            }

            consecutiveDeepSeekFailures = 0;
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
            return translateTeamNames(timeoutMatcher.group(1).trim()) + " 暂停";
        }

        String translated = text.trim();
        Matcher jumpBall = JUMP_BALL_PATTERN.matcher(translated);
        if (jumpBall.matches()) {
            return translatePlayerNames(jumpBall.group(1)) + " 与 " + translatePlayerNames(jumpBall.group(2))
                    + " 跳球，拨给 " + translatePlayerNames(jumpBall.group(3));
        }
        boolean missed = translated.regionMatches(true, 0, "MISS ", 0, 5);
        if (missed) translated = translated.substring(5);
        translated = PERIOD_PATTERN.matcher(translated).replaceAll(match -> {
            String period = "overtime".equalsIgnoreCase(match.group(3)) ? "加时" : "节";
            return "第" + match.group(2) + period + ("start".equalsIgnoreCase(match.group(1)) ? "开始" : "结束");
        });
        translated = translated.replaceAll("(?i)(\\d+)(?:st|nd|rd|th)\\s+(?:period|quarter)\\b", "第$1节");
        translated = translated.replaceAll("(?i)FLAGRANT\\.FOUL\\.TYPE(\\d+)", "$1级恶意犯规");
        translated = translated.replaceAll("(\\d+(?:\\.\\d+)?)['’]", "$1英尺");
        translated = translated.replaceAll("(?i)\\b(\\d+) of (\\d+)\\b", "$2罚第$1次");
        translated = translated.replaceAll("(?i)\\bP(\\d+)\\.T(\\d+)\\b", "个人$1次，球队$2次");
        translated = translated.replaceAll("(?i)\\bP(\\d+)\\.PN\\b", "个人$1次，球队犯规已满");
        // 动作先于姓名，防止球员姓氏 Ball 等误匹配 Jump Ball、Lost Ball。
        translated = TERM_PATTERN.matcher(translated).replaceAll(match ->
                Matcher.quoteReplacement(TERMS_BY_KEY.get(match.group().toLowerCase(Locale.ROOT))));
        translated = translateTeamNames(translatePlayerNames(translated));
        if (missed) translated += " 未命中";
        translated = translated.replaceAll("\\s+", " ").trim();
        return translated;
    }

    private volatile Pattern playerNamePattern;
    private volatile Map<String, String> sortedPlayerNames;

    private String translatePlayerNames(String text) {
        if (playerNameMap.isEmpty()) return text;
        // 懒编译正则：只在 playerNameMap 变化时重建
        if (playerNamePattern == null || sortedPlayerNames == null) {
            buildPlayerNamePattern();
        }
        if (playerNamePattern == null) return text;

        Matcher matcher = playerNamePattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String match = matcher.group();
            String cn = sortedPlayerNames.get(match.toLowerCase(Locale.ROOT));
            if (cn != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(cn));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private synchronized void buildPlayerNamePattern() {
        if (playerNamePattern != null) return;
        // 按长度降序排列，避免短名误匹配
        sortedPlayerNames = playerNameMap.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()))
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, java.util.LinkedHashMap::new));
        String regex = sortedPlayerNames.keySet().stream()
                .map(Pattern::quote)
                .collect(java.util.stream.Collectors.joining("|"));
        if (!regex.isEmpty()) {
            playerNamePattern = Pattern.compile("(?<![A-Za-z])(?:" + regex + ")(?![A-Za-z])", Pattern.CASE_INSENSITIVE);
        }
    }

    private String translatePlayerName(String name) {
        if (name == null) return name;
        return playerNameMap.getOrDefault(name.toLowerCase(Locale.ROOT), name);
    }

    public String translatePlayerName(String name, Long nbaPlayerId) {
        String byId = nbaPlayerId == null ? null : playerNamesById.get(nbaPlayerId);
        return byId != null ? byId : translatePlayerName(name);
    }

    private static Map<String, String> buildTeamNameMap() {
        Map<String, String> names = new java.util.HashMap<>();
        TEAM_NAMES.forEach((english, chinese) -> {
            names.put(english.toLowerCase(Locale.ROOT), chinese);
            String nickname = english.endsWith("Trail Blazers") ? "Trail Blazers"
                    : english.substring(english.lastIndexOf(' ') + 1);
            names.put(nickname.toLowerCase(Locale.ROOT), chinese);
        });
        return Map.copyOf(names);
    }

    private static Pattern compileNamePattern(Map<String, String> names) {
        String choices = names.keySet().stream().sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .map(Pattern::quote).collect(java.util.stream.Collectors.joining("|"));
        return Pattern.compile("(?<![A-Za-z])(?:" + choices + ")(?![A-Za-z])", Pattern.CASE_INSENSITIVE);
    }

    private String translateTeamNames(String text) {
        return teamNamePattern.matcher(text).replaceAll(match ->
                Matcher.quoteReplacement(teamNameMap.get(match.group().toLowerCase(Locale.ROOT))));
    }
}
