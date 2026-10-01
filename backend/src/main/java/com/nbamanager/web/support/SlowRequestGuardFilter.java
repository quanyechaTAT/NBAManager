package com.nbamanager.web.support;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 慢请求总时长上限（压测报告 §十 10.6 P5 —— Slowloris 类资源占用面）。
 *
 * 现象：慢客户端（每 64B 停 50ms）可把单请求拖到 <b>59.5s</b>，服务端全程不主动断开。
 *
 * 为什么 socket 级超时解决不了（逐一实测，避免重复踩坑）：
 * 慢客户端的特征是"<b>持续但极慢地</b>发送/接收"，每 50ms 就有数据到达，于是
 * <ul>
 *   <li>{@code connection-timeout}（约束读取请求行/头的空闲）不触发 —— 实测请求头
 *       不全时 6.1s 会断开，但完整请求头 + 慢 body 时 80s 仍保持；</li>
 *   <li>{@code socket.soTimeout}（约束两次读之间的空闲）不触发 —— 空闲维度永远够不到阈值
 *       （设 8s 后实测 50s 仍保持）；</li>
 *   <li>{@code connectionUploadTimeout} 仅对上传语义生效，对本模式无效。</li>
 * </ul>
 *
 * 关于"慢请求体"（Slowloris 写方向）的机制澄清（第 4 轮修复 jstack 线程转储裁决，
 * <b>修正本类第 6 轮注释的错误结论</b>——"不派发工作线程/不进过滤器链"是误判，
 * 当时把"瞬时 400 后计数已释放"误读为"未进链"）：
 * 慢体请求<b>会进入过滤器链</b>并被 BulkheadFilter 计入在途；其线程占用分两段——
 * <ul>
 *   <li><b>链内 body 读取</b>（json 由 @RequestBody 触发、form 由参数解析触发）：
 *       本守卫的 30s 总时长上限覆盖该段，到点经 TomcatSocketCloser 关闭连接释放线程；</li>
 *   <li><b>响应完成后的 endRequest 吞剩余 body</b>（Http11Processor.endRequest→
 *       swallow）：此时过滤器链已返回，<b>守卫计时已取消、计数已释放，
 *       本守卫覆盖不到</b>——Tomcat 默认 maxSwallowSize=2MB，滴水（1.28KB/s）下
 *       每个连接可把一个工作线程钉住约 26 分钟。jstack 实测 220 慢体时
 *       200/200 工作线程全部卡在该段，连 bulkhead 的 429 快速失败都无法执行。
 *       已通过 {@code server.tomcat.max-swallow-size: 8KB} 收紧（吞满即断连，
 *       该段线程占用被封顶在 ~8KB/滴水速率），这是慢体风暴的主修复。</li>
 * </ul>
 * 慢读大响应（响应适配内核缓冲时不阻塞、阻塞时由本守卫封顶 30s）与慢业务
 * （RAG 等，分档 120s）见上。剩余风险与部署建议见 output/ROUND4_FIX_REPORT.md。
 *
 * 修复：改为对进入 servlet 的请求施加"<b>总时长</b>"上限——超时即回收处理线程，
 * 把单请求的资源占用时间封顶。
 *
 * 为什么到点后不能只用 {@code Thread.interrupt()}：它对 NIO 阻塞式 socket 读/写
 * 无效（第 5 轮实测：慢请求体客户端触发超时后，线程仍卡在读上，直到客户端停止
 * 滴水再等满一个 soTimeout 才收到 408，单请求被拖到 64.82s）。因此看门狗到点后
 * 会先<b>直接关闭底层客户端连接</b>（见 {@link TomcatSocketCloser}），让阻塞中的
 * 读/写立刻以 IOException 失败、线程归还线程池；interrupt 作为可中断等待
 * （sleep/wait/JDBC 部分场景）的补充手段同时执行。
 * 连接被关闭后客户端表现为"被服务端主动断开"（连接重置），符合第 3 轮任务书
 * "慢客户端在配置超时后被服务端主动断开"的验收口径。
 *
 * 实现要点：
 * - 用<b>共享的</b>单线程调度器做看门狗，而不是每请求新建线程（后者在高并发下
 *   本身就是可观开销）；每个在途请求只占用一个 ScheduledFuture；
 * - 只作用于 /api/**；
 * - <b>分档</b>上限：普通接口 30s；RAG/Copilot 用更宽的上限（默认 120s），
 *   因为它们有自己的长超时配置（RAG read 600s、Copilot read 60s），产品上允许
 *   LLM 慢查询跑几十秒，按 30s 一刀切会把"配置允许的慢"误判成"异常慢"；
 *   但仍设上限——实测把 RAG 完全豁免后，过载时请求会在 Python 侧无界排队
 *   （RAG 进程内存从 3.4GB 涨到 6GB），所以"宽限"不等于"不设限"；
 * - SSE(/api/rag/stream) 真正豁免（流式问答正常就持续很久）；
 * - 超时后链路抛出的异常（断连/中断的连锁反应）按 408 语义吞掉，
 *   不落到 Tomcat ErrorReportValve 记 500 + 堆栈噪音；
 * - 任一档为 0/负数表示关闭该档；内部异常一律 fail-open，守卫不能成为故障点。
 */
