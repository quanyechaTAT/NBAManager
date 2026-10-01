package com.nbamanager.web.support;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * F5 回归：入口并发隔离（bulkhead）。
 *
 * 压测报告 §十 10.3：开环 1500/2000 RPS 时实际只完成 1296/1579，p99 却涨到 5.1s/8.6s，
 * 且请求仍无界排队返回 200。修复后超限请求应立即拿到 429（快速失败），
 * 而不是一起变慢。
 */
class BulkheadFilterTest {

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        req.setRequestURI(uri);
        req.setRemoteAddr("10.0.0.1");
        return req;
    }

    @Test
    void rejectsWithTooManyRequestsWhenPerClientConcurrencyExceeded() throws Exception {
        BulkheadFilter filter = new BulkheadFilter(1, 100);

        // 第一个请求占住（不释放），模拟慢请求
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(() -> {
            try {
                filter.doFilter(request("/api/posts"), new MockHttpServletResponse(), (req, res) -> {
                    entered.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            } catch (Exception ignored) {
            }
        });
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        // 第二个并发请求应被拒（429）
        MockHttpServletResponse second = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        filter.doFilter(request("/api/posts"), second, (req, res) -> chainCalled.set(true));

        assertThat(second.getStatus()).isEqualTo(429);
        assertThat(second.getContentAsString()).contains("429");
        assertThat(chainCalled).as("被拒请求不应进入业务链").isFalse();

        release.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void allowsRequestsUnderTheLimit() throws Exception {
        BulkheadFilter filter = new BulkheadFilter(5, 100);

        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            AtomicInteger calls = new AtomicInteger();
            filter.doFilter(request("/api/posts"), res, (rq, rs) -> calls.incrementAndGet());
            assertThat(res.getStatus()).isEqualTo(200);
            assertThat(calls.get()).isEqualTo(1);
        }
    }

    @Test
    void releasesSlotAfterRequestCompletes() throws Exception {
        BulkheadFilter filter = new BulkheadFilter(1, 100);

        // 连续两次串行请求：第一次结束后额度应释放，第二次仍可通行
        for (int i = 0; i < 2; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(request("/api/posts"), res, (rq, rs) -> { });
            assertThat(res.getStatus()).as("第 %d 次串行请求应放行", i + 1).isEqualTo(200);
        }
        assertThat(filter.globalInFlight()).isZero();
    }

    @Test
    void globalLimitProtectsAgainstManyDistinctClients() throws Exception {
        // 单 IP 阈值放宽，但全局阈值很小 -> 第 3 个不同来源的并发请求应被拒
        BulkheadFilter filter = new BulkheadFilter(100, 2);

        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        FilterChain block = (req, res) -> {
            entered.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        for (int i = 1; i <= 2; i++) {
            final String ip = "10.0.0." + i;
            pool.submit(() -> {
                MockHttpServletRequest rq = new MockHttpServletRequest("GET", "/api/posts");
                rq.setRequestURI("/api/posts");
                rq.setRemoteAddr(ip);
                try {
                    filter.doFilter(rq, new MockHttpServletResponse(), block);
                } catch (Exception ignored) {
                }
            });
        }
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        MockHttpServletRequest third = new MockHttpServletRequest("GET", "/api/posts");
        third.setRequestURI("/api/posts");
        third.setRemoteAddr("10.0.0.3");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(third, res, (rq, rs) -> { });

        assertThat(res.getStatus()).isEqualTo(429);

        release.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void nonApiAndSsePathsAreExempt() throws Exception {
        BulkheadFilter filter = new BulkheadFilter(1, 100);

        // 静态/SPA 路径不受保护
        MockHttpServletResponse spa = new MockHttpServletResponse();
        filter.doFilter(request("/posts/123"), spa, (rq, rs) -> { });
        assertThat(spa.getStatus()).isEqualTo(200);

        // SSE 长连接被豁免，不占用并发额度
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(1);
        pool.submit(() -> {
            try {
                filter.doFilter(request("/api/rag/stream"), new MockHttpServletResponse(), (rq, rs) -> {
                    entered.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            } catch (Exception ignored) {
            }
        });
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

        // 即使 SSE 在跑，普通 API 仍能通过（说明 SSE 未占额度）
        MockHttpServletResponse api = new MockHttpServletResponse();
        filter.doFilter(request("/api/posts"), api, (rq, rs) -> { });
        assertThat(api.getStatus()).isEqualTo(200);

        release.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void slotIsReleasedEvenWhenDownstreamThrows() throws Exception {
        BulkheadFilter filter = new BulkheadFilter(1, 100);

        MockHttpServletResponse res = new MockHttpServletResponse();
        try {
            filter.doFilter(request("/api/posts"), res, (rq, rs) -> {
                throw new ServletException("boom");
            });
        } catch (ServletException expected) {
            // ignore
        }
        assertThat(filter.globalInFlight()).as("异常路径也必须归还额度").isZero();

        // 额度归还后，后续请求仍可通行
        MockHttpServletResponse after = new MockHttpServletResponse();
        filter.doFilter(request("/api/posts"), after, (rq, rs) -> { });
        assertThat(after.getStatus()).isEqualTo(200);
    }
}
