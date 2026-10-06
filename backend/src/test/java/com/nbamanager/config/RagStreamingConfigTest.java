package com.nbamanager.config;

import com.nbamanager.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RagStreamingConfigTest {
    @Test
    void streamingExecutorPreservesTheConfiguredMvcTimeout() {
        class Options extends AsyncSupportConfigurer {
            Long timeout() { return getTimeout(); }
        }
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "spring.mvc.async.request-timeout", "25000", "app.rag.stream.max-concurrent", "1")));
            context.register(RagStreamingConfig.class);
            context.refresh();
            var options = new Options();
            context.getBean(RagStreamingConfig.class).configureAsyncSupport(options);
            assertThat(options.timeout()).isEqualTo(25000L);
            assertThat(context.getBean("ragStreamExecutor", org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor.class)
                    .getMaxPoolSize()).isEqualTo(1);
        }
    }

    @Test
    void excessStreamsFailImmediatelyAndCapacityReturnsAfterCompletion() throws Exception {
        var executor = new RagStreamingConfig(1, 120000).ragStreamExecutor();
        executor.initialize();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> {
                started.countDown();
                try { release.await(2, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> executor.execute(() -> {})).isInstanceOf(TaskRejectedException.class);
            var response = new GlobalExceptionHandler().handleAsyncCapacity(new TaskRejectedException("full"));
            assertThat(response.getStatusCode().value()).isEqualTo(429);
            release.countDown();
            first.get(2, TimeUnit.SECONDS);
            // The completed task releases its worker before the next submission.
            for (int i = 0; i < 100 && executor.getActiveCount() != 0; i++) Thread.sleep(5);
            assertThat(executor.submit(() -> "available").get(1, TimeUnit.SECONDS)).isEqualTo("available");
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
