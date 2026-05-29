package com.nbamanager.config;

import com.nbamanager.domain.GameNews;
import com.nbamanager.domain.Player;
import com.nbamanager.domain.Role;
import com.nbamanager.domain.Team;
import com.nbamanager.domain.UserAccount;
import com.nbamanager.repository.GameNewsRepository;
import com.nbamanager.repository.PlayerRepository;
import com.nbamanager.repository.TeamRepository;
import com.nbamanager.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private final UserAccountRepository userAccountRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final GameNewsRepository gameNewsRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    @Bean
    @Transactional
    CommandLineRunner seedData() {
        return args -> {
            boolean hasUsers = userAccountRepository.count() > 0;
            if (hasUsers) {
                log.info("数据库中已有用户数据，跳过用户/球队/球员初始化。");
            } else {
                log.info("开始初始化演示数据...");
                seedUsersAndTeams();
            }

            // 赛事资讯独立初始化：只要 game_news 表为空就写入
            if (gameNewsRepository.count() == 0) {
                log.info("开始初始化赛事资讯...");
                seedNews();
                log.info("演示数据初始化完成！");
            }

            // 补足已有球员的场均抢断数据（DDL新增字段后为NULL）
            List<Player> allPlayers = playerRepository.findAll();
            boolean needsStealsFix = allPlayers.stream().anyMatch(p -> p.getStealsPerGame() == null);
            if (needsStealsFix) {
                log.info("补足球员场均抢断数据...");
                for (Player p : allPlayers) {
                    if (p.getStealsPerGame() == null) {
                        p.setStealsPerGame(1.0);
                    }
                }
                playerRepository.saveAll(allPlayers);
            }

            // 迁移旧数据：将遗留的 game_time 列数据填充到 game_start_time / game_end_time
            @SuppressWarnings("unchecked")
            List<Object[]> nullRows = entityManager.createNativeQuery(
                    "SELECT id FROM game_news WHERE game_start_time IS NULL").getResultList();
            if (!nullRows.isEmpty()) {
                log.info("迁移旧赛事资讯的时间数据（共{}条）...", nullRows.size());
                entityManager.createNativeQuery(
                        "UPDATE game_news SET game_start_time = game_time, game_end_time = DATE_ADD(game_time, INTERVAL 2 HOUR) WHERE game_start_time IS NULL")
                        .executeUpdate();
            }
        };
    }

    private void seedUsersAndTeams() {

            UserAccount admin = new UserAccount();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRole(Role.ADMIN);
            userAccountRepository.save(admin);

            UserAccount user = new UserAccount();
            user.setUsername("user");
            user.setPassword(passwordEncoder.encode("user123"));
            user.setRole(Role.USER);
            userAccountRepository.save(user);

            List<Team> teams = new ArrayList<>();
            teams.add(team("湖人", "洛杉矶", "西部", 47, 35));
            teams.add(team("勇士", "旧金山", "西部", 46, 36));
            teams.add(team("凯尔特人", "波士顿", "东部", 64, 18));
            teams.add(team("雄鹿", "密尔沃基", "东部", 48, 34));
            teams.add(team("掘金", "丹佛", "西部", 57, 25));
            teams.add(team("太阳", "菲尼克斯", "西部", 49, 33));
            teamRepository.saveAll(teams);

            Team lakers = teams.get(0);
            Team warriors = teams.get(1);
            Team celtics = teams.get(2);
            Team bucks = teams.get(3);
            Team nuggets = teams.get(4);
            Team suns = teams.get(5);

            List<Player> players = new ArrayList<>();
            players.add(player("勒布朗·詹姆斯", lakers, "小前锋", 25.7, 7.3, 8.3, 1.3));
            players.add(player("安东尼·戴维斯", lakers, "大前锋", 24.7, 12.6, 3.5, 1.2));
            players.add(player("斯蒂芬·库里", warriors, "控卫", 26.4, 4.5, 5.1, 0.9));
            players.add(player("克莱·汤普森", warriors, "分卫", 17.9, 3.3, 2.3, 0.6));
            players.add(player("杰森·塔图姆", celtics, "小前锋", 26.9, 8.1, 4.9, 1.0));
            players.add(player("杰伦·布朗", celtics, "分卫", 23.0, 5.5, 3.6, 1.1));
            players.add(player("扬尼斯·阿德托昆博", bucks, "大前锋", 30.4, 11.5, 6.5, 1.2));
            players.add(player("达米安·利拉德", bucks, "控卫", 24.3, 4.4, 7.0, 1.0));
            players.add(player("尼古拉·约基奇", nuggets, "中锋", 26.4, 12.4, 9.0, 1.4));
            players.add(player("贾马尔·穆雷", nuggets, "控卫", 21.2, 4.1, 6.5, 0.9));
            players.add(player("凯文·杜兰特", suns, "小前锋", 27.1, 6.6, 5.0, 0.8));
            players.add(player("德文·布克", suns, "分卫", 27.1, 4.5, 6.9, 0.9));
            playerRepository.saveAll(players);
    }

    private void seedNews() {
            LocalDate today = LocalDate.now();
            List<GameNews> newsList = new ArrayList<>();
            newsList.add(news("🔥 湖人 VS 勇士巅峰对决", "詹姆斯与库里再度交锋，湖人主场迎战勇士",
                    "洛杉矶湖人队将在斯台普斯中心迎战金州勇士队。两队本赛季状态火热，詹姆斯场均25.7分，库里场均26.4分。本场比赛将是西部季后赛卡位战的关键一战，双方必将全力以赴。",
                    "湖人", "勇士", null, null,
                    today.atTime(10, 30), today.atTime(12, 30)));
            newsList.add(news("🏆 凯尔特人冲击连胜纪录", "东部霸主凯尔特人力争十连胜",
                    "波士顿凯尔特人目前以64胜18负的战绩领跑全联盟，塔图姆和布朗双探花组合发挥出色。他们将在主场迎战雄鹿队，若能取胜将创造本赛季最长连胜纪录。",
                    "凯尔特人", "雄鹿", null, null,
                    today.atTime(13, 0), today.atTime(15, 0)));
            newsList.add(news("⚡ 掘金主场迎战太阳，西部强强对话", "约基奇对阵杜兰特，MVP级别的较量",
                    "丹佛掘金队将在主场迎战菲尼克斯太阳队。约基奇本赛季场均26.4分12.4篮板9.0助攻，几乎场均三双。杜兰特则场均27.1分，两位超级巨星的对决令人期待。",
                    "掘金", "太阳", null, null,
                    today.atTime(15, 30), today.atTime(17, 30)));
            newsList.add(news("📊 本周东西部排名分析", "常规赛收官阶段，各队冲刺季后赛",
                    "随着常规赛进入收官阶段，东西部的排名争夺愈发激烈。东部方面凯尔特人已锁定第一，雄鹿、骑士紧随其后。西部方面掘金、湖人、勇士的排名仍在变动，最后几场比赛将决定最终座次。",
                    "全联盟", "全联盟", null, null,
                    today.atTime(18, 0), today.atTime(20, 0)));
            newsList.add(news("🏀 湖人 112:108 勇士 — 加时险胜！", "詹姆斯砍下32分，湖人加时逆转勇士",
                    "在刚刚结束的焦点战中，洛杉矶湖人队通过加时赛以112比108险胜金州勇士队。勒布朗·詹姆斯全场贡献32分8篮板7助攻，安东尼·戴维斯也有28分14篮板的出色表现。库里虽然命中6记三分砍下30分，但未能帮助球队取胜。",
                    "湖人", "勇士", 112, 108,
                    today.atTime(6, 0), today.atTime(8, 0)));
            newsList.add(news("🎯 凯尔特人 120:105 雄鹿 — 豪取十连胜", "塔图姆35分率队大胜",
                    "凯尔特人延续火热状态，以120比105击败雄鹿，豪取十连胜。杰森·塔图姆砍下35分9篮板，杰伦·布朗也有26分进账。雄鹿方面字母哥虽然拿下31分，但独木难支。",
                    "凯尔特人", "雄鹿", 120, 105,
                    today.atTime(7, 0), today.atTime(9, 0)));
            gameNewsRepository.saveAll(newsList);
    }

    private static Team team(String name, String city, String conf, int w, int l) {
        Team t = new Team();
        t.setName(name);
        t.setCity(city);
        t.setConference(conf);
        t.setWins(w);
        t.setLosses(l);
        return t;
    }

    private static Player player(String name, Team team, String pos, double ppg, double rpg, double apg, double spg) {
        Player p = new Player();
        p.setName(name);
        p.setTeam(team);
        p.setPosition(pos);
        p.setPointsPerGame(ppg);
        p.setReboundsPerGame(rpg);
        p.setAssistsPerGame(apg);
        p.setStealsPerGame(spg);
        return p;
    }

    private static GameNews news(String title, String summary, String content,
                                  String homeTeam, String awayTeam,
                                  Integer homeScore, Integer awayScore,
                                  LocalDateTime gameStartTime, LocalDateTime gameEndTime) {
        GameNews g = new GameNews();
        g.setTitle(title);
        g.setSummary(summary);
        g.setContent(content);
        g.setHomeTeam(homeTeam);
        g.setAwayTeam(awayTeam);
        g.setHomeScore(homeScore);
        g.setAwayScore(awayScore);
        g.setGameStartTime(gameStartTime);
        g.setGameEndTime(gameEndTime);
        g.setCreateTime(LocalDateTime.now());
        return g;
    }
}
