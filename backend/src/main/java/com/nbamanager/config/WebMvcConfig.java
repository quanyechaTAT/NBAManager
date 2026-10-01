package com.nbamanager.config;

import com.nbamanager.web.support.BulkheadFilter;
import com.nbamanager.web.support.PagingRequestInterceptor;
import com.nbamanager.web.support.SlowRequestGuardFilter;
import com.nbamanager.web.support.SmallResponseLengthFilter;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class WebMvcConfig implements WebMvcConfigurer {

    private final PagingRequestInterceptor pagingRequestInterceptor;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /**
     * 慢请求总时长上限（压测报告 §十 10.6：慢客户端可把单请求拖到 59.5s）。
     * socket 级超时对"持续极慢发送"无效（每 50ms 有新数据，空闲维度永远不触发），
     * 因此改为按总时长封顶。分两档：普通接口 + 自带长超时的 RAG/Copilot。
     * SSE 豁免。任一档设为 0 表示关闭。
     */
    @Bean
    public FilterRegistrationBean<SlowRequestGuardFilter> slowRequestGuardFilterRegistration(
            @Value("${app.http.max-request-millis:30000}") long maxRequestMillis,
            @Value("${app.http.long-request-millis:120000}") long longRequestMillis) {
        FilterRegistrationBean<SlowRequestGuardFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SlowRequestGuardFilter(maxRequestMillis, longRequestMillis));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }

    /**
     * 入口并发隔离：超限请求立即 429，避免过载时无界排队（压测报告 §十 10.3）。
     * 通过 app.bulkhead.enabled=false 可关闭（保留排障/压测时的对照能力）。
     *
     * 关闭为什么用 @ConditionalOnProperty 而不是在方法体里 if：
     * - {@code registration.setEnabled(false)} 且未 setFilter → 启动抛
     *   {@code IllegalArgumentException: Filter must not be null}；
     * - 直接返回 null → Spring 会把它包成 NullBean，而 FilterRegistrationBean 实现了
     *   ServletContextInitializer，收集时抛 {@code BeanNotOfRequiredTypeException}。
     * 两者都会让「关闭该保护」这一配置直接把应用弄挂，因此改为不注册该 bean。
     *
     * globalMax 为什么默认 180（< threads.max=200）：请求"在途数"受工作线程数约束，
     * 全局阈值若 ≥ 线程数则永远不会成为第一个约束，等于虚设（第 5 轮实测：
     * 线程池被慢客户端占满后，本应 1ms 返回的 429 也要排队 29.7s）。
     * 留 20 线程余量保证拒绝响应与正常流量始终有线程可用。
     */
    @Bean
    @ConditionalOnProperty(name = "app.bulkhead.enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<BulkheadFilter> bulkheadFilterRegistration(
            @Value("${app.bulkhead.per-client-max-concurrent:100}") int perClientMax,
            @Value("${app.bulkhead.global-max-concurrent:180}") int globalMax) {
        FilterRegistrationBean<BulkheadFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new BulkheadFilter(perClientMax, globalMax));
        registration.addUrlPatterns("/api/*");
        // 排在鉴权之前：过载时尽早失败，不浪费 BCrypt/JWT 校验成本
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        log.info("BulkheadFilter 已启用: perClientMax={}, globalMax={}", perClientMax, globalMax);
        return registration;
    }

    /**
     * 小响应补 Content-Length，让 {@code server.compression.min-response-size} 生效
     * （第 9 轮 P1-1：API 响应全是 chunked、无长度，Tomcat 只能按"长度未知"压缩，
     * 导致 98B 的 /api/rag/stats 被压成 115B）。
     *
     * 顺序说明：必须排在压缩过滤器**之前**（即 order 更小），这样长度在压缩判定前就已写好。
     * Tomcat 的 compression filter 默认 order 是 {@code Ordered.LOWEST_PRECEDENCE}，
     * 因此这里用 HIGHEST_PRECEDENCE + 30（在 bulkhead +10 / slow-guard +20 之后，
     * 但在压缩之前）即可。
     *
     * <b>ERROR 派发必须纳入</b>：未认证/无权限时，拒绝响应（403 JSON）由 Spring Boot 的
     * {@code /error} 派发生成，而 {@code OncePerRequestFilter} 默认<b>不</b>参与 ERROR 派发
     * ⇒ 该响应拿不到 Content-Length ⇒ 仍被压缩放大（实测 102B → 118B，
     * 与 P1-1 要修的缺陷同型）。因此显式加上 {@code DispatcherType.ERROR}。
     */
    @Bean
    public FilterRegistrationBean<SmallResponseLengthFilter> smallResponseLengthFilterRegistration(
            @Value("${server.compression.min-response-size:2048}") int threshold) {
        FilterRegistrationBean<SmallResponseLengthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SmallResponseLengthFilter(threshold));
        // /error 也要注册：ERROR 派发的目标路径是 /error（原始 URI 在 request attribute 里），
        // 只注册 /api/* 会让 403/500 的错误 JSON 拿不到长度（仍被压缩放大）。
        registration.addUrlPatterns("/api/*", "/error");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 30);
        registration.setDispatcherTypes(
                jakarta.servlet.DispatcherType.REQUEST,
                jakarta.servlet.DispatcherType.ASYNC,
                jakarta.servlet.DispatcherType.ERROR);
        log.info("SmallResponseLengthFilter 已启用: threshold={}B（<= 阈值的响应不压缩）", threshold);
        return registration;
    }

    /**
     * 分页边界统一在 Controller 之前校验：
     * page=abc / size=abc / 超大 page 一律 400，避免静默回落或 JPA offset 溢出成 500。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(pagingRequestInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        String location = root.toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}
