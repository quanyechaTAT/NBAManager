package com.nbamanager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class SchedulingConfig {

    @Configuration
    @EnableScheduling
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class Timers {}

    /**
     * 同步作业共用有界预算，覆盖手动、定时和登录补偿来源。
     * 调度线程仅提交作业；队列满时由 SyncCoordinator 返回 SYNC_QUEUE_FULL。
     */
    @Bean("manualSyncExecutor")
    public ThreadPoolTaskExecutor manualSyncExecutor(
            @org.springframework.beans.factory.annotation.Value("${app.sync.executor-core-size:2}") int core,
            @org.springframework.beans.factory.annotation.Value("${app.sync.executor-max-size:4}") int max,
            @org.springframework.beans.factory.annotation.Value("${app.sync.executor-queue-capacity:16}") int queue) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int boundedMax = Math.max(1, Math.min(8, max));
        executor.setCorePoolSize(Math.max(1, Math.min(boundedMax, core)));
        executor.setMaxPoolSize(boundedMax);
        executor.setQueueCapacity(Math.max(0, Math.min(128, queue)));
        executor.setThreadNamePrefix("manual-sync-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        return executor;
    }
}
