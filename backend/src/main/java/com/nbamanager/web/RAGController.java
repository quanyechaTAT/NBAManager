package com.nbamanager.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nbamanager.web.support.StrictStringDeserializer;
import com.nbamanager.config.RagTimeoutProperties;
import com.nbamanager.domain.RAGConversation;
import com.nbamanager.security.UserPrincipal;
import com.nbamanager.service.RAGConversationService;
import com.nbamanager.service.RAGService;
import com.nbamanager.service.RAGService.RAGResult;
import com.nbamanager.service.RAGService.RAGStats;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * RAG 智能问答接口
 */
@Slf4j
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RAGController {

    private final RAGService ragService;
    private final RAGConversationService conversationService;
    private final RagTimeoutProperties timeoutProperties;

    /** RAG 服务地址，可用环境变量 RAG_SERVER_URL 或配置项 app.rag.server-url 覆盖 */
    @org.springframework.beans.factory.annotation.Value("${app.rag.server-url:${RAG_SERVER_URL:http://127.0.0.1:8899}}")
    private String ragServerUrl;

    /**
     * 智能问答（非流式）
     */
    @GetMapping("/query")
    public ResponseEntity<RAGResponse> query(
            @RequestParam String question,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(name = "session_id", required = false) String sessionId) {
        validateQuestion(question);
        RAGResult result = ragService.query(question, topK, scopedSession(sessionId));
        if (result.success()) {
            return ResponseEntity.ok(RAGResponse.success(result.answer(), result.sources(), result.responseTime(), result.model(), result.sourceDetails(), result.followUps()));
        } else {
            return ResponseEntity.ok(RAGResponse.error(result.error()));
        }
    }

    @PostMapping(
            value = "/query",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<RAGResponse> queryPost(@RequestBody RAGQueryRequest request) {
        validateQuestion(request == null ? null : request.question());
        validateConversationHistory(request == null ? null : request.conversationHistory());
        RAGResult result = ragService.query(
                request.question(),
                request.effectiveTopK(),
                scopedSession(request.sessionId()),
                request.conversationHistory()
        );
        if (result.success()) {
            return ResponseEntity.ok(RAGResponse.success(result.answer(), result.sources(), result.responseTime(), result.model(), result.sourceDetails(), result.followUps()));
        } else {
            return ResponseEntity.ok(RAGResponse.error(result.error()));
        }
    }

    /** Forward Python SSE frames as they arrive; never buffer a complete answer. */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public StreamingResponseBody streamQuery(
            @RequestParam String question,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(name = "session_id", required = false) String sessionId,
            HttpServletResponse response) {
        validateQuestion(question);
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        response.setContentType("text/event-stream;charset=utf-8");
        // Capture the authenticated context before entering the async response thread.
        UserPrincipal user = currentUser();
        List<Map<String, String>> history = user == null ? List.of() : conversationService.recentHistory(sessionId, user.getId());
        String pythonSession = scopedSession(sessionId);
        JSONObject body = buildPythonRequestBody(question, topK, pythonSession, history);

        return outputStream -> {
            HttpURLConnection conn = null;
            boolean terminalEvent = false;
            try {
                writeEvent(outputStream, new JSONObject().put("type", "status").put("message", "正在检索比赛和数据来源..."));
                ragService.ensureServerStarted();
                byte[] requestBody = body.toString().getBytes(StandardCharsets.UTF_8);
                conn = (HttpURLConnection) new URL(ragServerUrl + "/stream").openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Accept", "text/event-stream");
                conn.setRequestProperty("Connection", "close");
                conn.setDoOutput(true);
                conn.setConnectTimeout(timeoutProperties.getConnectMillis());
                conn.setReadTimeout(timeoutProperties.getReadMillis());
                conn.setFixedLengthStreamingMode(requestBody.length);
                try (OutputStream requestStream = conn.getOutputStream()) {
                    requestStream.write(requestBody);
                }
                if (conn.getResponseCode() != 200) {
                    throw new IllegalStateException("RAG 服务返回 HTTP " + conn.getResponseCode());
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder frame = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        frame.append(line).append('\n');
                        if (frame.length() > 1_000_000) throw new IllegalStateException("RAG SSE 事件过大");
                        if (!line.isEmpty()) continue;
                        String event = frame.toString();
                        outputStream.write(event.getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();
                        for (String eventLine : event.split("\n")) {
                            if (eventLine.startsWith("data:")) {
                                JSONObject data = new JSONObject(eventLine.substring(5).trim());
                                terminalEvent = data.optString("type").equals("done") || data.optString("type").equals("error");
                            }
                        }
                        frame.setLength(0);
                        if (terminalEvent) break;
                    }
                }
                if (!terminalEvent) throw new IllegalStateException("回答完成前上游连接中断，请重试");
            } catch (Exception e) {
                try {
                    writeEvent(outputStream, new JSONObject().put("type", "error")
                            .put("error", e.getMessage() == null ? "服务暂时不可用" : e.getMessage()));
                } catch (java.io.IOException disconnected) {
                    log.debug("RAG stream client disconnected");
                }
            } finally {
                if (conn != null) conn.disconnect();
            }
        };
    }

    /**
     * conversationHistory 上限（安全报告 §十五 M10）：python 端虽有"最近 8 轮、
     * 单条 1000 字"截断，但发生在 java 全量接收/解析/转发之后——无界请求体在
     * java 层形成内存与带宽放大面。与分页消毒器既定原则一致：**拒绝而非静默截断**。
     * {@link #buildPythonRequestBody} 内的截断保留为纵深防御（两条防线互为兜底）。
     * 范围说明：POST /api/rag/query 是唯一接受客户端 conversationHistory 的活跃路由
     * （GET /api/rag/stream 的 history 来自服务端 recentHistory(sessionId)；
     * RAGStreamRequest 的客户端构造路径当前无活跃路由，其内部截断保留为兜底）。
     */
    private static void validateConversationHistory(List<Map<String, String>> history) {
        if (history == null) {
            return;
        }
        if (history.size() > 8) {
            throw new com.nbamanager.exception.ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "会话历史最多 8 轮");
        }
        for (Map<String, String> turn : history) {
            String content = turn == null ? null : turn.get("content");
            if (content != null && content.length() > 1000) {
                throw new com.nbamanager.exception.ApiException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "会话历史单条最多 1000 字符");
            }
        }
    }

    /** question 长度上限：超长输入直送 LLM 的 token 成本攻击面 */
    private static void validateQuestion(String question) {
        if (question == null || question.isBlank() || question.length() > 2000) {
            throw new com.nbamanager.exception.ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "问题长度须在 2-2000 字符之间");
        }
    }

    private void writeEvent(OutputStream outputStream, JSONObject event) throws java.io.IOException {
        outputStream.write(("data: " + event + "\n\n").getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    private static UserPrincipal currentUser() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal ? principal : null;
    }

    private static String scopedSession(String sessionId) {
        UserPrincipal user = currentUser();
        return user == null || sessionId == null || sessionId.isBlank() ? sessionId : "user:" + user.getId() + ":" + sessionId;
    }

    static JSONObject buildPythonRequestBody(RAGStreamRequest request) {
        JSONObject body = new JSONObject()
                .put("question", request.question() == null ? "" : request.question())
                .put("top_k", request.effectiveTopK())
                .put("force_web_search", false);
        if (request.sessionId() != null && !request.sessionId().isBlank()) {
            body.put("session_id", request.sessionId());
        }

        JSONArray history = new JSONArray();
        List<Map<String, String>> turns = request.conversationHistory();
        if (turns != null) {
            int start = Math.max(0, turns.size() - 8);
            for (int index = start; index < turns.size(); index++) {
                Map<String, String> turn = turns.get(index);
                if (turn == null) continue;
                String role = turn.getOrDefault("role", "").trim().toLowerCase();
                String content = turn.getOrDefault("content", "").trim();
                if (!(role.equals("user") || role.equals("assistant")) || content.isEmpty()) {
                    continue;
                }
                if (content.length() > 1000) {
                    content = content.substring(0, 1000);
                }
                history.put(new JSONObject().put("role", role).put("content", content));
            }
        }
        body.put("conversation_history", history);
        return body;
    }

    static JSONObject buildPythonRequestBody(
            String question,
            int topK,
            String sessionId,
            List<Map<String, String>> conversationHistory) {
        return buildPythonRequestBody(new RAGStreamRequest(
                question, topK, sessionId, conversationHistory));
    }

    /**
     * 获取RAG统计信息（公开）
     */
    @GetMapping("/stats")
    public ResponseEntity<RAGStatsResponse> getStats() {
        RAGStats stats = ragService.getStats();
        return ResponseEntity.ok(new RAGStatsResponse(stats.documentCount(), stats.embeddingModel(), stats.llmModel()));
    }

    /**
     * 重建索引（仅管理员）
     */
    @PostMapping("/rebuild")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> rebuildIndex() {
        boolean wasRebuilding = ragService.isRebuilding();
        boolean success = ragService.rebuildIndex();
        if (wasRebuilding) {
            return ResponseEntity.ok(Map.of("status", "busy", "message", "索引重建进行中，请勿重复触发"));
        }
        return ResponseEntity.ok(Map.of("status", success ? "started" : "error", "message", success ? "索引重建已启动，后台执行完成后生效" : "索引重建失败"));
    }

    // ==================== 会话管理 API（需要认证 + 用户隔离） ====================

    /** 获取当前用户的会话列表 */
    @GetMapping("/conversations")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> listConversations(@AuthenticationPrincipal UserPrincipal user) {
        List<RAGConversation> convs = conversationService.listByUserId(user.getId());
        List<Map<String, Object>> result = convs.stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getSessionId());
            m.put("title", c.getTitle());
            m.put("time", c.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
            m.put("shareId", c.getShareId());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /** 获取当前用户的单个会话 */
    @GetMapping("/conversations/{sessionId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> getConversation(@PathVariable String sessionId, @AuthenticationPrincipal UserPrincipal user) {
        return conversationService.getBySessionId(sessionId, user.getId())
                .map(c -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", c.getSessionId());
                    m.put("title", c.getTitle());
                    m.put("time", c.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
                    m.put("messages", c.getMessagesJson());
                    m.put("shareId", c.getShareId());
                    return ResponseEntity.ok(m);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** 获取分享的会话（公开，不需要认证） */
    @GetMapping("/shared/{shareId}")
    public ResponseEntity<?> getSharedConversation(@PathVariable String shareId) {
        return conversationService.getByShareId(shareId)
                .map(c -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", c.getSessionId());
                    m.put("title", c.getTitle());
                    m.put("time", c.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
                    m.put("messages", c.getMessagesJson());
                    return ResponseEntity.ok(m);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** 保存/更新当前用户的会话 */
    @PostMapping("/conversations")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> saveConversation(@RequestBody Map<String, String> body, @AuthenticationPrincipal UserPrincipal user) {
        String sessionId = body.get("sessionId");
        String title = body.get("title");
        String messagesJson = body.get("messages");
        if (sessionId == null || messagesJson == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "缺少必要参数"));
        }
        RAGConversation conv = conversationService.saveOrUpdate(sessionId, title, messagesJson, user.getId());
        Map<String, Object> result = new HashMap<>();
        result.put("id", conv.getSessionId());
        result.put("title", conv.getTitle());
        return ResponseEntity.ok(result);
    }

    /** 删除当前用户的会话 */
    @DeleteMapping("/conversations/{sessionId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> deleteConversation(@PathVariable String sessionId, @AuthenticationPrincipal UserPrincipal user) {
        boolean deleted = conversationService.deleteBySessionId(sessionId, user.getId());
        if (deleted) {
            return ResponseEntity.ok(Map.of("success", true));
        }
        return ResponseEntity.status(404).body(Map.of("error", "会话不存在或无权删除"));
    }

    /** 为当前用户的会话生成分享链接 */
    @PostMapping("/conversations/{sessionId}/share")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> shareConversation(@PathVariable String sessionId, @AuthenticationPrincipal UserPrincipal user) {
        try {
            String shareId = conversationService.generateShareId(sessionId, user.getId());
            return ResponseEntity.ok(Map.of("shareId", shareId));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }


    public record RAGQueryRequest(
            // 严格类型：拒绝 JSON number/boolean（安全报告 §十 10.5 RISK-1）
            @JsonDeserialize(using = StrictStringDeserializer.class) String question,
            @JsonProperty("top_k") Integer topK,
            @JsonProperty("session_id") String sessionId,
            List<Map<String, String>> conversationHistory
    ) {
        public int effectiveTopK() {
            return topK == null ? 5 : Math.max(1, Math.min(20, topK));
        }
    }

    public record RAGStreamRequest(
            // 严格类型：拒绝 JSON number/boolean（安全报告 §十 10.5 RISK-1）
            @JsonDeserialize(using = StrictStringDeserializer.class) String question,
            @JsonProperty("top_k") Integer topK,
            @JsonProperty("session_id") String sessionId,
            List<Map<String, String>> conversationHistory
    ) {
        public int effectiveTopK() {
            return topK == null ? 5 : Math.max(1, Math.min(20, topK));
        }
    }

    public record RAGResponse(boolean success, String answer, List<String> sources, int responseTime, String model, String error, List<Object> sourceDetails, @JsonProperty("follow_ups") List<String> followUps) {
        public RAGResponse(boolean success, String answer, List<String> sources, int responseTime, String model, String error, List<Object> sourceDetails) { this(success, answer, sources, responseTime, model, error, sourceDetails, List.of()); }
        public RAGResponse(boolean success, String answer, List<String> sources, int responseTime, String model, String error) { this(success, answer, sources, responseTime, model, error, List.of()); }
        public static RAGResponse success(String answer, List<String> sources, int responseTime, String model) {
            return new RAGResponse(true, answer, sources, responseTime, model, null);
        }
        public static RAGResponse success(String answer, List<String> sources, int responseTime, String model, List<Object> details) { return new RAGResponse(true, answer, sources, responseTime, model, null, details); }
        public static RAGResponse success(String answer, List<String> sources, int responseTime, String model, List<Object> details, List<String> followUps) { return new RAGResponse(true, answer, sources, responseTime, model, null, details, followUps); }
        public static RAGResponse error(String error) {
            return new RAGResponse(false, null, List.of(), 0, null, error);
        }
    }

    public record RAGStatsResponse(int documentCount, String embeddingModel, String llmModel) {}
}