@Slf4j
public class SlowRequestGuardFilter extends OncePerRequestFilter {

    /** 真正豁免（不设总时长上限）的路径：SSE 长连接本身就会持续很久。 */
    private static final String[] EXEMPT_PREFIXES = {
            "/api/rag/stream",
    };

    /** 使用"宽限"上限的路径：自带长超时配置，但仍需要上界兜住无界排队。 */
    private static final String[] LONG_LIMIT_PREFIXES = {
            "/api/rag/",
            "/api/nba-copilot/",
    };

    private final long maxRequestMillis;
    private final long longRequestMillis;
    private final ScheduledExecutorService watchdog =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "slow-request-guard");
                t.setDaemon(true);
                return t;
            });

    public SlowRequestGuardFilter(long maxRequestMillis, long longRequestMillis) {
        this.maxRequestMillis = maxRequestMillis;
        this.longRequestMillis = longRequestMillis;
    }

    /** 取本次请求适用的上限：0 表示该档关闭。 */
    long limitFor(String uri) {
        if (uri == null) {
            return 0;
        }
        for (String prefix : LONG_LIMIT_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return longRequestMillis;
            }
        }
        return maxRequestMillis;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return true;
        }
        if (!uri.startsWith("/api/")) {
            return true;
        }
        for (String prefix : EXEMPT_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return limitFor(uri) <= 0;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        long limit = limitFor(request.getRequestURI());
        if (limit <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        Thread worker = Thread.currentThread();
        AtomicBoolean timedOut = new AtomicBoolean(false);

        ScheduledFuture<?> task;
        try {
            task = watchdog.schedule(() -> {
                timedOut.set(true);
                log.warn("请求处理超过 {}ms 上限，回收连接: {} {}",
                        limit, request.getMethod(), request.getRequestURI());
                // interrupt 对 NIO 阻塞 socket 读/写无效（实测单请求被拖到 64.82s），
                // 先直接关闭底层客户端连接：阻塞中的读/写立刻失败，线程归还线程池。
                // interrupt 仍要执行，兜底 sleep/wait 等可中断等待。
                TomcatSocketCloser.close(request);
                worker.interrupt();
            }, limit, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            // 调度器不可用时放行（fail-open），不因守卫本身导致请求失败
            filterChain.doFilter(request, response);
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            if (!timedOut.get()) {
                throw ex;
            }
            // 看门狗已判定超时并关闭了连接：这里的异常是断连/中断的连锁反应，
            // 不是新故障，吞掉（finally 会清中断标记并尽力补 408），
            // 避免落到 Tomcat ErrorReportValve 记 500 + 堆栈。
        } finally {
            task.cancel(false);
            if (timedOut.get()) {
                // 关键收尾：中断标记必须清掉。
                // 工作线程是 Tomcat 池里的共享线程：若它带着 interrupt 标记回到池中，
                // 下一次 poll/取任务时可能立刻抛 InterruptedException，被容器当作该 worker
                // 的退出信号（表现为偶发的连接被莫名断开）。
                // 另外中断也可能落在"请求刚好处理完"的窗口里，此时响应还没写——带着标记写
                // 响应同样会失败。先清标记，再写 408。
                Thread.interrupted();
            }
            if (timedOut.get() && !response.isCommitted()) {
                tryWriteRequestTimeout(response);
            }
        }
    }

    /** 尽力以 408 收尾；连接多半已被关闭，写失败一律忽略。 */
    private static void tryWriteRequestTimeout(HttpServletResponse response) {
        try {
            response.setStatus(HttpStatus.REQUEST_TIMEOUT.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"code\":" + HttpStatus.REQUEST_TIMEOUT.value()
                    + ",\"message\":\"请求处理超时，请稍后重试\"}");
            response.getWriter().flush();
        } catch (Exception ignored) {
            // 响应可能已被容器回收或连接已被本守卫关闭
        }
    }

    /** 供测试/诊断读取：是否已触发过超时。 */
    @PreDestroy
    void shutdown() {
        watchdog.shutdownNow();
    }
}
