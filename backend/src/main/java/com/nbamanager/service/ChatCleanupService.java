package com.nbamanager.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    @Scheduled(cron = "${app.chat.cleanup-cron:0 0 3 * * ?}")
    public void cleanupExpiredMessages() {
        if (!enabled) return;

        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY, "1", Duration.ofSeconds(LOCK_TTL_SECONDS));
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("未获取清理锁，跳过本次执行");
            return;
        }

        try {
            Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
            log.info("开始清理{}天前的聊天消息, cutoff={}", retentionDays, cutoff);

            int totalDeleted = 0;

            for (int batch = 0; batch < maxBatches; batch++) {
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
            redisTemplate.delete(LOCK_KEY);
        }
    }
}
