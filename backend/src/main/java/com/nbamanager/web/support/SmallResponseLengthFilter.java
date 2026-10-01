package com.nbamanager.web.support;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 小响应补 {@code Content-Length}，让 Tomcat 的 gzip 阈值真正生效（第 9 轮 P1-1）。
 *
 * <h2>缺陷（socket 级实测，读原始字节）</h2>
 * <pre>
 *   /api/rag/stats       98B → 115B（+17B，越压越大）
 *   /api/players/1      624B → 437B（&lt;2KB 仍被压缩，越界）
 *   /api/posts?size=20  924B → 514B（同上）
 *   /                   939B → 未压缩（正确）
 * </pre>
 *
 * <h2>根因（分两层，第二层是实测才暴露的）</h2>
 * <b>第一层</b>：Tomcat 只有在<b>已知 Content-Length</b> 时才能判断"小于阈值就不压"：
 * <pre>
 *   if (contentLength != -1 &amp;&amp; contentLength &lt; compressionMinSize) return false;
 * </pre>
 * {@code @ResponseBody} 响应由 Jackson 直接往输出流写，不预先给出长度 ⇒ 走 chunked、
 * contentLength = -1 ⇒ Tomcat 按"长度未知"压缩 ⇒ 98B 被压成 115B。
 *
 * <b>第二层</b>：仅"在过滤器收尾时补长度"**不够**。实测发现返回 {@code ResponseEntity}
 * 的接口会显式 flush，把响应在过滤器收尾前就提交掉：
 * <pre>
 *   HttpEntityMethodProcessor.handleReturnValue:248
 *     → ServletServerHttpResponse.flush()
 *     → LifecycleHttpServletResponse.flushBuffer()
 *     → OnCommittedResponseWrapper.flushBuffer()   ← 提交，之后无法再改 Content-Length
 * </pre>
 * 这就是为什么只有"裸 DTO"接口被修好（它们不 flush，Tomcat 自动补长度），
 * 而 {@code /api/rag/stats}、{@code /api/historical/**}、{@code /api/nba/**}、
 * {@code /api/drafts} 这些小响应依然是 chunked。（本类最初只做第一层，
 * 实测发现漏了 4 类端点后才补上第二层。）
 *
 * <h2>修复</h2>
 * 拦下这次"过早的 flush"：在缓冲未超阈值时，{@code flushBuffer()} 不立即提交，
 * 只做标记；等过滤器收尾时统一补 {@code Content-Length} 再写出。
 * 于是响应提交时长度已知 ⇒ Tomcat 判定"小响应不压缩"。
 *
 * <h2>安全边界（为什么不会成为故障点）</h2>
 * <ul>
 *   <li><b>内存有界</b>：每请求最多缓冲阈值字节（默认 2048）。180 并发在途 ⇒ 约 360KB。</li>
 *   <li><b>只推迟一次 flush</b>：第二次 {@code flushBuffer()} 即转直通。
 *       真正需要增量输出的流式接口不会被憋住（同时也说明：新增流式端点应加入
 *       {@link #STREAMING_PREFIXES} 豁免，或依赖这条"第二次 flush 即放行"的兜底）。</li>
 *   <li><b>超阈值即直通</b>：大响应（news 17.6KB、historical 292KB、shot-chart 133KB）
 *       保持长度未知 ⇒ Tomcat 照常压缩，71%–92% 的收益不回退。</li>
 *   <li><b>writer 路径直通</b>：只有 {@code getOutputStream()} 会被缓冲；
 *       一旦调用 {@code getWriter()}/{@code sendError}/{@code sendRedirect}
 *       就立刻转直通——宁可少优化，也绝不写坏响应。</li>
 *   <li><b>流式/SSE/HEAD 豁免</b>：{@code /api/rag/stream} 补长度会破坏流式语义；
 *       HEAD 无实体；异步派发（{@code isAsyncStarted}）不介入。</li>
 *   <li><b>204/304 不介入</b>：语义上不允许带消息体。</li>
 *   <li><b>fail-open</b>：缓冲/回写任何异常都按原样放行。</li>
 * </ul>
 */
@Slf4j
public class SmallResponseLengthFilter extends OncePerRequestFilter {

    /** SSE / 流式长连接：必须豁免。 */
    private static final String[] STREAMING_PREFIXES = {
            "/api/rag/stream",
    };

    private static final int NO_CONTENT = 204;
    private static final int NOT_MODIFIED = 304;

    private final int threshold;

    public SmallResponseLengthFilter(int threshold) {
        this.threshold = Math.max(1, threshold);
    }

    /**
     * 允许参与 ERROR 派发。
     *
     * <b>这是 403/500 错误 JSON 仍被压缩放大的真正原因</b>：
     * {@code OncePerRequestFilter.shouldNotFilterErrorDispatch()} 默认返回 {@code true}，
     * 即"不参与 ERROR 派发"——优先级高于 {@link #shouldNotFilter}，
     * 所以哪怕 {@code shouldNotFilter} 返回 false、注册里加了
     * {@code DispatcherType.ERROR}，过滤器仍然会被跳过。
     *
     * 实测：未认证访问 {@code /api/rag/stats} 得到 Boot 默认错误 JSON（403，102B），
     * 该响应由 {@code /error} 派发生成，因本方法未覆写而拿不到 Content-Length，
     * 于是被压成 118B（与 P1-1 要修的缺陷同型：小响应越压越大）。
     */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return true;
        }
        // ERROR 派发：URI 是原始路径（如 /api/rag/stats），由 /error 控制器生成响应体。
        // 这类响应（未认证/无权限的 403 JSON、未捕获异常的 500 JSON）同样需要 Content-Length。
        boolean errorDispatch = request.getDispatcherType() == jakarta.servlet.DispatcherType.ERROR;
        if (!errorDispatch) {
            if (!uri.startsWith("/api/")) {
                return true;
            }
            // HEAD 无实体，补长度会得到语义不符的 0
            if ("HEAD".equalsIgnoreCase(request.getMethod())) {
                return true;
            }
            for (String prefix : STREAMING_PREFIXES) {
                if (uri.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        LengthHintResponse wrapper = new LengthHintResponse(response, threshold);
        try {
            filterChain.doFilter(request, wrapper);
        } finally {
            // 异步/流式派发时响应尚未产出，不能在此收尾
            if (!request.isAsyncStarted()) {
                wrapper.finish();
            }
        }
    }

    /**
     * 带上限缓冲的响应包装：只在"能安全补长度"时才补，其余一律直通。
     */
    static final class LengthHintResponse extends HttpServletResponseWrapper {

        private final HttpServletResponse delegate;
        private final int threshold;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        /** true = 放弃优化，所有写入直接透传（不再补 Content-Length）。 */
        private boolean passthrough;
        /** 是否已经收到过一次 flush 请求（过一次即推迟，第二次就放行）。 */
        private boolean flushRequested;

        private ServletOutputStream stream;
        private PrintWriter writer;

        LengthHintResponse(HttpServletResponse delegate, int threshold) {
            super(delegate);
            this.delegate = delegate;
            this.threshold = threshold;
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (passthrough) {
                return delegate.getOutputStream();
            }
            if (stream == null) {
                stream = new BufferingStream();
            }
            return stream;
        }

        /**
         * 一旦走 writer，就放弃优化并交回容器。
         *
         * 刻意保守：混用 writer/stream 的包装极易写坏响应，而 {@code @ResponseBody} 路径
         * 走的是 {@code getOutputStream()}，不会用到 writer。
         */
        @Override
        public PrintWriter getWriter() throws IOException {
            if (writer == null) {
                // 先把已缓冲内容落到容器，再交回原生 writer，保证内容不丢
                toPassthrough();
                writer = delegate.getWriter();
            }
            return writer;
        }

        /**
         * 推迟第一次 flush。
         *
         * 这是本修复的关键：{@code ResponseEntity} 返回路径
         * （{@code HttpEntityMethodProcessor}）会显式 flush，若照做就会在过滤器
         * 收尾前提交响应，长度再也补不上——正是 4 类端点此前修不好的原因。
         *
         * 第二次 flush 视为"确实要增量输出"，转直通放行。
         */
        @Override
        public void flushBuffer() throws IOException {
            if (passthrough) {
                delegate.flushBuffer();
                return;
            }
            if (writer != null) {
                writer.flush();
            }
            if (flushRequested) {
                toPassthrough();
                delegate.flushBuffer();
                return;
            }
            // 第一次：只记录，内容留在缓冲里，等 finish() 补长度
            flushRequested = true;
        }

        @Override
        public boolean isCommitted() {
            return delegate.isCommitted();
        }

        @Override
        public void sendError(int sc) throws IOException {
            toPassthrough();
            delegate.sendError(sc);
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            toPassthrough();
            delegate.sendError(sc, msg);
        }

        @Override
        public void sendRedirect(String location) throws IOException {
            toPassthrough();
            delegate.sendRedirect(location);
        }

        /** 把已缓冲内容交给容器，并转为直通（长度保持未知 ⇒ 容器照常压缩）。 */
        private void toPassthrough() throws IOException {
            if (passthrough) {
                return;
            }
            passthrough = true;
            if (buffer.size() > 0) {
                delegate.getOutputStream().write(buffer.toByteArray());
                buffer.reset();
            }
        }

        /** 收尾：小响应补 Content-Length；已转直通则把剩余缓冲透传。 */
        void finish() {
            try {
                if (writer != null) {
                    writer.flush();
                }
                if (passthrough) {
                    if (buffer.size() > 0) {
                        delegate.getOutputStream().write(buffer.toByteArray());
                        buffer.reset();
                    }
                    return;
                }
                int status = delegate.getStatus();
                if (status == NO_CONTENT || status == NOT_MODIFIED || delegate.isCommitted()) {
                    return;
                }
                byte[] body = buffer.toByteArray();
                buffer.reset();
                // 关键一步：补上长度，容器才能按 min-response-size 判断"太小就不压"
                delegate.setContentLength(body.length);
                if (body.length > 0) {
                    delegate.getOutputStream().write(body);
                }
                delegate.getOutputStream().flush();
            } catch (IOException | RuntimeException e) {
                // fail-open：优化失败不影响正确性
                log.debug("小响应补 Content-Length 失败（按原样放行）: {}", e.toString());
            }
        }

        /** 带上限的输出流：超限即透传，保证内存有界。 */
        private final class BufferingStream extends ServletOutputStream {

            @Override
            public void write(int b) throws IOException {
                write(new byte[]{(byte) b}, 0, 1);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                if (passthrough) {
                    delegate.getOutputStream().write(b, off, len);
                    return;
                }
                if (buffer.size() + len > threshold) {
                    // 超过阈值：转直通（已缓冲内容先交给容器），长度未知 ⇒ 容器压缩
                    byte[] pending = buffer.toByteArray();
                    buffer.reset();
                    passthrough = true;
                    if (pending.length > 0) {
                        delegate.getOutputStream().write(pending);
                    }
                    delegate.getOutputStream().write(b, off, len);
                    return;
                }
                buffer.write(b, off, len);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
                // 同步缓冲实现不支持非阻塞写；同步派发路径不会调用
            }
        }
    }
}
