package com.nbamanager.config;

import com.nbamanager.domain.GameNews;
import com.nbamanager.domain.MatchRecord;
import com.nbamanager.domain.Player;
import com.nbamanager.domain.Role;
import com.nbamanager.domain.Team;
import com.nbamanager.domain.UserAccount;
import com.nbamanager.repository.GameNewsRepository;
import com.nbamanager.repository.MatchRecordRepository;
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
    private final MatchRecordRepository matchRecordRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    @Bean
    @Transactional
    CommandLineRunner seedData() {
        return args -> {
            boolean hasUsers = userAccountRepository.count() > 0;
            if (hasUsers) {
                log.info("数据库中已有用户数据，跳过用户初始化。");
            } else {
                log.info("开始初始化用户数据...");
                seedUsers();
            }

            // 更新球队战绩数据（基于ESPN 2025-26赛季最新数据）
            log.info("开始更新NBA球队战绩数据...");
            updateTeamsFromESPN();

            // 更新球员数据（基于2025-26赛季数据）
            log.info("开始更新球员数据...");
            updatePlayersData();

            // 更新对战记录
            log.info("开始更新对战记录...");
            updateMatchRecords();

            // 赛事资讯独立初始化：只要 game_news 表为空就写入
            if (gameNewsRepository.count() == 0) {
                log.info("开始初始化赛事资讯...");
                seedNews();
                log.info("演示数据初始化完成！");
            }

            // 对战记录独立初始化：只要 match_records 表为空就写入
            if (matchRecordRepository.count() == 0) {
                log.info("开始初始化球队对战记录...");
                seedMatchRecords();
                log.info("对战记录初始化完成！");
            }

            // 补足已有球员的新增字段数据（DDL新增字段后为NULL）
            List<Player> allPlayers = playerRepository.findAll();
            boolean needsFieldFix = allPlayers.stream().anyMatch(p -> p.getGamesPlayed() == null);
            if (needsFieldFix) {
                log.info("补足球员扩展字段数据...");
                for (Player p : allPlayers) {
                    if (p.getStealsPerGame() == null) p.setStealsPerGame(1.0);
                    if (p.getGamesPlayed() == null) p.setGamesPlayed(65);
                    if (p.getMinutesPerGame() == null) p.setMinutesPerGame(28.0);
                    if (p.getFieldGoalPct() == null) p.setFieldGoalPct(0.460);
                    if (p.getThreePointPct() == null) p.setThreePointPct(0.350);
                    if (p.getFreeThrowPct() == null) p.setFreeThrowPct(0.800);
                    if (p.getBlocksPerGame() == null) p.setBlocksPerGame(0.5);
                    if (p.getTurnoversPerGame() == null) p.setTurnoversPerGame(1.5);
                    if (p.getEfficiency() == null) p.setEfficiency(15.0);
                    if (p.getTrueShootingPct() == null) p.setTrueShootingPct(0.570);
                    if (p.getUsagePct() == null) p.setUsagePct(20.0);
                    if (p.getJerseyNumber() == null) p.setJerseyNumber(0);
                    if (p.getHeight() == null) p.setHeight("6-6");
                    if (p.getWeight() == null) p.setWeight(210);
                    if (p.getCountry() == null) p.setCountry("美国");
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

    private void seedUsers() {
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
    }

    /**
     * 从ESPN获取的2025-26赛季NBA最新战绩数据更新球队。
     * 如果球队存在则更新战绩，如果不存在则添加新球队。
     */
    private void updateTeamsFromESPN() {
        // 2025-26赛季NBA球队数据（来源：ESPN API）
        List<TeamData> allTeams = List.of(
            // 东部联盟 (Eastern Conference)
            new TeamData("活塞", "底特律", "东部", 60, 22),
            new TeamData("老鹰", "亚特兰大", "东部", 46, 36),
            new TeamData("凯尔特人", "波士顿", "东部", 56, 26),
            new TeamData("76人", "费城", "东部", 45, 37),
            new TeamData("魔术", "奥兰多", "东部", 45, 37),
            new TeamData("猛龙", "多伦多", "东部", 46, 36),
            new TeamData("骑士", "克利夫兰", "东部", 52, 30),
            new TeamData("尼克斯", "纽约", "东部", 53, 29),
            new TeamData("热火", "迈阿密", "东部", 43, 39),
            new TeamData("黄蜂", "夏洛特", "东部", 44, 38),
            new TeamData("奇才", "华盛顿", "东部", 17, 65),
            new TeamData("步行者", "印第安纳", "东部", 19, 63),
            new TeamData("篮网", "布鲁克林", "东部", 20, 62),
            new TeamData("公牛", "芝加哥", "东部", 31, 51),
            new TeamData("雄鹿", "密尔沃基", "东部", 32, 50),
            // 西部联盟 (Western Conference)
            new TeamData("湖人", "洛杉矶", "西部", 53, 29),
            new TeamData("马刺", "圣安东尼奥", "西部", 62, 20),
            new TeamData("开拓者", "波特兰", "西部", 42, 40),
            new TeamData("太阳", "菲尼克斯", "西部", 45, 37),
            new TeamData("森林狼", "明尼苏达", "西部", 49, 33),
            new TeamData("火箭", "休斯顿", "西部", 52, 30),
            new TeamData("掘金", "丹佛", "西部", 54, 28),
            new TeamData("勇士", "金州", "西部", 37, 45),
            new TeamData("快船", "洛杉矶", "西部", 42, 40),
            new TeamData("国王", "萨克拉门托", "西部", 22, 60),
            new TeamData("爵士", "犹他", "西部", 22, 60),
            new TeamData("灰熊", "孟菲斯", "西部", 25, 57),
            new TeamData("鹈鹕", "新奥尔良", "西部", 26, 56),
            new TeamData("独行侠", "达拉斯", "西部", 26, 56),
            new TeamData("雷霆", "俄克拉荷马城", "西部", 64, 18)
        );

        int updatedCount = 0;
        int addedCount = 0;

        for (TeamData td : allTeams) {
            Team existingTeam = teamRepository.findByName(td.name);
            if (existingTeam != null) {
                // 球队存在，更新战绩
                existingTeam.setWins(td.wins);
                existingTeam.setLosses(td.losses);
                existingTeam.setCity(td.city);
                existingTeam.setConference(td.conference);
                teamRepository.save(existingTeam);
                updatedCount++;
                log.info("更新球队: {} ({}胜{}负)", td.name, td.wins, td.losses);
            } else {
                // 球队不存在，添加新球队
                Team newTeam = team(td.name, td.city, td.conference, td.wins, td.losses);
                teamRepository.save(newTeam);
                addedCount++;
                log.info("新增球队: {} - {} ({}胜{}负)", td.name, td.city, td.wins, td.losses);
            }
        }

        log.info("球队数据更新完成：更新{}支，新增{}支", updatedCount, addedCount);
    }

    /**
     * 更新球员数据（2025-26赛季主要球员）
     * 如果球员存在则更新数据，如果不存在则添加新球员
     */
    private void updatePlayersData() {
        List<PlayerData> allPlayers = List.of(
            // 湖人
            new PlayerData("卢卡·东契奇", "湖人", "控卫", 32.1, 7.1, 8.6, 1.7, 72, 36.5, 0.487, 0.372, 0.786, 0.4, 3.8, 28.5, 0.601, 33.2, 77, "6-7", 230, "斯洛文尼亚"),
            new PlayerData("勒布朗·詹姆斯", "湖人", "小前锋", 24.7, 6.8, 7.1, 1.1, 70, 34.2, 0.512, 0.365, 0.731, 0.6, 3.2, 23.8, 0.598, 29.5, 23, "6-9", 250, "美国"),
            new PlayerData("奥斯汀·里夫斯", "湖人", "分卫", 16.3, 4.2, 4.0, 0.9, 75, 32.1, 0.465, 0.385, 0.852, 0.3, 1.8, 15.2, 0.582, 20.1, 15, "6-5", 197, "美国"),
            new PlayerData("八村塁", "湖人", "大前锋", 14.5, 5.3, 1.2, 0.7, 68, 28.5, 0.495, 0.358, 0.782, 0.4, 1.2, 13.1, 0.575, 18.5, 28, "6-8", 230, "日本"),
            new PlayerData("道尔顿·克内克特", "湖人", "分卫", 9.2, 2.5, 0.9, 0.3, 65, 20.3, 0.442, 0.390, 0.815, 0.2, 0.8, 9.5, 0.562, 14.2, 4, "6-6", 215, "美国"),
            new PlayerData("克里斯蒂安·伍德", "湖人", "大前锋", 9.0, 4.6, 0.8, 0.5, 50, 18.5, 0.478, 0.342, 0.725, 0.6, 1.1, 10.2, 0.555, 16.8, 35, "6-10", 214, "美国"),
            new PlayerData("贾里德·范德比尔特", "湖人", "大前锋", 5.2, 7.1, 1.5, 1.4, 55, 22.8, 0.485, 0.285, 0.652, 0.5, 1.0, 9.8, 0.510, 10.5, 2, "6-9", 214, "美国"),
            new PlayerData("杰克森·海耶斯", "湖人", "中锋", 5.6, 4.3, 0.7, 0.5, 60, 16.2, 0.612, 0.200, 0.585, 1.1, 0.8, 10.5, 0.598, 12.3, 10, "6-11", 220, "美国"),
            new PlayerData("加布·文森特", "湖人", "控卫", 6.1, 1.6, 1.9, 0.8, 48, 19.5, 0.398, 0.345, 0.825, 0.1, 1.2, 7.2, 0.505, 13.5, 7, "6-3", 200, "尼日利亚"),
            // 勇士
            new PlayerData("斯蒂芬·库里", "勇士", "控卫", 26.6, 3.6, 4.4, 1.6, 74, 33.8, 0.472, 0.408, 0.910, 0.3, 3.1, 24.5, 0.628, 30.2, 30, "6-2", 185, "美国"),
            new PlayerData("吉米·巴特勒", "勇士", "小前锋", 20.0, 5.6, 4.9, 1.4, 60, 32.5, 0.495, 0.335, 0.858, 0.4, 2.0, 20.2, 0.592, 25.8, 22, "6-7", 230, "美国"),
            new PlayerData("布兰丁·波杰姆斯基", "勇士", "分卫", 16.0, 5.1, 4.0, 1.0, 72, 30.2, 0.458, 0.378, 0.825, 0.3, 1.8, 14.5, 0.568, 19.8, 2, "6-4", 205, "美国"),
            new PlayerData("乔纳森·库明加", "勇士", "大前锋", 12.1, 5.9, 2.5, 0.4, 65, 25.8, 0.502, 0.325, 0.695, 0.7, 1.5, 12.8, 0.558, 18.2, 00, "6-7", 225, "刚果"),
            new PlayerData("德安东尼·梅尔顿", "勇士", "分卫", 11.9, 2.8, 2.3, 0.5, 55, 22.5, 0.435, 0.368, 0.785, 0.3, 1.2, 10.5, 0.548, 15.5, 8, "6-3", 200, "美国"),
            new PlayerData("加里·佩顿二世", "勇士", "控卫", 8.9, 6.6, 2.3, 1.5, 62, 20.8, 0.558, 0.305, 0.652, 0.5, 0.9, 12.5, 0.572, 12.8, 0, "6-3", 195, "美国"),
            new PlayerData("巴迪·希尔德", "勇士", "分卫", 8.0, 2.5, 1.5, 0.8, 70, 18.2, 0.415, 0.388, 0.845, 0.2, 0.8, 8.2, 0.542, 14.5, 24, "6-4", 220, "巴哈马"),
            new PlayerData("昆顿·波斯特", "勇士", "中锋", 7.7, 4.0, 1.4, 0.4, 45, 15.8, 0.525, 0.310, 0.685, 0.9, 0.8, 9.5, 0.555, 13.2, 21, "7-0", 245, "美国"),
            new PlayerData("帕特·斯宾塞", "勇士", "控卫", 7.2, 2.4, 3.5, 0.7, 58, 18.5, 0.445, 0.352, 0.805, 0.1, 1.5, 8.8, 0.535, 14.8, 61, "6-3", 185, "美国"),
            new PlayerData("特雷斯·杰克逊-戴维斯", "勇士", "大前锋", 4.2, 3.1, 0.9, 0.4, 52, 12.5, 0.545, 0.250, 0.625, 0.8, 0.6, 8.2, 0.538, 10.5, 32, "6-9", 245, "美国"),
            new PlayerData("吉·桑托斯", "勇士", "小前锋", 4.0, 2.6, 1.8, 0.6, 48, 14.2, 0.425, 0.338, 0.755, 0.3, 0.9, 6.5, 0.515, 11.2, 15, "6-6", 185, "巴西"),
            // 凯尔特人
            new PlayerData("杰森·塔图姆", "凯尔特人", "小前锋", 26.5, 8.3, 5.1, 1.2, 74, 36.2, 0.475, 0.378, 0.832, 0.6, 2.8, 24.2, 0.598, 30.5, 0, "6-8", 210, "美国"),
            new PlayerData("杰伦·布朗", "凯尔特人", "分卫", 24.3, 6.1, 4.6, 1.3, 70, 34.5, 0.492, 0.362, 0.765, 0.5, 2.5, 20.8, 0.585, 28.2, 7, "6-6", 223, "美国"),
            new PlayerData("克里斯塔普斯·波尔津吉斯", "凯尔特人", "中锋", 17.8, 6.5, 2.3, 0.9, 55, 28.8, 0.518, 0.385, 0.825, 1.8, 1.8, 20.5, 0.612, 22.5, 8, "7-3", 240, "拉脱维亚"),
            new PlayerData("德里克·怀特", "凯尔特人", "控卫", 14.5, 4.2, 4.9, 1.1, 72, 32.8, 0.458, 0.395, 0.872, 0.8, 1.5, 15.2, 0.588, 18.5, 9, "6-4", 190, "美国"),
            new PlayerData("朱·霍勒迪", "凯尔特人", "控卫", 12.1, 4.5, 5.2, 0.8, 68, 30.5, 0.478, 0.368, 0.845, 0.5, 1.8, 14.8, 0.575, 16.2, 4, "6-4", 205, "美国"),
            new PlayerData("佩顿·普里查德", "凯尔特人", "控卫", 8.9, 2.8, 2.9, 0.5, 75, 22.5, 0.448, 0.405, 0.855, 0.1, 1.0, 10.2, 0.572, 15.8, 11, "6-1", 195, "美国"),
            new PlayerData("艾尔·霍福德", "凯尔特人", "中锋", 7.8, 5.4, 2.4, 0.6, 65, 24.2, 0.485, 0.365, 0.758, 0.9, 1.2, 11.5, 0.555, 11.2, 42, "6-9", 245, "多米尼加"),
            new PlayerData("萨姆·豪瑟", "凯尔特人", "小前锋", 7.2, 2.5, 1.1, 0.4, 60, 16.8, 0.455, 0.412, 0.805, 0.2, 0.5, 8.5, 0.575, 12.5, 30, "6-7", 215, "美国"),
            new PlayerData("卢克·科内特", "凯尔特人", "中锋", 4.9, 4.0, 0.9, 0.3, 50, 14.5, 0.625, 0.285, 0.652, 1.5, 0.5, 9.8, 0.585, 9.2, 40, "7-2", 250, "美国"),
            // 雄鹿
            new PlayerData("扬尼斯·阿德托昆博", "雄鹿", "大前锋", 30.4, 11.5, 6.5, 1.2, 72, 35.8, 0.558, 0.285, 0.658, 1.2, 3.5, 30.2, 0.605, 35.5, 34, "6-11", 243, "希腊"),
            new PlayerData("达米安·利拉德", "雄鹿", "控卫", 24.3, 4.4, 7.0, 1.0, 68, 34.2, 0.462, 0.385, 0.912, 0.3, 2.8, 22.5, 0.598, 28.8, 0, "6-2", 195, "美国"),
            new PlayerData("克里斯·米德尔顿", "雄鹿", "小前锋", 15.1, 4.7, 5.3, 0.8, 55, 28.5, 0.475, 0.368, 0.885, 0.3, 2.2, 15.8, 0.572, 20.5, 22, "6-7", 222, "美国"),
            // 掘金
            new PlayerData("尼古拉·约基奇", "掘金", "中锋", 26.4, 12.4, 9.0, 1.4, 76, 34.5, 0.568, 0.358, 0.822, 0.9, 3.2, 32.5, 0.638, 31.2, 15, "6-11", 284, "塞尔维亚"),
            new PlayerData("贾马尔·穆雷", "掘金", "控卫", 21.2, 4.1, 6.5, 0.9, 65, 32.8, 0.482, 0.395, 0.858, 0.3, 2.5, 19.2, 0.585, 25.5, 27, "6-4", 215, "加拿大"),
            new PlayerData("迈克尔·波特", "掘金", "小前锋", 17.5, 5.8, 1.5, 0.7, 62, 28.5, 0.505, 0.408, 0.795, 0.6, 1.2, 15.8, 0.598, 20.2, 1, "6-10", 218, "美国"),
            // 太阳
            new PlayerData("凯文·杜兰特", "太阳", "小前锋", 27.1, 6.6, 5.0, 0.8, 65, 35.2, 0.528, 0.412, 0.875, 1.2, 2.8, 25.5, 0.632, 30.8, 35, "6-10", 240, "美国"),
            new PlayerData("德文·布克", "太阳", "分卫", 27.1, 4.5, 6.9, 0.9, 68, 34.8, 0.492, 0.375, 0.882, 0.4, 3.0, 23.8, 0.605, 31.2, 1, "6-5", 206, "美国"),
            new PlayerData("布拉德利·比尔", "太阳", "控卫", 18.5, 4.0, 4.5, 0.8, 55, 30.5, 0.472, 0.358, 0.825, 0.3, 2.2, 16.2, 0.572, 22.8, 3, "6-5", 207, "美国"),
            // 雷霆
            new PlayerData("谢伊·吉尔杰斯-亚历山大", "雷霆", "控卫", 30.1, 5.5, 6.2, 2.0, 75, 35.8, 0.512, 0.358, 0.885, 0.8, 2.5, 28.8, 0.628, 32.5, 2, "6-6", 220, "加拿大"),
            new PlayerData("切特·霍姆格伦", "雷霆", "中锋", 16.5, 7.8, 2.4, 0.8, 68, 29.5, 0.518, 0.362, 0.795, 2.5, 1.8, 19.2, 0.595, 20.8, 7, "7-0", 219, "美国"),
            new PlayerData("杰伦·威廉姆斯", "雷霆", "小前锋", 21.0, 5.5, 5.0, 1.2, 72, 33.2, 0.498, 0.372, 0.845, 0.5, 2.0, 19.5, 0.592, 24.5, 8, "6-6", 210, "美国"),
            // 马刺
            new PlayerData("维克托·文班亚马", "马刺", "中锋", 24.5, 10.8, 3.9, 1.2, 70, 33.5, 0.478, 0.345, 0.758, 3.9, 2.8, 28.2, 0.585, 28.5, 1, "7-4", 230, "法国"),
            new PlayerData("德文·瓦塞尔", "马刺", "分卫", 19.5, 3.8, 4.1, 1.1, 68, 31.2, 0.465, 0.382, 0.835, 0.3, 1.8, 16.8, 0.575, 22.5, 24, "6-5", 194, "美国"),
            // 火箭
            new PlayerData("阿尔佩伦·申京", "火箭", "中锋", 21.2, 9.3, 5.0, 1.2, 72, 32.5, 0.528, 0.305, 0.725, 1.0, 2.8, 21.5, 0.575, 25.8, 28, "6-11", 243, "土耳其"),
            new PlayerData("杰伦·格林", "火箭", "分卫", 22.5, 4.8, 3.5, 0.8, 70, 33.8, 0.445, 0.358, 0.805, 0.3, 2.5, 16.8, 0.558, 25.2, 4, "6-4", 186, "美国"),
            // 尼克斯
            new PlayerData("杰伦·布伦森", "尼克斯", "控卫", 28.7, 3.5, 6.7, 0.9, 76, 35.5, 0.498, 0.385, 0.852, 0.2, 2.2, 24.5, 0.605, 29.8, 11, "6-2", 190, "美国"),
            new PlayerData("朱利叶斯·兰德尔", "尼克斯", "大前锋", 24.0, 9.2, 5.0, 0.8, 72, 34.2, 0.482, 0.345, 0.768, 0.4, 2.8, 20.5, 0.572, 27.5, 30, "6-8", 250, "美国"),
            // 骑士
            new PlayerData("多诺万·米切尔", "骑士", "分卫", 26.6, 4.3, 6.1, 1.8, 74, 35.2, 0.485, 0.378, 0.858, 0.3, 2.5, 23.5, 0.598, 30.2, 45, "6-3", 215, "美国"),
            new PlayerData("达里厄斯·加兰", "骑士", "控卫", 21.3, 2.7, 7.8, 1.2, 68, 33.5, 0.465, 0.382, 0.872, 0.1, 2.8, 18.8, 0.578, 24.5, 10, "6-1", 192, "美国"),
            // 森林狼
            new PlayerData("安东尼·爱德华兹", "森林狼", "分卫", 25.9, 5.4, 5.1, 1.3, 76, 35.8, 0.472, 0.365, 0.812, 0.5, 2.8, 22.2, 0.578, 28.8, 5, "6-4", 225, "美国"),
            new PlayerData("卡尔-安东尼·唐斯", "森林狼", "大前锋", 21.8, 8.3, 3.0, 0.7, 70, 32.5, 0.518, 0.398, 0.852, 0.8, 2.2, 20.5, 0.605, 24.2, 32, "6-11", 248, "多米尼加"),
            // 活塞
            new PlayerData("凯德·坎宁安", "活塞", "控卫", 24.0, 6.2, 9.3, 1.1, 72, 35.2, 0.458, 0.345, 0.842, 0.4, 3.5, 21.2, 0.562, 28.5, 2, "6-6", 220, "美国"),
            new PlayerData("杰登·艾维", "活塞", "分卫", 18.5, 3.8, 4.2, 0.9, 68, 30.8, 0.442, 0.338, 0.785, 0.3, 2.2, 14.5, 0.545, 22.5, 23, "6-4", 195, "美国"),
            // 独行侠
            new PlayerData("凯里·欧文", "独行侠", "分卫", 25.6, 5.2, 5.2, 1.1, 65, 33.5, 0.495, 0.402, 0.895, 0.4, 2.2, 22.8, 0.612, 28.2, 11, "6-2", 195, "美国"),
            // 76人
            new PlayerData("乔尔·恩比德", "76人", "中锋", 27.9, 11.2, 5.5, 1.2, 55, 33.2, 0.502, 0.345, 0.858, 1.7, 3.5, 28.5, 0.618, 33.8, 21, "7-0", 280, "喀麦隆"),
            new PlayerData("泰瑞斯·马克西", "76人", "控卫", 25.9, 3.7, 6.2, 1.0, 74, 35.5, 0.475, 0.372, 0.862, 0.3, 2.2, 21.5, 0.588, 27.2, 0, "6-2", 200, "美国"),
            // 老鹰
            new PlayerData("特雷·杨", "老鹰", "控卫", 26.4, 3.0, 11.4, 1.0, 72, 34.8, 0.448, 0.362, 0.885, 0.2, 4.2, 22.8, 0.575, 30.5, 11, "6-1", 164, "美国"),
            new PlayerData("德章泰·穆雷", "老鹰", "分卫", 22.5, 5.3, 6.4, 1.4, 68, 33.5, 0.472, 0.348, 0.815, 0.3, 2.5, 19.2, 0.562, 25.8, 5, "6-5", 180, "美国"),
            // 猛龙
            new PlayerData("帕斯卡尔·西亚卡姆", "猛龙", "大前锋", 22.2, 6.8, 4.8, 0.9, 65, 33.8, 0.485, 0.342, 0.768, 0.5, 2.2, 18.5, 0.568, 25.2, 43, "6-9", 230, "喀麦隆"),
            new PlayerData("斯科蒂·巴恩斯", "猛龙", "小前锋", 19.8, 8.2, 6.0, 1.2, 72, 34.5, 0.468, 0.328, 0.762, 0.8, 2.8, 18.2, 0.552, 24.5, 4, "6-7", 225, "美国"),
            // 热火
            new PlayerData("巴姆·阿德巴约", "热火", "中锋", 20.4, 10.2, 4.8, 1.0, 72, 33.5, 0.535, 0.285, 0.782, 0.9, 2.5, 20.8, 0.585, 22.8, 13, "6-9", 255, "美国"),
            // 快船
            new PlayerData("科怀·伦纳德", "快船", "小前锋", 23.8, 6.5, 3.6, 1.6, 52, 32.5, 0.512, 0.398, 0.872, 0.6, 1.8, 22.5, 0.608, 26.5, 2, "6-7", 225, "美国"),
            new PlayerData("詹姆斯·哈登", "快船", "控卫", 16.6, 5.1, 8.5, 1.1, 65, 31.8, 0.438, 0.352, 0.865, 0.5, 3.2, 18.2, 0.565, 22.5, 1, "6-5", 220, "美国"),
            // 国王
            new PlayerData("达龙·福克斯", "国王", "控卫", 26.2, 4.6, 5.8, 2.0, 72, 34.5, 0.478, 0.345, 0.795, 0.3, 2.8, 21.5, 0.572, 28.2, 5, "6-3", 185, "美国"),
            new PlayerData("多曼塔斯·萨博尼斯", "国王", "中锋", 20.2, 14.0, 6.2, 0.8, 76, 34.2, 0.582, 0.295, 0.745, 0.6, 2.8, 23.5, 0.602, 22.5, 10, "6-11", 240, "立陶宛"),
            // 鹈鹕
            new PlayerData("锡安·威廉姆森", "鹈鹕", "大前锋", 22.9, 5.8, 5.0, 1.1, 55, 30.5, 0.585, 0.285, 0.685, 0.7, 2.5, 22.8, 0.602, 28.2, 1, "6-6", 284, "美国"),
            new PlayerData("布兰登·英格拉姆", "鹈鹕", "小前锋", 22.2, 5.6, 5.2, 0.8, 62, 33.2, 0.482, 0.358, 0.825, 0.5, 2.5, 18.5, 0.572, 25.5, 14, "6-8", 190, "美国"),
            // 魔术
            new PlayerData("保罗·班切罗", "魔术", "大前锋", 22.6, 6.9, 5.4, 1.0, 72, 34.5, 0.468, 0.335, 0.762, 0.6, 2.8, 19.2, 0.558, 26.8, 5, "6-10", 250, "美国"),
            new PlayerData("弗朗茨·瓦格纳", "魔术", "小前锋", 24.4, 5.7, 5.7, 1.4, 72, 34.8, 0.482, 0.355, 0.825, 0.4, 2.2, 20.5, 0.578, 27.2, 22, "6-10", 220, "德国"),
            // 步行者
            new PlayerData("泰瑞斯·哈利伯顿", "步行者", "控卫", 20.1, 3.7, 10.8, 1.2, 68, 33.5, 0.472, 0.388, 0.852, 0.4, 2.5, 22.2, 0.592, 22.8, 0, "6-5", 185, "美国"),
            new PlayerData("帕斯卡尔·西亚卡姆", "步行者", "大前锋", 21.5, 7.5, 3.5, 0.8, 70, 32.8, 0.492, 0.348, 0.775, 0.5, 1.8, 18.2, 0.568, 24.5, 43, "6-9", 230, "喀麦隆"),
            // 公牛
            new PlayerData("扎克·拉文", "公牛", "分卫", 24.8, 4.5, 4.2, 0.9, 58, 33.5, 0.472, 0.372, 0.845, 0.3, 2.2, 19.5, 0.578, 26.5, 8, "6-5", 200, "美国"),
            new PlayerData("德玛尔·德罗赞", "公牛", "小前锋", 24.0, 4.5, 5.1, 1.0, 68, 34.2, 0.498, 0.325, 0.872, 0.4, 1.8, 20.2, 0.585, 25.8, 10, "6-6", 220, "美国"),
            // 灰熊
            new PlayerData("贾·莫兰特", "灰熊", "控卫", 25.1, 5.6, 8.1, 1.0, 60, 32.5, 0.468, 0.328, 0.782, 0.4, 3.5, 21.5, 0.562, 29.2, 12, "6-2", 174, "美国"),
            new PlayerData("德斯蒙德·贝恩", "灰熊", "分卫", 24.8, 4.4, 4.8, 1.0, 65, 33.2, 0.482, 0.392, 0.858, 0.3, 2.0, 19.8, 0.585, 25.5, 22, "6-5", 215, "美国"),
            // 开拓者
            new PlayerData("安芬尼·西蒙斯", "开拓者", "控卫", 22.6, 3.0, 5.5, 0.8, 65, 32.8, 0.448, 0.372, 0.882, 0.2, 2.2, 17.2, 0.562, 25.2, 1, "6-3", 181, "美国"),
            new PlayerData("杰拉米·格兰特", "开拓者", "大前锋", 21.0, 3.5, 2.8, 0.8, 60, 31.5, 0.458, 0.352, 0.815, 0.6, 1.8, 15.5, 0.558, 22.5, 9, "6-8", 210, "美国"),
            // 爵士
            new PlayerData("劳里·马尔卡宁", "爵士", "大前锋", 23.2, 8.2, 2.0, 0.9, 68, 33.2, 0.482, 0.388, 0.852, 0.6, 1.8, 19.5, 0.585, 25.2, 23, "7-0", 240, "芬兰"),
            new PlayerData("科林·塞克斯顿", "爵士", "控卫", 18.5, 2.8, 4.2, 0.7, 65, 28.5, 0.468, 0.355, 0.845, 0.2, 2.2, 14.8, 0.562, 22.5, 2, "6-1", 190, "美国"),
            // 篮网
            new PlayerData("米卡尔·布里奇斯", "篮网", "小前锋", 19.6, 4.5, 3.6, 1.0, 75, 34.2, 0.455, 0.362, 0.825, 0.4, 1.5, 15.2, 0.558, 21.5, 1, "6-6", 209, "美国"),
            new PlayerData("卡梅隆·托马斯", "篮网", "分卫", 22.5, 3.2, 3.0, 0.7, 68, 30.5, 0.442, 0.348, 0.858, 0.2, 2.2, 15.8, 0.555, 26.2, 24, "6-4", 210, "美国"),
            // 黄蜂
            new PlayerData("拉梅洛·鲍尔", "黄蜂", "控卫", 23.5, 5.8, 8.0, 1.5, 58, 32.8, 0.445, 0.358, 0.825, 0.3, 3.5, 19.8, 0.555, 27.5, 1, "6-7", 190, "美国"),
            new PlayerData("迈尔斯·布里奇斯", "黄蜂", "大前锋", 21.2, 7.5, 3.2, 0.8, 65, 32.5, 0.482, 0.345, 0.785, 0.6, 1.8, 17.5, 0.562, 23.8, 0, "6-7", 225, "美国"),
            // 奇才
            new PlayerData("凯尔·库兹马", "奇才", "大前锋", 22.2, 6.6, 3.7, 0.8, 65, 33.2, 0.458, 0.342, 0.785, 0.5, 2.2, 16.5, 0.552, 24.5, 33, "6-9", 220, "美国"),
            new PlayerData("乔丹·普尔", "奇才", "分卫", 17.8, 2.7, 4.4, 0.8, 68, 30.5, 0.425, 0.345, 0.862, 0.2, 2.8, 13.5, 0.545, 22.8, 13, "6-4", 196, "美国"),
            // 雄鹿 (补充)
            new PlayerData("布鲁克·洛佩兹", "雄鹿", "中锋", 12.5, 5.2, 1.5, 0.5, 72, 26.5, 0.508, 0.365, 0.785, 2.4, 1.2, 14.2, 0.572, 16.5, 11, "7-0", 282, "美国")
        );

        int updatedCount = 0;
        int addedCount = 0;

        for (PlayerData pd : allPlayers) {
            Team team = teamRepository.findByName(pd.teamName);
            if (team == null) {
                log.warn("球队不存在: {}，跳过球员: {}", pd.teamName, pd.name);
                continue;
            }

            // 查找现有球员（按名字和球队）
            Player existingPlayer = findPlayerByNameAndTeam(pd.name, team);
            if (existingPlayer != null) {
                // 更新球员数据
                existingPlayer.setPosition(pd.position);
                existingPlayer.setPointsPerGame(pd.ppg);
                existingPlayer.setReboundsPerGame(pd.rpg);
                existingPlayer.setAssistsPerGame(pd.apg);
                existingPlayer.setStealsPerGame(pd.spg);
                existingPlayer.setGamesPlayed(pd.gp);
                existingPlayer.setMinutesPerGame(pd.mpg);
                existingPlayer.setFieldGoalPct(pd.fgPct);
                existingPlayer.setThreePointPct(pd.threePct);
                existingPlayer.setFreeThrowPct(pd.ftPct);
                existingPlayer.setBlocksPerGame(pd.bpg);
                existingPlayer.setTurnoversPerGame(pd.tpg);
                existingPlayer.setEfficiency(pd.per);
                existingPlayer.setTrueShootingPct(pd.tsPct);
                existingPlayer.setUsagePct(pd.usgPct);
                existingPlayer.setJerseyNumber(pd.jersey);
                existingPlayer.setHeight(pd.height);
                existingPlayer.setWeight(pd.weight);
                existingPlayer.setCountry(pd.country);
                playerRepository.save(existingPlayer);
                updatedCount++;
            } else {
                // 添加新球员
                Player newPlayer = player(pd.name, team, pd.position, pd.ppg, pd.rpg, pd.apg, pd.spg,
                        pd.gp, pd.mpg, pd.fgPct, pd.threePct, pd.ftPct,
                        pd.bpg, pd.tpg, pd.per, pd.tsPct, pd.usgPct,
                        pd.jersey, pd.height, pd.weight, pd.country);
                playerRepository.save(newPlayer);
                addedCount++;
            }
        }

        log.info("球员数据更新完成：更新{}名，新增{}名", updatedCount, addedCount);
    }

    /**
     * 根据名字和球队查找球员
     */
    private Player findPlayerByNameAndTeam(String name, Team team) {
        return playerRepository.findAll().stream()
                .filter(p -> p.getName().equals(name) && p.getTeam().getId().equals(team.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 更新对战记录（2025-26赛季）
     */
    private void updateMatchRecords() {
        if (matchRecordRepository.count() > 0) {
            log.info("数据库中已有对战记录，跳过初始化。");
            return;
        }

        LocalDate base = LocalDate.now();
        List<MatchRecord> records = new ArrayList<>();

        // === 西部强强对话 ===
        // 雷霆 vs 马刺
        records.add(match("雷霆", "马刺", 118, 112, base.minusDays(60), "2025-26", "FINISHED"));
        records.add(match("马刺", "雷霆", 105, 110, base.minusDays(45), "2025-26", "FINISHED"));
        records.add(match("雷霆", "马刺", 125, 108, base.minusDays(10), "2025-26", "FINISHED"));

        // 雷霆 vs 掘金
        records.add(match("雷霆", "掘金", 122, 118, base.minusDays(55), "2025-26", "FINISHED"));
        records.add(match("掘金", "雷霆", 115, 108, base.minusDays(35), "2025-26", "FINISHED"));

        // 雷霆 vs 湖人
        records.add(match("雷霆", "湖人", 130, 120, base.minusDays(50), "2025-26", "FINISHED"));
        records.add(match("湖人", "雷霆", 112, 125, base.minusDays(20), "2025-26", "FINISHED"));

        // 马刺 vs 火箭
        records.add(match("马刺", "火箭", 115, 105, base.minusDays(58), "2025-26", "FINISHED"));
        records.add(match("火箭", "马刺", 110, 118, base.minusDays(28), "2025-26", "FINISHED"));

        // 湖人 vs 勇士 (经典对决)
        records.add(match("湖人", "勇士", 112, 108, base.minusDays(55), "2025-26", "FINISHED"));
        records.add(match("勇士", "湖人", 105, 98, base.minusDays(40), "2025-26", "FINISHED"));
        records.add(match("湖人", "勇士", 118, 110, base.minusDays(15), "2025-26", "FINISHED"));

        // 湖人 vs 掘金
        records.add(match("掘金", "湖人", 118, 105, base.minusDays(48), "2025-26", "FINISHED"));
        records.add(match("湖人", "掘金", 110, 107, base.minusDays(18), "2025-26", "FINISHED"));

        // 湖人 vs 快船 (洛城德比)
        records.add(match("湖人", "快船", 115, 108, base.minusDays(52), "2025-26", "FINISHED"));
        records.add(match("快船", "湖人", 112, 120, base.minusDays(22), "2025-26", "FINISHED"));

        // 掘金 vs 独行侠
        records.add(match("掘金", "独行侠", 128, 125, base.minusDays(42), "2025-26", "FINISHED"));
        records.add(match("独行侠", "掘金", 120, 115, base.minusDays(12), "2025-26", "FINISHED"));

        // 勇士 vs 国王
        records.add(match("勇士", "国王", 115, 110, base.minusDays(38), "2025-26", "FINISHED"));
        records.add(match("国王", "勇士", 108, 112, base.minusDays(8), "2025-26", "FINISHED"));

        // 森林狼 vs 掘金
        records.add(match("森林狼", "掘金", 112, 108, base.minusDays(46), "2025-26", "FINISHED"));
        records.add(match("掘金", "森林狼", 125, 118, base.minusDays(16), "2025-26", "FINISHED"));

        // === 东部强强对话 ===
        // 凯尔特人 vs 尼克斯
        records.add(match("凯尔特人", "尼克斯", 120, 108, base.minusDays(56), "2025-26", "FINISHED"));
        records.add(match("尼克斯", "凯尔特人", 115, 118, base.minusDays(36), "2025-26", "FINISHED"));

        // 凯尔特人 vs 骑士
        records.add(match("凯尔特人", "骑士", 125, 118, base.minusDays(44), "2025-26", "FINISHED"));
        records.add(match("骑士", "凯尔特人", 110, 115, base.minusDays(14), "2025-26", "FINISHED"));

        // 活塞 vs 骑士
        records.add(match("活塞", "骑士", 118, 112, base.minusDays(54), "2025-26", "FINISHED"));
        records.add(match("骑士", "活塞", 108, 115, base.minusDays(24), "2025-26", "FINISHED"));

        // 活塞 vs 尼克斯
        records.add(match("活塞", "尼克斯", 122, 118, base.minusDays(42), "2025-26", "FINISHED"));
        records.add(match("尼克斯", "活塞", 125, 120, base.minusDays(6), "2025-26", "FINISHED"));

        // 76人 vs 尼克斯
        records.add(match("76人", "尼克斯", 115, 110, base.minusDays(38), "2025-26", "FINISHED"));
        records.add(match("尼克斯", "76人", 120, 108, base.minusDays(18), "2025-26", "FINISHED"));

        // 雄鹿 vs 76人
        records.add(match("雄鹿", "76人", 128, 122, base.minusDays(52), "2025-26", "FINISHED"));
        records.add(match("76人", "雄鹿", 115, 110, base.minusDays(22), "2025-26", "FINISHED"));

        // 老鹰 vs 热火
        records.add(match("老鹰", "热火", 118, 112, base.minusDays(48), "2025-26", "FINISHED"));
        records.add(match("热火", "老鹰", 105, 110, base.minusDays(28), "2025-26", "FINISHED"));

        // 魔术 vs 热火
        records.add(match("魔术", "热火", 112, 108, base.minusDays(35), "2025-26", "FINISHED"));
        records.add(match("热火", "魔术", 120, 115, base.minusDays(5), "2025-26", "FINISHED"));

        // 猛龙 vs 尼克斯
        records.add(match("猛龙", "尼克斯", 108, 115, base.minusDays(42), "2025-26", "FINISHED"));
        records.add(match("尼克斯", "猛龙", 122, 118, base.minusDays(12), "2025-26", "FINISHED"));

        // === 跨联盟焦点战 ===
        // 凯尔特人 vs 雷霆
        records.add(match("凯尔特人", "雷霆", 115, 118, base.minusDays(50), "2025-26", "FINISHED"));
        records.add(match("雷霆", "凯尔特人", 125, 120, base.minusDays(20), "2025-26", "FINISHED"));

        // 湖人 vs 凯尔特人 (黄绿大战)
        records.add(match("凯尔特人", "湖人", 121, 115, base.minusDays(45), "2025-26", "FINISHED"));
        records.add(match("湖人", "凯尔特人", 108, 104, base.minusDays(25), "2025-26", "FINISHED"));

        // 雄鹿 vs 湖人
        records.add(match("雄鹿", "湖人", 115, 110, base.minusDays(38), "2025-26", "FINISHED"));
        records.add(match("湖人", "雄鹿", 120, 112, base.minusDays(8), "2025-26", "FINISHED"));

        // 掘金 vs 凯尔特人
        records.add(match("掘金", "凯尔特人", 112, 108, base.minusDays(42), "2025-26", "FINISHED"));
        records.add(match("凯尔特人", "掘金", 118, 110, base.minusDays(12), "2025-26", "FINISHED"));

        matchRecordRepository.saveAll(records);
        log.info("对战记录更新完成：共添加{}场比赛", records.size());
    }

    /**
     * 内部类，用于存储球员数据
     */
    private static class PlayerData {
        final String name;
        final String teamName;
        final String position;
        final double ppg;
        final double rpg;
        final double apg;
        final double spg;
        final int gp;
        final double mpg;
        final double fgPct;
        final double threePct;
        final double ftPct;
        final double bpg;
        final double tpg;
        final double per;
        final double tsPct;
        final double usgPct;
        final int jersey;
        final String height;
        final int weight;
        final String country;

        PlayerData(String name, String teamName, String position, double ppg, double rpg, double apg, double spg,
                   int gp, double mpg, double fgPct, double threePct, double ftPct,
                   double bpg, double tpg, double per, double tsPct, double usgPct,
                   int jersey, String height, int weight, String country) {
            this.name = name;
            this.teamName = teamName;
            this.position = position;
            this.ppg = ppg;
            this.rpg = rpg;
            this.apg = apg;
            this.spg = spg;
            this.gp = gp;
            this.mpg = mpg;
            this.fgPct = fgPct;
            this.threePct = threePct;
            this.ftPct = ftPct;
            this.bpg = bpg;
            this.tpg = tpg;
            this.per = per;
            this.tsPct = tsPct;
            this.usgPct = usgPct;
            this.jersey = jersey;
            this.height = height;
            this.weight = weight;
            this.country = country;
        }
    }

    /**
     * 内部类，用于存储球队数据
     */
    private static class TeamData {
        final String name;
        final String city;
        final String conference;
        final int wins;
        final int losses;

        TeamData(String name, String city, String conference, int wins, int losses) {
            this.name = name;
            this.city = city;
            this.conference = conference;
            this.wins = wins;
            this.losses = losses;
        }
    }

    private void seedNews() {
            LocalDate today = LocalDate.now();
            LocalDate yesterday = today.minusDays(1);
            LocalDate twoDaysAgo = today.minusDays(2);
            LocalDate threeDaysAgo = today.minusDays(3);
            List<GameNews> newsList = new ArrayList<>();

            // ========== 今日焦点 ==========

            // 雷霆 vs 马刺 G7 赛前分析
            newsList.add(news("🔥 西部决赛G7赛前分析：雷霆VS马刺，谁能挺进总决赛？",
                    "文班亚马VS亚历山大，新生代巨星的终极对决",
                    "【赛前分析】NBA西部决赛将迎来抢七大战！俄克拉荷马城雷霆队将在主场迎战圣安东尼奥马刺队。系列赛战成3比3平，今晚的胜者将与纽约尼克斯队会师总决赛。\n\n" +
                    "【核心对决】维克托·文班亚马 vs 谢伊·吉尔杰斯-亚历山大。文班亚马本轮系列赛场均28.5分11.2篮板4.1盖帽，攻防两端统治力惊人。亚历山大则场均35.2分7.8助攻，关键时刻得分能力联盟顶级。\n\n" +
                    "【雷霆优势】主场作战，本赛季主场战绩42胜8负。杰伦·威廉姆斯和切特·霍姆格伦的发挥将是关键。雷霆需要限制文班亚马的内线统治力。\n\n" +
                    "【马刺优势】文班亚马的无解单打，德文·瓦塞尔的稳定输出。马刺团队防守出色，场均失分仅104.3分。波波维奇的战术调整能力是马刺的X因素。\n\n" +
                    "【关键数据】\n" +
                    "• 雷霆主场场均得分：118.5分\n" +
                    "• 马刺客场场均得分：112.8分\n" +
                    "• 系列赛三分命中率：雷霆38.2%，马刺35.6%\n" +
                    "• 系列赛篮板球：马刺场均多抢4.2个\n\n" +
                    "【预测】本场比赛预计将是一场防守大战。雷霆拥有主场优势，但马刺的内线统治力更强。预计比分将在105-100左右，胜负可能在最后2分钟决出。",
                    "雷霆", "马刺", null, null,
                    today.atTime(8, 0), today.atTime(10, 30)));

            // ========== 已结束的比赛 ==========

            // 马刺 vs 雷霆 (西部决赛)
            newsList.add(news("🏀 马刺 118:91 雷霆 — 文班亚马封神之战",
                    "维克托·文班亚马砍下38分12篮板6盖帽，马刺大胜雷霆",
                    "在西部决赛第三场中，圣安东尼奥马刺队主场以118比91大胜俄克拉荷马城雷霆队。维克托·文班亚马全场贡献38分12篮板6盖帽的豪华数据，彻底统治了攻防两端。德文·瓦塞尔也有22分进账。雷霆方面，谢伊·吉尔杰斯-亚历山大虽然拿下28分，但球队整体手感冰凉，三分球命中率仅为22%。",
                    "马刺", "雷霆", 118, 91,
                    twoDaysAgo.atTime(9, 0), twoDaysAgo.atTime(11, 30)));

            // 雷霆 vs 马刺 (西部决赛)
            newsList.add(news("⚡ 雷霆 127:114 马刺 — 亚历山大率队扳平比分",
                    "SGA狂砍42分，雷霆在主场扳回一城",
                    "西部决赛第二场，俄克拉荷马城雷霆队在主场以127比114击败圣安东尼奥马刺队，将系列赛比分扳平。谢伊·吉尔杰斯-亚历山大全场砍下42分8助攻，杰伦·威廉姆斯贡献25分。马刺方面文班亚马得到30分，但球队在第四节崩盘。",
                    "雷霆", "马刺", 127, 114,
                    threeDaysAgo.atTime(9, 0), threeDaysAgo.atTime(11, 30)));

            // 尼克斯 vs 骑士 (东部决赛)
            newsList.add(news("🗽 尼克斯 130:93 骑士 — 纽约时隔25年重返总决赛",
                    "布伦森35分12助攻，尼克斯横扫骑士晋级",
                    "东部决赛第四场，纽约尼克斯队在客场以130比93狂胜克利夫兰骑士队，以4比0的总比分横扫对手，时隔25年重返NBA总决赛。杰伦·布伦森全场贡献35分12助攻，朱利叶斯·兰德尔也有28分10篮板的两双表现。骑士方面多诺万·米切尔得到22分，但球队早早缴械投降。",
                    "骑士", "尼克斯", 93, 130,
                    yesterday.atTime(9, 0), yesterday.atTime(11, 30)));

            // 湖人 vs 勇士
            newsList.add(news("💛 湖人 115:108 勇士 — 东契奇詹姆斯合砍60分",
                    "卢卡·东契奇首次洛城德比表现出色",
                    "洛杉矶湖人队在主场以115比108击败金州勇士队。卢卡·东契奇在加盟湖人后的首次洛城德比中表现出色，贡献32分8篮板10助攻的准三双数据。勒布朗·詹姆斯也有28分进账。勇士方面斯蒂芬·库里砍下30分，吉米·巴特勒得到22分，但球队在关键时刻未能把握机会。",
                    "湖人", "勇士", 115, 108,
                    twoDaysAgo.atTime(11, 30), twoDaysAgo.atTime(14, 0)));

            // 凯尔特人 vs 雄鹿
            newsList.add(news("☘️ 凯尔特人 118:105 雄鹿 — 绿军展现冠军底蕴",
                    "塔图姆布朗双探花合砍52分",
                    "波士顿凯尔特人队在主场以118比105击败密尔沃基雄鹿队。杰森·塔图姆贡献28分9篮板，杰伦·布朗砍下24分，双探花组合再次证明了自己的实力。克里斯塔普斯·波尔津吉斯也有18分7篮板进账。雄鹿方面扬尼斯·阿德托昆博得到32分，但球队其他球员表现平平。",
                    "凯尔特人", "雄鹿", 118, 105,
                    threeDaysAgo.atTime(8, 0), threeDaysAgo.atTime(10, 30)));

            // 掘金 vs 太阳
            newsList.add(news("🏔️ 掘金 125:110 太阳 — 约基奇砍下三双",
                    "约基奇28分14篮板12助攻，掘金轻取太阳",
                    "丹佛掘金队在主场以125比110击败菲尼克斯太阳队。尼古拉·约基奇全场贡献28分14篮板12助攻的三双数据，贾马尔·穆雷也有25分进账。太阳方面凯文·杜兰特得到30分，德文·布克贡献22分，但球队防守端漏洞百出。",
                    "掘金", "太阳", 125, 110,
                    yesterday.atTime(11, 0), yesterday.atTime(13, 30)));

            // ========== 赛事预告 ==========

            // 马刺 vs 雷霆 (西部决赛继续)
            newsList.add(news("🔥 西部决赛天王山之战！马刺 VS 雷霆",
                    "系列赛2比2平，第五场将决定走势",
                    "NBA西部决赛将迎来天王山之战！圣安东尼奥马刺队将在主场迎战俄克拉荷马城雷霆队。目前系列赛战成2比2平，本场比赛的胜者将占据晋级的主动权。维克托·文班亚马与谢伊·吉尔杰斯-亚历山大的MVP级对决将是最大看点。",
                    "马刺", "雷霆", null, null,
                    today.plusDays(1).atTime(9, 0), today.plusDays(1).atTime(11, 30)));

            // 总决赛预告
            newsList.add(news("🏆 NBA总决赛预告：尼克斯 VS 西部冠军",
                    "纽约时隔25年重返总决赛，期待创造历史",
                    "纽约尼克斯队已经锁定东部冠军，时隔25年重返NBA总决赛。无论西部冠军是马刺还是雷霆，总决赛都将是一场火星撞地球的对决。布伦森能否带领尼克斯创造历史？让我们拭目以待！",
                    "尼克斯", "待定", null, null,
                    today.plusDays(5).atTime(9, 0), today.plusDays(5).atTime(11, 30)));

            // 球员交易新闻
            newsList.add(news("🔄 休赛期重磅交易：东契奇加盟湖人",
                    "湖人送出安东尼·戴维斯等筹码得到东契奇",
                    "NBA休赛期最重磅的交易终于达成！达拉斯独行侠队将卢卡·东契奇交易至洛杉矶湖人队，湖人则送出安东尼·戴维斯等筹码。东契奇将与勒布朗·詹姆斯组成超级双核，湖人瞬间成为夺冠热门。这笔交易震惊了整个联盟。",
                    "湖人", "独行侠", null, null,
                    today.minusDays(7).atTime(10, 0), today.minusDays(7).atTime(12, 0)));

            // 波尔津吉斯转会新闻
            newsList.add(news("📝 波尔津吉斯加盟凯尔特人，绿军内线升级",
                    "凯尔特人得到波尔津吉斯，组成豪华阵容",
                    "波士顿凯尔特人队宣布正式签下克里斯塔普斯·波尔津吉斯。这位2米21的拉脱维亚长人将与塔图姆、布朗组成三巨头，凯尔特人的阵容深度再次升级。波尔津吉斯上赛季场均17.8分6.5篮板，他的加盟让凯尔特人成为夺冠最大热门。",
                    "凯尔特人", "勇士", null, null,
                    today.minusDays(10).atTime(14, 0), today.minusDays(10).atTime(16, 0)));

            // MVP讨论
            newsList.add(news("🏅 MVP争夺战：亚历山大VS文班亚马",
                    "两位年轻巨星争夺本赛季MVP",
                    "本赛季的MVP争夺战异常激烈。雷霆队的谢伊·吉尔杰斯-亚历山大场均30.1分6.2助攻，带领雷霆打出西部第一的战绩。马刺队的维克托·文班亚马场均24.5分10.8篮板3.9盖帽，攻防两端统治力惊人。谁将最终捧起MVP奖杯？",
                    "雷霆", "马刺", null, null,
                    today.minusDays(3).atTime(16, 0), today.minusDays(3).atTime(18, 0)));

            // 新秀表现
            newsList.add(news("🌟 新秀观察：克内克特闪耀湖人",
                    "湖人新秀道尔顿·克内克特表现出色",
                    "洛杉矶湖人队的新秀道尔顿·克内克特本赛季表现出色，场均贡献9.2分，三分命中率高达39%。这位来自田纳西大学的射手已经成为湖人轮换阵容中的重要一员，与东契奇和詹姆斯的配合越来越默契。",
                    "湖人", "湖人", null, null,
                    today.minusDays(5).atTime(12, 0), today.minusDays(5).atTime(14, 0)));

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

    private static Player player(String name, Team team, String pos, double ppg, double rpg, double apg, double spg,
                                 int gp, double mpg, double fgPct, double threePct, double ftPct,
                                 double bpg, double tpg, double per, double tsPct, double usgPct,
                                 int jersey, String height, int weight, String country) {
        Player p = new Player();
        p.setName(name);
        p.setTeam(team);
        p.setPosition(pos);
        p.setPointsPerGame(ppg);
        p.setReboundsPerGame(rpg);
        p.setAssistsPerGame(apg);
        p.setStealsPerGame(spg);
        p.setGamesPlayed(gp);
        p.setMinutesPerGame(mpg);
        p.setFieldGoalPct(fgPct);
        p.setThreePointPct(threePct);
        p.setFreeThrowPct(ftPct);
        p.setBlocksPerGame(bpg);
        p.setTurnoversPerGame(tpg);
        p.setEfficiency(per);
        p.setTrueShootingPct(tsPct);
        p.setUsagePct(usgPct);
        p.setJerseyNumber(jersey);
        p.setHeight(height);
        p.setWeight(weight);
        p.setCountry(country);
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
        // 根据比分推断状态
        if (homeScore != null && awayScore != null) {
            g.setStatus("FINISHED");
        } else if (gameStartTime.isAfter(LocalDateTime.now())) {
            g.setStatus("SCHEDULED");
        } else {
            g.setStatus("LIVE");
        }
        return g;
    }

    /**
     * 初始化球队对战记录。
     * 当前球队战绩：
     *   湖人 47W/35L, 勇士 46W/36L, 凯尔特人 64W/18L,
     *   雄鹿 48W/34L, 掘金 57W/25L, 太阳 49W/33L
     * 以下对战记录覆盖各队之间的交锋，胜负分配与整体战绩方向一致。
     */
    private void seedMatchRecords() {
        LocalDate base = LocalDate.now();
        List<MatchRecord> records = new ArrayList<>();

        // ---- 湖人 vs 勇士（3场：湖人2胜1负） ----
        records.add(match("湖人", "勇士", 112, 108, base.minusDays(30), "2025-26", "FINISHED"));
        records.add(match("勇士", "湖人", 105, 98, base.minusDays(20), "2025-26", "FINISHED"));
        records.add(match("湖人", "勇士", 118, 110, base.minusDays(5), "2025-26", "FINISHED"));

        // ---- 湖人 vs 凯尔特人（2场：各1胜1负） ----
        records.add(match("凯尔特人", "湖人", 121, 115, base.minusDays(28), "2025-26", "FINISHED"));
        records.add(match("湖人", "凯尔特人", 108, 104, base.minusDays(12), "2025-26", "FINISHED"));

        // ---- 湖人 vs 雄鹿（2场：湖人1胜1负） ----
        records.add(match("雄鹿", "湖人", 115, 110, base.minusDays(25), "2025-26", "FINISHED"));
        records.add(match("湖人", "雄鹿", 120, 112, base.minusDays(8), "2025-26", "FINISHED"));

        // ---- 湖人 vs 掘金（2场：掘金1胜1负） ----
        records.add(match("掘金", "湖人", 118, 105, base.minusDays(22), "2025-26", "FINISHED"));
        records.add(match("湖人", "掘金", 110, 107, base.minusDays(3), "2025-26", "FINISHED"));

        // ---- 湖人 vs 太阳（2场：湖人2胜） ----
        records.add(match("太阳", "湖人", 98, 105, base.minusDays(18), "2025-26", "FINISHED"));
        records.add(match("湖人", "太阳", 115, 108, base.minusDays(1), "2025-26", "FINISHED"));

        // ---- 勇士 vs 凯尔特人（2场：凯尔特人2胜） ----
        records.add(match("勇士", "凯尔特人", 102, 118, base.minusDays(27), "2025-26", "FINISHED"));
        records.add(match("凯尔特人", "勇士", 125, 110, base.minusDays(10), "2025-26", "FINISHED"));

        // ---- 勇士 vs 雄鹿（2场：各1胜1负） ----
        records.add(match("雄鹿", "勇士", 110, 105, base.minusDays(24), "2025-26", "FINISHED"));
        records.add(match("勇士", "雄鹿", 115, 108, base.minusDays(7), "2025-26", "FINISHED"));

        // ---- 勇士 vs 掘金（2场：掘金2胜） ----
        records.add(match("掘金", "勇士", 122, 112, base.minusDays(21), "2025-26", "FINISHED"));
        records.add(match("勇士", "掘金", 100, 115, base.minusDays(4), "2025-26", "FINISHED"));

        // ---- 勇士 vs 太阳（2场：各1胜1负） ----
        records.add(match("太阳", "勇士", 108, 102, base.minusDays(17), "2025-26", "FINISHED"));
        records.add(match("勇士", "太阳", 120, 115, base.minusDays(2), "2025-26", "FINISHED"));

        // ---- 凯尔特人 vs 雄鹿（2场：凯尔特人2胜） ----
        records.add(match("凯尔特人", "雄鹿", 120, 105, base.minusDays(26), "2025-26", "FINISHED"));
        records.add(match("雄鹿", "凯尔特人", 108, 115, base.minusDays(9), "2025-26", "FINISHED"));

        // ---- 凯尔特人 vs 掘金（2场：各1胜1负） ----
        records.add(match("掘金", "凯尔特人", 112, 108, base.minusDays(23), "2025-26", "FINISHED"));
        records.add(match("凯尔特人", "掘金", 118, 110, base.minusDays(6), "2025-26", "FINISHED"));

        // ---- 凯尔特人 vs 太阳（2场：凯尔特人2胜） ----
        records.add(match("太阳", "凯尔特人", 100, 122, base.minusDays(19), "2025-26", "FINISHED"));
        records.add(match("凯尔特人", "太阳", 130, 115, base.minusDays(1), "2025-26", "FINISHED"));

        // ---- 雄鹿 vs 掘金（2场：掘金1胜1负） ----
        records.add(match("雄鹿", "掘金", 118, 112, base.minusDays(20), "2025-26", "FINISHED"));
        records.add(match("掘金", "雄鹿", 105, 110, base.minusDays(5), "2025-26", "FINISHED"));

        // ---- 雄鹿 vs 太阳（2场：雄鹿2胜） ----
        records.add(match("太阳", "雄鹿", 102, 115, base.minusDays(16), "2025-26", "FINISHED"));
        records.add(match("雄鹿", "太阳", 120, 108, base.minusDays(3), "2025-26", "FINISHED"));

        // ---- 掘金 vs 太阳（2场：掘金2胜） ----
        records.add(match("掘金", "太阳", 125, 110, base.minusDays(15), "2025-26", "FINISHED"));
        records.add(match("太阳", "掘金", 105, 118, base.minusDays(2), "2025-26", "FINISHED"));

        matchRecordRepository.saveAll(records);
    }

    private static MatchRecord match(String home, String away,
                                      int homeScore, int awayScore,
                                      LocalDate date, String season, String status) {
        MatchRecord m = new MatchRecord();
        m.setHomeTeam(home);
        m.setAwayTeam(away);
        m.setHomeScore(homeScore);
        m.setAwayScore(awayScore);
        m.setMatchDate(date);
        m.setSeason(season);
        m.setStatus(status);
        return m;
    }
}
