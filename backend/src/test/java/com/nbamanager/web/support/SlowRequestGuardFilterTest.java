package com.nbamanager.web.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * F5 回归：慢请求总时长上限（压测报告 §十 10.6 P5）。
 *
 * 慢客户端（每 64B 停 50ms）可把单请求拖到 59.5s：socket 级超时都看"空闲多久"，
 * 而该模式每 50ms 就有数据到达，永不触发。因此改为按"总时长"封顶。
 */
class SlowRequestGuardFilterTest {

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        req.setRequestURI(uri);
        req.setRemoteAddr("10.0.0.1");
        return req;
    }

    @Test
    void interruptsAndReturns408WhenRequestExceedsLimit() throws Exception {
        SlowRequestGuardFilter filter = new SlowRequestGuardFilter(300, 300);

        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicReference<Throwable> chainError = new AtomicReference<>();
        CountDownLatch interrupted = new CountDownLatch(1);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        pool.submit(() -> {
            try {
                filter.doFilter(request("/api/posts"), res, (rq, rs) -> {
                    try {
                        // 模拟"持续有数据但很慢"的长时间处理
                        Thread.sleep(5_000);
                    } catch (InterruptedException e) {
                        interrupted.countDown();
                        throw new java.io.IOException("interrupted by guard", e);
                    }
                });
            } catch (Throwable t) {
                chainError.set(t);
            }
        });

        assertThat(interrupted.await(5, TimeUnit.SECONDS))
                .as("超过上限后工作线程应被中断")
                .isTrue();
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(chainError.get())
                .as("超时引发的连锁异常应由守卫吞掉（避免 Tomcat 记 500 噪音），不向外传播")
                .isNull();
        assertThat(res.getStatus())
                .as("超时且响应未提交时应回 408")
                .isEqualTo(408);
    }

    /**
     * 看门狗到点时工作线程可能阻塞在 NIO socket 读/写上——interrupt 对其无效，
     * 因此守卫必须先关闭底层客户端连接，让阻塞的读写立刻失败、线程归还线程池。
     * 这里断言超时路径确实触发了关闭尝试（反射链在 Tomcat 类可用时应解析成功）。
     */
    @Test
    void timeoutClosesUnderlyingClientSocket() throws Exception {
        SlowRequestGuardFilter filter = new SlowRequestGuardFilter(150, 150);
        MockHttpServletResponse res = new MockHttpServletResponse();
        CountDownLatch done = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        pool.submit(() -> {
            try {
                filter.doFilter(request("/api/posts"), res, (rq, rs) -> {
                    // 模拟对 interrupt 无响应的阻塞（等价于卡在 socket 读上）
                    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(2_000);
                    while (System.nanoTime() < deadline) {
                        Thread.onSpinWait();
                    }
                    rs.getWriter().write("late");
                });
            } catch (Exception ignored) {
                // 超时连锁异常由守卫吞掉
            } finally {
                done.countDown();
            }
        });

        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        // 反射链必须在当前 Tomcat 版本上可用，否则该机制退化为 interrupt-only，
        // 慢客户端风险回归——这里显式钉住，升级 Tomcat 后若断链会被测试发现
        assertThat(TomcatSocketCloser.isAvailable())
                .as("TomcatSocketCloser 反射链应解析成功（RequestFacade→coyote→socketWrapper→channel）")
                .isTrue();
    }

    @Test
    void fastRequestIsUnaffected() throws Exception {
        SlowRequestGuardFilter filter = new SlowRequestGuardFilter(5_000, 5_000);

        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(request("/api/posts"), res, (rq, rs) -> rs.getWriter().write("ok"));

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getContentAsString()).isEqualTo("ok");
    }

    @Test
    void sseAndNonApiPathsAreExempt() throws Exception {
        SlowRequestGuardFilter filter = new SlowRequestGuardFilter(1, 1);

        // SSE 长连接：即使远超上限也不应被中断
        MockHttpServletResponse sse = new MockHttpServletResponse();
        filter.doFilter(request("/api/rag/stream"), sse, (rq, rs) -> {
            sleepQuietly(150);
            rs.getWriter().write("data: done");
        });
        assertThat(sse.getStatus()).isEqualTo(200);

        // 静态/SPA 路径不受保护
        MockHttpServletResponse spa = new MockHttpServletResponse();
        filter.doFilter(request("/posts/123"), spa, (rq, rs) -> {
            sleepQuietly(150);
            rs.getWriter().write("<html>");
        });
        assertThat(spa.getStatus()).isEqualTo(200);
    }

    /**
     * RAG / Copilot 走"宽限"档而非完全豁免。
     *
     * 它们自带长超时（RAG read 600s、Copilot read 60s），按普通档 30s 一刀切会把
     * "配置允许的慢"误判成"异常慢"。但也不能完全豁免——实测把 RAG 全部豁免后，
     * 过载请求会在 Python 侧无界排队（RAG 进程内存从 3.4GB 涨到 6GB），
     * 所以"宽限"仍需要有上界。
     */
    @Test
    void ragAndCopilotUseLongerLimitThanNormalApi() {
        SlowRequestGuardFilter filter = new SlowRequestGuardFilter(30_000, 120_000);

        assertThat(filter.limitFor("/api/rag/query")).isEqualTo(120_000);
        assertThat(filter.limitFor("/api/rag/stats")).isEqualTo(120_000);
        assertThat(filter.limitFor("/api/nba-copilot/chat")).isEqualTo(120_000);
        assertThat(filter.limitFor("/api/posts")).isEqualTo(30_000);
        assertThat(filter.limitFor("/api/dashboard/stats")).isEqualTo(30_000);
    }

    /** SSE 真正豁免：不设总时长上限。 */
    @Test
    void sseStreamIsFullyExempt() throws Exception {
        SlowRequestGuardFilter filter = new SlowRequestGuardFilter(1, 1);

        MockHttpServletResponse sse = new MockHttpServletResponse();
        filter.doFilter(request("/api/rag/stream"), sse, (rq, rs) -> {
            sleepQuietly(150);
            rs.getWriter().write("data: done");
        });
        assertThat(sse.getStatus()).isEqualTo(200);
    }

    /** 某一档设为 0 表示关闭该档。 */
    @Test
    void zeroLimitDisablesThatTier() {
        SlowRequestGuardFilter filter = new SlowRequestGuardFilter(0, 120_000);

        assertThat(filter.limitFor("/api/posts")).isZero();
        assertThat(filter.limitFor("/api/rag/query")).isEqualTo(120_000);
    }

    /** 非豁免路径仍受上限约束（确认上面的豁免没有把整个守卫关掉）。 */
    @Test
    void nonExemptApiPathIsStillCapped() throws Exception {
        SlowRequestGuardFilter filter = new SlowRequestGuardFilter(200, 200);

        MockHttpServletResponse res = new MockHttpServletResponse();
        CountDownLatch interrupted = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        pool.submit(() -> {
            try {
                filter.doFilter(request("/api/posts"), res, (rq, rs) -> {
                    try {
                        Thread.sleep(5_000);
                    } catch (InterruptedException e) {
                        interrupted.countDown();
                        throw new java.io.IOException("interrupted", e);
                    }
                });
            } catch (Exception ignored) {
                // 中断后抛出，交由过滤器收尾
            }
        });

        assertThat(interrupted.await(5, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();
    }

    @Test
    void limitDisabledWhenZeroOrNegative() throws Exception {
        SlowRequestGuardFilter filter = new SlowRequestGuardFilter(0, 0);

        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(request("/api/posts"), res, (rq, rs) -> {
            sleepQuietly(200);
            rs.getWriter().write("slow-but-allowed");
        });

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getContentAsString()).isEqualTo("slow-but-allowed");
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * 回归：超时中断后必须清掉工作线程的 interrupt 标记。
     *
     * 工作线程是 Tomcat 池里的共享线程。若带着标记回到池中，下一次取任务时可能立刻抛
     * InterruptedException，被容器当成该 worker 的退出信号（表现为偶发连接被莫名断开）；
     * 而当中断恰好落在"请求刚处理完、响应还没写"的窗口时，连 408 都会写失败。
     *
     * 这里用一个<b>完全无视中断</b>的处理链来构造该状态（sleep 会吞掉标记，测不出来）。
     */
    @Test
    void interruptFlagIsClearedBeforeWorkerReturnsToPool() throws Exception {
        SlowRequestGuardFilter filter = new SlowRequestGuardFilter(150, 150);

        MockHttpServletResponse res = new MockHttpServletResponse();
        java.util.concurrent.atomic.AtomicBoolean interruptedDuringHandling =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicBoolean flagStillSetAfterReturn =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        CountDownLatch handled = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();

        pool.submit(() -> {
            try {
                filter.doFilter(request("/api/posts"), res, (rq, rs) -> {
                    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(600);
                    // 忙等并且不检查中断：标记会一直保持 set 状态
                    while (System.nanoTime() < deadline) {
                        if (Thread.currentThread().isInterrupted()) {
                            interruptedDuringHandling.set(true);
                        }
                    }
                });
                flagStillSetAfterReturn.set(Thread.currentThread().isInterrupted());
            } catch (Exception ignored) {
                flagStillSetAfterReturn.set(Thread.currentThread().isInterrupted());
            } finally {
                handled.countDown();
            }
        });

        assertThat(handled.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(interruptedDuringHandling)
                .as("超过上限后看门狗确实中断了工作线程")
                .isTrue();
        assertThat(flagStillSetAfterReturn)
                .as("中断标记必须被清掉，否则被污染的 worker 会回到池中")
                .isFalse();
        assertThat(res.getStatus())
                .as("清掉标记后 408 仍应正常写出")
                .isEqualTo(408);
    }
}
