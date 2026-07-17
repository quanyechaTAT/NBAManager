package com.nbamanager.service;

import com.nbamanager.domain.SyncJob;
import com.nbamanager.event.AdminLoginEvent;
import com.nbamanager.exception.SyncJobConflictException;
import com.nbamanager.repository.SyncJobRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncCatchupService {

    private final SyncCoordinator coordinator;
    private final SyncDefinitionRegistry registry;
    private final SyncJobRepository repository;
    private final SyncLockService lockService;

    @Value("${app.sync.catchup-guard-ttl-seconds:300}")
    private long catchupGuardTtlSeconds = 300;

    private final AtomicBoolean catchupPending = new AtomicBoolean(false);
    private final AtomicReference<String> pendingRequestedBy = new AtomicReference<>(null);

    @Async("taskExecutor")
    @EventListener
    public void onAdminLogin(AdminLoginEvent event) {
        String token = UUID.randomUUID().toString();
        if (!lockService.tryAcquire("nba:sync:catchup", token,
                java.time.Duration.ofSeconds(catchupGuardTtlSeconds))) {
            log.debug("补偿检查被跳过，另一个检查正在进行");
            return;
        }

        try {
            ZonedDateTime now = event.loggedInAt();
            boolean anyMissed = false;

            // Check if full sync is due
            for (SyncDefinition definition : registry.scheduledDefinitions()) {
                Optional<SyncJob> latest = repository.findTopByModuleAndStatusOrderByFinishedAtDesc(
                        definition.module(), "SUCCESS");
                boolean due = latest.isEmpty() || definition.isDue(toZoned(latest.get().getFinishedAt()), now);
                if (due) {
                    anyMissed = true;
                    log.info("检测到遗漏调度: module={}, 用户={}", definition.module(), event.username());
                    try {
                        coordinator.submit(SyncCommand.loginCatchup(definition.module(), event.username()));
                    } catch (SyncJobConflictException e) {
                        log.debug("补偿提交被跳过: module={}, reason={}", definition.module(), e.getMessage());
                    }
                }
            }

            // If any module is missed but couldn't be submitted, mark for retry
            if (anyMissed) {
                catchupPending.set(true);
                pendingRequestedBy.set(event.username());
            }
        } finally {
            lockService.release("nba:sync:catchup", token);
        }
    }

    @Scheduled(fixedDelayString = "${app.sync.catchup-retry-ms:60000}")
    public void retryPendingCatchup() {
        if (!catchupPending.get()) return;
        String username = pendingRequestedBy.get();
        if (username == null) return;

        String token = UUID.randomUUID().toString();
        if (!lockService.tryAcquire("nba:sync:catchup-retry", token,
                java.time.Duration.ofSeconds(catchupGuardTtlSeconds))) {
            return;
        }

        try {
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
            for (SyncDefinition definition : registry.scheduledDefinitions()) {
                Optional<SyncJob> latest = repository.findTopByModuleAndStatusOrderByFinishedAtDesc(
                        definition.module(), "SUCCESS");
                boolean due = latest.isEmpty() || definition.isDue(toZoned(latest.get().getFinishedAt()), now);
                if (due) {
                    coordinator.submit(SyncCommand.loginCatchup(definition.module(), username));
                }
            }
            catchupPending.set(false);
            pendingRequestedBy.set(null);
        } catch (SyncJobConflictException e) {
            log.debug("补偿重试被跳过: {}", e.getMessage());
        } finally {
            lockService.release("nba:sync:catchup-retry", token);
        }
    }

    private ZonedDateTime toZoned(LocalDateTime value) {
        return value.atZone(ZoneId.of("Asia/Shanghai"));
    }
}
