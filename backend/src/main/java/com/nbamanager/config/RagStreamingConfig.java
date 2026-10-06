package com.nbamanager.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Long-lived responses have a separate, bounded executor and never queue. */
@Configuration
public class RagStreamingConfig implements WebMvcConfigurer {
    private final int maxConcurrent;
    private final long timeoutMillis;

    public RagStreamingConfig(
            @Value("${app.rag.stream.max-concurrent:16}") int maxConcurrent,
            @Value("${app.rag.stream.timeout-millis:${spring.mvc.async.request-timeout:600000}}") long timeoutMillis) {
        this.maxConcurrent = Math.max(1, Math.min(128, maxConcurrent));
        this.timeoutMillis = Math.max(1000, timeoutMillis);
    }

    @Bean(name = "ragStreamExecutor")
    public ThreadPoolTaskExecutor ragStreamExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.min(2, maxConcurrent));
        executor.setMaxPoolSize(maxConcurrent);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("RagStream-");
        return executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(ragStreamExecutor());
        configurer.setDefaultTimeout(timeoutMillis);
    }
}
