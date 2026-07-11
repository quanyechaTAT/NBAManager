package com.nbamanager.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatCleanupService {

    @Value("${app.chat.cleanup-enabled:true}")
    private boolean enabled;

    @Value("${app.chat.retention-days:180}")
    private int retentionDays;

    @Value("${app.chat.cleanup-batch-size:1000}")
    private int batchSize;

    @Value("${app.chat.cleanup-max-batches:500}")
    private int maxBatches;

    private final ChatCleanupBatchService batchService;
    private final StringRedisTemplate redisTemplate;

    private static final String LOCK_KEY = "nba:chat:cleanup:lock";
    private static final long LOCK_TTL_SECONDS = 300;

    /** 续期间隔（秒），测试中可设为极小值 */
    long renewalIntervalSeconds = 60;

    static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    static final DefaultRedisScript<Long> RENEWAL_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('expire', KEYS[1], ARGV[2]) else return 0 end",
            Long.class);

    /**
     * 创建 watchdog 调度器。测试可覆盖以注入可控调度器。
     */
    ScheduledExecutorService createWatchdogExecutor() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "chat-cleanup-watchdog");
            t.setDaemon(true);
            return t;
        });
    }

    @Scheduled(cron = "${app.chat.cleanup-cron:0 0 3 * * ?}")
    public void cleanupExpiredMessages() {
        if (!enabled) return;

        if (retentionDays <= 0 || batchSize <= 0 || maxBatches <= 0) {
            log.warn("聊天清理配置无效: retentionDays={}, batchSize={}, maxBatches={}", retentionDays, batchSize, maxBatches);
            return;
        }

        String lockValue = UUID.randomUUID().toString();
        boolean acquired = false;

        try {
            Boolean result = redisTemplate.opsForValue()
                    .setIfAbsent(LOCK_KEY, lockValue, Duration.ofSeconds(LOCK_TTL_SECONDS));
            acquired = Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("Redis锁获取异常，跳过本次清理", e);
            return;
        }

        if (!acquired) {
            log.debug("未获取清理锁，跳过本次执行");
            return;
        }

        // 每次执行创建局部状态，避免跨任务干扰
        AtomicBoolean renewalFailed = new AtomicBoolean(false);
        ScheduledExecutorService watchdog = createWatchdogExecutor();
        ScheduledFuture<?> renewalTask = null;

        try {
            renewalTask = watchdog.scheduleAtFixedRate(
                    () -> renewLock(lockValue, renewalFailed),
                    renewalIntervalSeconds, renewalIntervalSeconds, TimeUnit.SECONDS);

            Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
            log.info("开始清理{}天前的聊天消息, cutoff={}", retentionDays, cutoff);

            int totalDeleted = 0;

            for (int batch = 0; batch < maxBatches; batch++) {
                if (renewalFailed.get()) {
                    log.warn("锁续期失败，当前批次完成后停止清理");
                    break;
                }

                try {
                    int deleted = batchService.deleteOneBatch(cutoff, batchSize);
                    totalDeleted += deleted;
                    log.debug("第{}批删除{}条", batch + 1, deleted);
                    if (deleted == 0) break;
                } catch (Exception e) {
                    log.warn("Chat cleanup batch {} failed, stop current run", batch + 1, e);
                    break;
                }
            }

            log.info("聊天消息清理完成, 共删除{}条", totalDeleted);
        } finally {
            if (renewalTask != null) {
                renewalTask.cancel(false);
            }
            watchdog.shutdown();
            safeUnlock(lockValue);
        }
    }

    void renewLock(String lockValue, AtomicBoolean renewalFailed) {
        try {
            Long result = redisTemplate.execute(RENEWAL_SCRIPT,
                    Collections.singletonList(LOCK_KEY), lockValue, String.valueOf(LOCK_TTL_SECONDS));
            if (!Long.valueOf(1L).equals(result)) {
                log.warn("锁续期失败（锁已不属于当前实例），当前批次完成后停止");
                renewalFailed.set(true);
            }
        } catch (Exception e) {
            log.warn("锁续期异常，当前批次完成后停止", e);
            renewalFailed.set(true);
        }
    }

    private void safeUnlock(String lockValue) {
        try {
            redisTemplate.execute(UNLOCK_SCRIPT,
                    Collections.singletonList(LOCK_KEY), lockValue);
        } catch (Exception e) {
            log.warn("Redis锁释放异常", e);
        }
    }
}
