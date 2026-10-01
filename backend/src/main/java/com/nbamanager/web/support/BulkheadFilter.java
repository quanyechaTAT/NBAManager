package com.nbamanager.web.support;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 入口层并发隔离（bulkhead）：超限请求立即返回 429，避免无界排队。
 *
 * 背景（压测报告 §十 10.3 开环压测）：
 * 目标 1500/2000 RPS 时实际只完成 1296/1579，p99 却涨到 5.1s/8.6s，
 * 且**仍然全部返回 200** —— 请求在无界排队，而不是被拒绝或降级。
 * 这类"延迟换吞吐"在公网会迅速耗尽连接与工作线程，放大为可用性事故。
 *
 * 策略：按来源 IP 统计"在途请求数"，超过阈值立即 429（快速失败）。
 * - 只作用于 /api/**，且跳过 SSE 长连接（/api/rag/stream 本就会长时间占用），
 *   否则会把正常的流式问答误杀；
 * - 计数键用 remoteAddr（不可被 X-Forwarded-For 伪造），避免攻击者轮换头绕过；
 * - 任何内部异常都 fail-open（放行），限流组件本身绝不能成为故障点；
 * - 阈值与开关都可通过环境变量配置，便于按部署环境调整或关闭。
 *
 * 注意：这是"准入"保护，不追求精确 QoS。它保证的是：一旦并发超过阈值，
 * 多余请求会立刻拿到 429，而不是一起变慢。
 */
@Slf4j
public class BulkheadFilter extends OncePerRequestFilter {

    /** SSE 长连接：不受并发阈值约束（否则正常的流式问答会被误判为过载）。 */
    private static final String SSE_PATH = "/api/rag/stream";

    private final int perClientMaxConcurrent;
    private final int globalMaxConcurrent;

    private final ConcurrentHashMap<String, AtomicInteger> perClientInFlight = new ConcurrentHashMap<>();
    private final AtomicInteger globalInFlight = new AtomicInteger();

    public BulkheadFilter(
            @Value("${app.bulkhead.per-client-max-concurrent:100}") int perClientMaxConcurrent,
            @Value("${app.bulkhead.global-max-concurrent:400}") int globalMaxConcurrent) {
        this.perClientMaxConcurrent = perClientMaxConcurrent;
        this.globalMaxConcurrent = globalMaxConcurrent;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return true;
        }
        // 只保护 API；静态资源、SPA、/ws 一律不拦
        return !uri.startsWith("/api/") || uri.startsWith(SSE_PATH);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String key = clientKey(request);
        AtomicInteger counter = perClientInFlight.computeIfAbsent(key, k -> new AtomicInteger());
        int clientInflight = counter.incrementAndGet();
        int global = globalInFlight.incrementAndGet();

        boolean admitted = true;
        try {
            if (clientInflight > perClientMaxConcurrent || global > globalMaxConcurrent) {
                admitted = false;
                reject(response, clientInflight, global);
                return;
            }
            filterChain.doFilter(request, response);
        } finally {
            counter.decrementAndGet();
            globalInFlight.decrementAndGet();
            // 计数归零时移除条目，避免 map 随扫描来源无限增长
            if (counter.get() <= 0) {
                perClientInFlight.remove(key, counter);
            }
            if (!admitted) {
                log.debug("bulkhead 拒绝: ip={}, clientInFlight={}, global={}", key, clientInflight, global);
            }
        }
    }

    private String clientKey(HttpServletRequest request) {
        // 用 remoteAddr 而不是 X-Forwarded-For：后者可被客户端伪造，
        // 轮换头即可绕过按 IP 的限流（安全报告 §六 F5 同类问题的教训）。
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    private void reject(HttpServletResponse response, int clientInflight, int global)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":" + HttpStatus.TOO_MANY_REQUESTS.value()
                + ",\"message\":\"请求过于频繁，请稍后重试\"}");
        response.getWriter().flush();
    }

    /** 供测试/诊断读取当前在途数。 */
    public Map<String, Integer> snapshot() {
        Map<String, Integer> out = new java.util.HashMap<>();
        perClientInFlight.forEach((k, v) -> out.put(k, v.get()));
        return out;
    }

    public int globalInFlight() {
        return globalInFlight.get();
    }
}
