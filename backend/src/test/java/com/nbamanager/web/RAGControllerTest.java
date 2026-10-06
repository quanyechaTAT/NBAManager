package com.nbamanager.web;

import com.nbamanager.config.RagTimeoutProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nbamanager.service.RAGConversationService;
import com.nbamanager.service.RAGService;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class RAGControllerTest {

    @Mock
    private RAGService ragService;

    @Mock
    private RAGConversationService conversationService;

    @Test
    void forwardsFirstUpstreamChunkBeforeTheAnswerFinishes() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var firstChunk = new java.util.concurrent.CountDownLatch(1);
        var upstreamDone = new java.util.concurrent.CountDownLatch(1);
        var requestedPaths = new java.util.concurrent.CopyOnWriteArrayList<String>();
        server.createContext("/", exchange -> {
            requestedPaths.add(exchange.getRequestURI().getPath());
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                if (exchange.getRequestURI().getPath().equals("/stream")) {
                    output.write("data: {\"type\":\"chunk\",\"content\":\"首段分析\"}\n\n".getBytes(StandardCharsets.UTF_8));
                    output.flush();
                }
                firstChunk.await(2, java.util.concurrent.TimeUnit.SECONDS);
                upstreamDone.countDown();
                output.write("data: {\"type\":\"done\",\"follow_ups\":[\"具体追问\"]}\n\n".getBytes(StandardCharsets.UTF_8));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        server.start();
        var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var controller = new RAGController(ragService, conversationService, new RagTimeoutProperties());
            org.springframework.test.util.ReflectionTestUtils.setField(controller, "ragServerUrl", "http://127.0.0.1:" + server.getAddress().getPort());
            var response = new MockHttpServletResponse();
            var output = new ByteArrayOutputStream() {
                @Override
                public synchronized void flush() {
                    if (toString(StandardCharsets.UTF_8).contains("首段分析") && upstreamDone.getCount() == 1) {
                        firstChunk.countDown();
                    }
                }
            };
            var task = worker.submit(() -> {
                try { controller.streamQuery("分析马刺和尼克斯的比赛", 5, "s1", response).writeTo(output); }
                catch (Exception e) { throw new RuntimeException(e); }
            });
            assertThat(firstChunk.await(1500, java.util.concurrent.TimeUnit.MILLISECONDS)).isTrue();
            task.get(3, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(requestedPaths).containsExactly("/stream");
            assertThat(output.toString(StandardCharsets.UTF_8)).contains("具体追问");
            assertThat(response.getHeader("Content-Length")).isNull();
        } finally {
            firstChunk.countDown();
            worker.shutdownNow();
            server.stop(0);
        }
    }

    @Test
    void streamQueryReturnsValidJsonErrorEventWhenStartupFails() throws Exception {
        doThrow(new RuntimeException("service \"boom\"\nretry later"))
                .when(ragService)
                .ensureServerStarted();
        RAGController controller = new RAGController(ragService, conversationService, new RagTimeoutProperties());
        ByteArrayOutputStream body = new ByteArrayOutputStream();

        controller.streamQuery("today games", 5, "s1", new MockHttpServletResponse()).writeTo(body);

        String sse = body.toString(StandardCharsets.UTF_8);
        assertThat(sse).startsWith("data: ");
        String last = sse.trim().substring(sse.trim().lastIndexOf("data: ") + "data: ".length());
        JSONObject event = new JSONObject(last);
        assertThat(event.getString("type")).isEqualTo("error");
        assertThat(event.getString("error")).isEqualTo("service \"boom\"\nretry later");
    }

    // ==================== P2：conversationHistory 上限（安全报告 §十五 M10） ====================

    private static List<Map<String, String>> history(int turns, int contentLen) {
        return java.util.stream.IntStream.range(0, turns)
                .mapToObj(i -> Map.of("role", "user", "content", "c" + i, "fill", "x".repeat(contentLen)))
                .collect(java.util.stream.Collectors.toList());
    }

    private static Map<String, String> singleTurn(int contentLen) {
        return Map.of("role", "user", "content", "y".repeat(contentLen));
    }

    @Test
    void historyOverEightTurnsIsRejectedWith400() {
        var controller = new RAGController(ragService, conversationService, new RagTimeoutProperties());
        var request = new RAGController.RAGQueryRequest("合法问题", 5, null, history(9, 10));

        assertThatThrownBy(() -> controller.queryPost(request))
                .isInstanceOfSatisfying(com.nbamanager.exception.ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST))
                .hasMessageContaining("8 轮");
        verifyNoInteractions(ragService);
    }

    @Test
    void historyTurnOverThousandCharsIsRejectedWith400() {
        var controller = new RAGController(ragService, conversationService, new RagTimeoutProperties());
        var request = new RAGController.RAGQueryRequest("合法问题", 5, null,
                List.of(singleTurn(1001)));

        assertThatThrownBy(() -> controller.queryPost(request))
                .isInstanceOfSatisfying(com.nbamanager.exception.ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST))
                .hasMessageContaining("1000 字符");
        verifyNoInteractions(ragService);
    }

    @Test
    void legalHistoryOfEightTurnsIsAccepted() {
        var controller = new RAGController(ragService, conversationService, new RagTimeoutProperties());
        when(ragService.query(anyString(), anyInt(),
                org.mockito.ArgumentMatchers.any(), anyList()))
                .thenReturn(new com.nbamanager.service.RAGService.RAGResult(true, "答", List.of(), 1, "m", null));

        var request = new RAGController.RAGQueryRequest("合法问题", 5, null, history(8, 1000));
        var response = controller.queryPost(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody().answer()).isEqualTo("答");
    }

    @Test
    void nullHistoryIsAccepted() {
        var controller = new RAGController(ragService, conversationService, new RagTimeoutProperties());
        when(ragService.query(anyString(), anyInt(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.nbamanager.service.RAGService.RAGResult(true, "答", List.of(), 1, "m", null));

        var request = new RAGController.RAGQueryRequest("合法问题", 5, null, null);
        var response = controller.queryPost(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void queryForwardsSessionIdForConversationMemory() {
        when(ragService.query("他的三分命中率是多少？", 5, "session-1"))
                .thenReturn(RAGService.RAGResult.success("39.3%", java.util.List.of(), 12, "test"));
        RAGController controller = new RAGController(
                ragService, conversationService, new RagTimeoutProperties()
        );

        var response = controller.query("他的三分命中率是多少？", 5, "session-1");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().answer()).isEqualTo("39.3%");
        verify(ragService).query("他的三分命中率是多少？", 5, "session-1");
    }

    @Test
    void postQueryForwardsConversationHistoryForRestoredSession() {
        List<Map<String, String>> history = List.of(
                Map.of("role", "user", "content", "请告诉我斯蒂芬·库里本赛季的场均得分。"),
                Map.of("role", "assistant", "content", "斯蒂芬·库里本赛季场均得分为 26.6 分。")
        );
        when(ragService.query("他的三分命中率是多少？", 5, "session-1", history))
                .thenReturn(RAGService.RAGResult.success("39.3%", List.of(), 12, "test"));
        RAGController controller = new RAGController(
                ragService, conversationService, new RagTimeoutProperties()
        );

        var response = controller.queryPost(new RAGController.RAGQueryRequest(
                "他的三分命中率是多少？", 5, "session-1", history
        ));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().answer()).isEqualTo("39.3%");
        verify(ragService).query("他的三分命中率是多少？", 5, "session-1", history);
    }

    @Test
    void streamRequestBodyIncludesConversationHistoryForPythonMemoryHydration() {
        JSONObject body = RAGController.buildPythonRequestBody(
                "他的三分命中率是多少？",
                5,
                "session-1",
                List.of(
                        Map.of("role", "user", "content", "请告诉我斯蒂芬·库里本赛季的场均得分。"),
                        Map.of("role", "assistant", "content", "斯蒂芬·库里本赛季场均得分为 26.6 分。")
                )
        );

        assertThat(body.getString("question")).isEqualTo("他的三分命中率是多少？");
        assertThat(body.getInt("top_k")).isEqualTo(5);
        assertThat(body.getString("session_id")).isEqualTo("session-1");
        assertThat(body.getJSONArray("conversation_history")).hasSize(2);
        assertThat(body.getJSONArray("conversation_history").getJSONObject(0).getString("role")).isEqualTo("user");
        assertThat(body.getJSONArray("conversation_history").getJSONObject(0).getString("content"))
                .contains("斯蒂芬·库里");
    }
}
