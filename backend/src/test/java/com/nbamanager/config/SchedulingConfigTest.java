package com.nbamanager.config;

import static org.assertj.core.api.Assertions.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

class SchedulingConfigTest {
    @Test void syncWorkersAndQueueAreBoundedAndRejectWithoutRunningInCaller() throws Exception {
        var executor = new SchedulingConfig().manualSyncExecutor(1, 1, 1);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        try {
            executor.execute(() -> { started.countDown(); try { release.await(); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); } });
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            executor.execute(() -> {});
            assertThatThrownBy(() -> executor.execute(() -> { throw new AssertionError("must not run in HTTP thread"); }))
                    .isInstanceOf(TaskRejectedException.class);
            assertThat(executor.getThreadPoolExecutor().getQueue()).hasSize(1);
        } finally { release.countDown(); executor.shutdown(); }
    }
    @Test void invalidSettingsCannotCreateUnboundedWorkersOrQueues() {
        var executor = new SchedulingConfig().manualSyncExecutor(100, 100, 10000);
        assertThat(executor.getCorePoolSize()).isEqualTo(8);
        assertThat(executor.getMaxPoolSize()).isEqualTo(8);
        assertThat(executor.getQueueCapacity()).isEqualTo(128);
    }
}
