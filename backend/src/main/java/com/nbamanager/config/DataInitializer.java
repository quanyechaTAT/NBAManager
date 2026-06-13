package com.nbamanager.config;

import com.nbamanager.domain.Player;
import com.nbamanager.domain.Role;
import com.nbamanager.domain.UserAccount;
import com.nbamanager.repository.GameNewsRepository;
import com.nbamanager.repository.MatchRecordRepository;
import com.nbamanager.repository.PlayerRepository;
import com.nbamanager.repository.TeamRepository;
import com.nbamanager.repository.UserAccountRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private final UserAccountRepository userAccountRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final GameNewsRepository gameNewsRepository;
    private final MatchRecordRepository matchRecordRepository;
    private final com.nbamanager.repository.DraftPickRepository draftPickRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;
    private final com.nbamanager.service.NbaDataSyncService nbaDataSyncService;

    @Bean
    @Transactional
    CommandLineRunner seedData() {
        return args -> {
            // 检查管理员用户是否存在且角色正确
            var adminOpt = userAccountRepository.findByUsername("admin");
            if (adminOpt.isEmpty()) {
                log.info("管理员用户不存在，创建默认管理员...");
                seedUsers();
            } else if (adminOpt.get().getRole() != Role.ADMIN) {
                log.info("管理员用户角色不正确，更新为ADMIN...");
                adminOpt.get().setRole(Role.ADMIN);
                userAccountRepository.save(adminOpt.get());
            } else {
                log.info("管理员用户已存在，跳过用户初始化。");
            }

            // 所有数据从API同步，不再使用硬编码种子数据
            // 注意：不在启动时自动触发全量同步，避免阻止单模块同步
            // 管理员可以通过数据管理页面手动触发同步
            log.info("系统启动完成，数据同步可通过管理页面手动触发");

            // 赛事资讯：由 NbaLiveSyncService 定时任务自动同步

            // 清理重复球员
            log.info("开始清理重复球员...");
            final int[] dedupResult = {0};
            transactionTemplate.executeWithoutResult(status -> {
                // 按名字+球队去重，删除没有nbaPlayerId的重复记录
                int count = entityManager.createNativeQuery(
                        "DELETE p1 FROM players p1 " +
                        "INNER JOIN players p2 " +
                        "ON p1.name = p2.name AND p1.team_id = p2.team_id AND p1.id > p2.id " +
                        "WHERE p1.nba_player_id IS NULL OR p1.nba_player_id = 0")
                        .executeUpdate();
                if (count > 0) {
                    log.info("清理重复球员（无nbaPlayerId）: {} 条", count);
                    dedupResult[0] += count;
                }
                // 按nbaPlayerId去重（保留id最小的记录）
                count = entityManager.createNativeQuery(
                        "DELETE p1 FROM players p1 " +
                        "INNER JOIN players p2 " +
                        "ON p1.nba_player_id = p2.nba_player_id AND p1.id > p2.id " +
                        "WHERE p1.nba_player_id IS NOT NULL AND p1.nba_player_id > 0")
                        .executeUpdate();
                if (count > 0) {
                    log.info("清理重复球员（按nbaPlayerId）: {} 条", count);
                    dedupResult[0] += count;
                }
                // 按名字+球队去重（删除id较大的重复记录）
                count = entityManager.createNativeQuery(
                        "DELETE p1 FROM players p1 " +
                        "INNER JOIN players p2 " +
                        "ON p1.name = p2.name AND p1.team_id = p2.team_id AND p1.id > p2.id")
                        .executeUpdate();
                if (count > 0) {
                    log.info("清理重复球员（按名字+球队）: {} 条", count);
                    dedupResult[0] += count;
                }
            });
            if (dedupResult[0] > 0) {
                log.info("共清理重复球员: {} 条", dedupResult[0]);
            }

            // 补足已有球员的新增字段数据（DDL新增字段后为NULL）
            List<Player> allPlayers = playerRepository.findAll();
            boolean needsFieldFix = allPlayers.stream().anyMatch(
                    p -> p.getGamesPlayed() == null || p.getJerseyNumber() == null
                            || p.getPer() == null || p.getWinShares() == null);
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
                    if (p.getJerseyNumber() == null) p.setJerseyNumber("");
                    if (p.getHeight() == null) p.setHeight("6-6");
                    if (p.getWeight() == null) p.setWeight(210);
                    if (p.getCountry() == null) p.setCountry("美国");
                    if (p.getPer() == null) p.setPer(0.0);
                    if (p.getWinShares() == null) p.setWinShares(0.0);
                    if (p.getVorp() == null) p.setVorp(0.0);
                    if (p.getBpm() == null) p.setBpm(0.0);
                    if (p.getOffensiveRating() == null) p.setOffensiveRating(0.0);
                    if (p.getDefensiveRating() == null) p.setDefensiveRating(0.0);
                }
                playerRepository.saveAll(allPlayers);
            }

            // 修复球衣号码（将"0"更新为空字符串）
            fixJerseyNumbers();
        };
    }

    private void seedUsers() {
        // 从环境变量读取初始账号密码，避免硬编码
        String adminUser = System.getenv("ADMIN_USER");
        String adminPass = System.getenv("ADMIN_PASS");
        String normalUser = System.getenv("USER_USER");
        String normalPass = System.getenv("USER_PASS");

        if (adminUser == null || adminUser.isBlank()) adminUser = "admin";
        if (adminPass == null || adminPass.isBlank()) adminPass = "admin123";
        if (normalUser == null || normalUser.isBlank()) normalUser = "user";
        if (normalPass == null || normalPass.isBlank()) normalPass = "user123";

        UserAccount admin = new UserAccount();
        admin.setUsername(adminUser);
        admin.setPassword(passwordEncoder.encode(adminPass));
        admin.setRole(Role.ADMIN);
        userAccountRepository.save(admin);

        UserAccount user = new UserAccount();
        user.setUsername(normalUser);
        user.setPassword(passwordEncoder.encode(normalPass));
        user.setRole(Role.USER);
        userAccountRepository.save(user);
    }

    /**
     * 修复球衣号码（将"0"更新为空字符串）
     */
    private void fixJerseyNumbers() {
        transactionTemplate.executeWithoutResult(status -> {
            try {
                int result = entityManager.createNativeQuery(
                        "UPDATE players SET jersey_number = '' WHERE jersey_number = '0'")
                        .executeUpdate();
                if (result > 0) {
                    log.info("修复球衣号码（0→空）: {} 条", result);
                }
            } catch (Exception e) {
                log.warn("修复球衣号码失败: {}", e.getMessage());
            }
        });
    }
}
