package com.nbamanager.web.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * 第 9 轮 P1-1 回归：小响应补 {@code Content-Length}（让 gzip 阈值生效）。
 *
 * <h2>缺陷</h2>
 * Tomcat 的 {@code min-response-size} 只在**已知 Content-Length** 时生效；
 * {@code @ResponseBody} JSON 响应全是 chunked、长度为 -1，于是被按"长度未知"压缩——
 * 实测 {@code /api/rag/stats} 98B → 115B（越压越大）。
 *
 * <h2>本测试锁定</h2>
 * 1. 小响应补上正确的 Content-Length（⇒ 容器不再压缩它）；
 * 2. 大响应**不补**长度、内容完整透传（⇒ 容器照常压缩，71%–92% 收益不回退）；
 * 3. 流式/SSE、HEAD、204/304 不被介入（避免破坏语义）；
 * 4. 边界：恰好等于阈值、空体、超阈值一个字节；
 * 5. 一旦使用 writer / flushBuffer / sendError（非 @ResponseBody 路径），
 *    必须转为直通——**绝不能把已写内容截断**（这是本过滤器最危险的失败模式）。
 */
class SmallResponseLengthFilterTest {

    private static final int THRESHOLD = 2048;

    private static MockHttpServletRequest request(String uri, String method) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRequestURI(uri);
        return req;
    }

    private static MockHttpServletResponse run(SmallResponseLengthFilter filter,
                                               MockHttpServletRequest req,
                                               ThrowingConsumer<jakarta.servlet.http.HttpServletResponse> body)
            throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        // 过滤器会把 response 包一层，因此这里必须用接口类型接收（不能强转 MockHttpServletResponse）
        filter.doFilter(req, res, (rq, rs) -> body.accept((jakarta.servlet.http.HttpServletResponse) rs));
        return res;
    }

    interface ThrowingConsumer<T> {
        void accept(T value) throws IOException;
    }

    private static byte[] bytes(int n) {
        byte[] data = new byte[n];
        java.util.Arrays.fill(data, (byte) 'a');
        return data;
    }

    // ------------------------------------------------------------ 小响应：补长度
    @Test
    void smallResponseGetsExactContentLength() throws Exception {
        SmallResponseLengthFilter filter = new SmallResponseLengthFilter(THRESHOLD);
        byte[] payload = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);

        MockHttpServletResponse res = run(filter, request("/api/rag/stats", "GET"), rs -> {
            rs.setContentType("application/json");
            rs.getOutputStream().write(payload);
        });

        assertThat(res.getContentLength()).isEqualTo(payload.length);
        assertThat(res.getContentAsByteArray()).isEqualTo(payload);
    }

    /** 98B 的 /api/rag/stats 就是本缺陷的原始样本。 */
    @Test
    void theOriginalOffendingPayloadGetsALength() throws Exception {
        SmallResponseLengthFilter filter = new SmallResponseLengthFilter(THRESHOLD);
        byte[] payload = bytes(98);

        MockHttpServletResponse res = run(filter, request("/api/rag/stats", "GET"),
                rs -> rs.getOutputStream().write(payload));

        assertThat(res.getContentLength())
                .as("98B < 2048B 阈值，必须补长度，容器才会跳过压缩")
                .isEqualTo(98);
    }

    @Test
    void emptyBodyGetsZeroLength() throws Exception {
        SmallResponseLengthFilter filter = new SmallResponseLengthFilter(THRESHOLD);

        MockHttpServletResponse res = run(filter, request("/api/anything", "GET"), rs -> { });

        assertThat(res.getContentLength()).isZero();
    }

    @Test
    void exactlyThresholdSizedResponseIsStillBuffered() throws Exception {
        SmallResponseLengthFilter filter = new SmallResponseLengthFilter(THRESHOLD);
        byte[] payload = bytes(THRESHOLD);

        MockHttpServletResponse res = run(filter, request("/api/x", "GET"),
                rs -> rs.getOutputStream().write(payload));

        assertThat(res.getContentLength()).isEqualTo(THRESHOLD);
        assertThat(res.getContentAsByteArray()).hasSize(THRESHOLD);
    }

    // ------------------------------------------------------------ 大响应：不介入
    @Test
    void largeResponseKeepsUnknownLengthSoContainerStillCompressesIt() throws Exception {
        SmallResponseLengthFilter filter = new SmallResponseLengthFilter(THRESHOLD);
        byte[] payload = bytes(THRESHOLD + 1);

        MockHttpServletResponse res = run(filter, request("/api/historical/players", "GET"),
                rs -> rs.getOutputStream().write(payload));

        assertThat(res.getContentLength())
                .as("超过阈值必须保持长度未知，否则大响应的压缩收益会回退")
                .isZero();   // 未设置长度：Mock 返回 0（容器层语义为 -1）
        assertThat(res.getContentAsByteArray())
                .as("内容必须完整透传，不能被截断")
                .isEqualTo(payload);
    }

    @Test
    void veryLargeResponseIsFullyForwardedAndNotBufferedEntirely() throws Exception {
        SmallResponseLengthFilter filter = new SmallResponseLengthFilter(THRESHOLD);
        byte[] payload = bytes(300_000);   // 接近 /api/historical/players?limit=500 的 292KB

        MockHttpServletResponse res = run(filter, request("/api/historical/players", "GET"),
                rs -> rs.getOutputStream().write(payload));

        assertThat(res.getContentAsByteArray()).isEqualTo(payload);
        assertThat(res.getContentLength()).isZero();   // 未设置长度：Mock 返回 0（容器层语义为 -1）
    }

    /** 分多次写入且累计超过阈值：内容顺序必须完整无缺。 */
    @Test
    void chunkedWritesCrossingThresholdStayIntact() throws Exception {
        SmallResponseLengthFilter filter = new SmallResponseLengthFilter(64);
        byte[] part1 = bytes(40);
        byte[] part2 = bytes(40);
        byte[] part3 = bytes(10);

        MockHttpServletResponse res = run(filter, request("/api/x", "GET"), rs -> {
            var out = rs.getOutputStream();
            out.write(part1);
            out.write(part2);
            out.write(part3);
        });

        byte[] expected = new byte[90];
        System.arraycopy(part1, 0, expected, 0, 40);
        System.arraycopy(part2, 0, expected, 40, 40);
        System.arraycopy(part3, 0, expected, 80, 10);
        assertThat(res.getContentAsByteArray()).isEqualTo(expected);
    }

    // ------------------------------------------------------------ 豁免与直通
    @Test
    void sseStreamIsNotFiltered() throws Exception {
        SmallResponseLengthFilter filter = new SmallResponseLengthFilter(THRESHOLD);

        assertThat(filter.shouldNotFilter(request("/api/rag/stream", "GET"))).isTrue();
        assertThat(filter.shouldNotFilter(request("/api/rag/stream", "POST"))).isTrue();
    }

    @Test
    void nonApiAndHeadRequestsAreNotFiltered() throws Exception {
        SmallResponseLengthFilter filter = new SmallResponseLengthFilter(THRESHOLD);

        assertThat(filter.shouldNotFilter(request("/posts/123", "GET"))).isTrue();
        assertThat(filter.shouldNotFilter(request("/api/dashboard/stats", "HEAD"))).isTrue();
        assertThat(filter.shouldNotFilter(request("/api/dashboard/stats", "GET"))).isFalse();
    }

    /**
     * ERROR 派发必须参与过滤。
     *
     * <b>这是 403/500 错误 JSON 仍被压缩放大的真正原因</b>：
     * {@code OncePerRequestFilter.shouldNotFilterErrorDispatch()} 默认返回 true
     * （不参与 ERROR 派发），其优先级高于 {@code shouldNotFilter}——
     * 仅覆写 shouldNotFilter 是没用的，必须同时覆写 shouldNotFilterErrorDispatch。
     */
    @Test
    void errorDispatchIsEnabled() {
        SmallResponseLengthFilter filter = new SmallResponseLengthFilter(THRESHOLD);

        assertThat(filter.shouldNotFilterErrorDispatch())
                .as("必须参与 ERROR 派发，否则 /error 生成的 403/500 JSON 拿不到 Content-Length")
                .isFalse();
    }

    @Test
    void errorDispatchIsFiltered() throws Exception {
        SmallResponseLengthFilter filter = new SmallResponseLengthFilter(THRESHOLD);

        // ERROR 派发时 URI 是原始路径（如 /api/rag/stats），由 /error 控制器生成响应体
        MockHttpServletRequest errorDispatch = new MockHttpServletRequest("GET", "/api/rag/stats") {
            @Override
            public jakarta.servlet.DispatcherType getDispatcherType() {
                return jakarta.servlet.DispatcherType.ERROR;
            }
        };
        errorDispatch.setRequestURI("/api/rag/stats");

        assertThat(filter.shouldNotFilter(errorDispatch))
                .as("ERROR 派发（403/500 错误 JSON）必须补长度")
                .isFalse();
    }

    /** ERROR 派发下同样只缓冲小响应：错误体很小，应拿到长度。 */
    @Test
    void errorResponseGetsContentLength() throws Exception {
        SmallResponseLengthFilter filter = new SmallResponseLengthFilter(THRESHOLD);
        byte[] payload = ("{\"timestamp\":\"2026-09-28T16:14:59.304+00:00\",\"status\":403,"
                + "\"error\":\"Forbidden\",\"path\":\"/api/rag/stats\"}").getBytes(StandardCharsets.UTF_8);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/rag/stats") {
            @Override
            public jakarta.servlet.DispatcherType getDispatcherType() {
                return jakarta.servlet.DispatcherType.ERROR;
            }
        };
        req.setRequestURI("/api/rag/stats");

        MockHttpServletResponse res = run(filter, req, rs -> {
            rs.setStatus(403);
            rs.setContentType("application/json");
            rs.getOutputStream().write(payload);
        });

        assertThat(res.getContentLength()).isEqualTo(payload.length);
        assertThat(res.getContentAsByteArray()).isEqualTo(payload);
    }

    @Test
    void noContentStatusIsNotGivenALength() throws Exception {
        SmallResponseLengthFilter filter = new SmallResponseLengthFilter(THRESHOLD);

        MockHttpServletResponse res = run(filter, request("/api/x", "DELETE"), rs -> {
            rs.setStatus(204);
            rs.getOutputStream().write(bytes(10));
        });

        assertThat(res.getContentLength())
                .as("204 语义上不允许带消息体，不能补长度")
                .isZero();   // 未设置长度：Mock 返回 0（容器层语义为 -1）
    }

    /**
     * 最危险的失败模式：一旦使用 writer（非 @ResponseBody 路径），
     * 必须整体直通，绝不能留下"Content-Length: 0 却丢掉了已写内容"。
     */
    @Test
    void writerPathFallsBackAndNeverTruncatesContent() throws Exception {
        SmallResponseLengthFilter filter = new SmallResponseLengthFilter(THRESHOLD);

        MockHttpServletResponse res = run(filter, request("/api/x", "GET"), rs -> {
            rs.getWriter().write("hello-from-writer");
        });

        assertThat(res.getContentAsString()).isEqualTo("hello-from-writer");
        assertThat(res.getContentLength()).isZero();   // 未设置长度：Mock 返回 0（容器层语义为 -1）
    }

    /** flushBuffer（主动流式产出）后同样转直通，内容不丢。 */
    @Test
    void flushBufferFallsBackToPassthrough() throws Exception {
        SmallResponseLengthFilter filter = new SmallResponseLengthFilter(THRESHOLD);

        MockHttpServletResponse res = run(filter, request("/api/x", "GET"), rs -> {
            rs.getOutputStream().write(bytes(10));
            rs.flushBuffer();
            rs.getOutputStream().write(bytes(5));
        });

        assertThat(res.getContentAsByteArray()).hasSize(15);
    }

    /**
     * 关键回归：{@code ResponseEntity} 路径会**先写内容再 flush**
     * （{@code HttpEntityMethodProcessor.handleReturnValue} → {@code flush()}）。
     *
     * 第一次 flush 必须被推迟，否则响应在过滤器收尾前就提交了，Content-Length 再也补不上——
     * 实测 {@code /api/rag/stats}、{@code /api/historical/**}、{@code /api/nba/**}、
     * {@code /api/drafts} 这 4 类端点正是因此修不好（只有裸 DTO 端点被修好）。
     */
    @Test
    void firstFlushIsDeferredSoLengthCanStillBeSet() throws Exception {
        SmallResponseLengthFilter filter = new SmallResponseLengthFilter(THRESHOLD);
        byte[] payload = "{\"documentCount\":2097}".getBytes(StandardCharsets.UTF_8);

        MockHttpServletResponse res = run(filter, request("/api/rag/stats", "GET"), rs -> {
            rs.setContentType("application/json");
            rs.getOutputStream().write(payload);
            rs.flushBuffer();          // ResponseEntity 路径的真实行为
        });

        assertThat(res.getContentLength())
                .as("首次 flush 被推迟，收尾时才能补上长度（否则 98B 会被压成 115B）")
                .isEqualTo(payload.length);
        assertThat(res.getContentAsByteArray()).isEqualTo(payload);
    }

    /** 第二次 flush 视为真的要增量输出：转直通，长度保持未知，内容不丢。 */
    @Test
    void repeatedFlushFallsBackToPassthrough() throws Exception {
        SmallResponseLengthFilter filter = new SmallResponseLengthFilter(THRESHOLD);

        MockHttpServletResponse res = run(filter, request("/api/x", "GET"), rs -> {
            rs.getOutputStream().write(bytes(10));
            rs.flushBuffer();          // 推迟
            rs.getOutputStream().write(bytes(5));
            rs.flushBuffer();          // 放行
            rs.getOutputStream().write(bytes(3));
        });

        assertThat(res.getContentAsByteArray()).hasSize(18);
        assertThat(res.getContentLength()).isZero();   // Mock 未设置 ⇒ 0（容器层为 -1）
    }

    /** 大响应即便走 ResponseEntity（先写后 flush）也必须保持长度未知，压缩收益不回退。 */
    @Test
    void largeResponseWithFlushKeepsUnknownLength() throws Exception {
        SmallResponseLengthFilter filter = new SmallResponseLengthFilter(THRESHOLD);
        byte[] payload = bytes(100_000);

        MockHttpServletResponse res = run(filter, request("/api/historical/players", "GET"), rs -> {
            rs.getOutputStream().write(payload);
            rs.flushBuffer();
        });

        assertThat(res.getContentAsByteArray()).isEqualTo(payload);
        assertThat(res.getContentLength()).isZero();
    }

    /** 服务端错误响应（401/403/500 的 JSON）也应补长度——它们同样是小响应。 */
    @Test
    void errorStatusJsonStillGetsLength() throws Exception {
        SmallResponseLengthFilter filter = new SmallResponseLengthFilter(THRESHOLD);
        byte[] payload = "{\"code\":403,\"message\":\"forbidden\"}".getBytes(StandardCharsets.UTF_8);

        MockHttpServletResponse res = run(filter, request("/api/injuries", "GET"), rs -> {
            rs.setStatus(403);
            rs.setContentType("application/json");
            rs.getOutputStream().write(payload);
        });

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentLength()).isEqualTo(payload.length);
        assertThat(res.getContentAsByteArray()).isEqualTo(payload);
    }
}
