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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private final ExecutorService executor = Executors.newCachedThreadPool();

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
        RAGResult result = ragService.query(question, topK, sessionId);
        if (result.success()) {
            return ResponseEntity.ok(RAGResponse.success(result.answer(), result.sources(), result.responseTime(), result.model()));
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
                request.sessionId(),
                request.conversationHistory()
        );
        if (result.success()) {
            return ResponseEntity.ok(RAGResponse.success(result.answer(), result.sources(), result.responseTime(), result.model()));
        } else {
            return ResponseEntity.ok(RAGResponse.error(result.error()));
        }
    }

    /**
     * 智能问答（SSE）。
     *
     * 实现说明：曾经逐行代理 python 的 /stream SSE，但该链路长期受两类问题困扰——
     * JDK KeepAliveCache 复用 uvicorn 已关闭的死连接（随机 Premature EOF）、
     * Tomcat 对 POST+StreamingResponseBody 不写 chunked 终止块（浏览器判为网络错误）。
     * 现改为内部调用被验证稳定的非流式 /query，由本端合成 SSE 事件序列；
     * chunk 按 60 字符分片以保留打字机式渐进渲染。
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public StreamingResponseBody streamQuery(
            @RequestParam String question,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(name = "session_id", required = false) String sessionId,
            HttpServletResponse response) {
        validateQuestion(question);
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setContentType("text/event-stream;charset=utf-8");

        return outputStream -> {
            try {
                ragService.ensureServerStarted();

                JSONObject body = new JSONObject()
                        .put("question", question == null ? "" : question)
                        .put("top_k", topK);
                if (sessionId != null && !sessionId.isBlank()) {
                    body.put("session_id", sessionId);
                }
                JSONArray history = new JSONArray();
                for (Map<String, String> turn : conversationService.recentHistory(sessionId)) {
                    history.put(new JSONObject()
                            .put("role", turn.getOrDefault("role", "user"))
                            .put("content", turn.getOrDefault("content", "")));
                }
                body.put("conversation_history", history);

                byte[] requestBody = body.toString().getBytes(StandardCharsets.UTF_8);
                URL url = new URL(ragServerUrl + "/query");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Connection", "close");
                conn.setDoOutput(true);
                conn.setConnectTimeout(timeoutProperties.getConnectMillis());
                conn.setReadTimeout(timeoutProperties.getReadMillis());
                conn.setFixedLengthStreamingMode(requestBody.length);
                try (OutputStream requestStream = conn.getOutputStream()) {
                    requestStream.write(requestBody);
                }

                String payload;
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    payload = sb.toString();
                }

                JSONObject resp = new JSONObject(payload);
                if (!resp.optBoolean("success", true)) {
                    throw new IllegalStateException(resp.optString("error", "RAG 服务返回失败"));
                }

                // 合成完整 SSE 文本后按 Content-Length 一次性写出：
                // chunked 传输在 StreamingResponseBody 完成路径上存在终止块缺失问题
                //（Tomcat 直接关闭连接，严格客户端报 TransferEncodingError），identity 传输彻底规避
                StringBuilder sse = new StringBuilder();
                JSONObject sourcesEvt = new JSONObject()
                        .put("type", "sources")
                        .put("sources", resp.optJSONArray("sources") == null ? new JSONArray() : resp.optJSONArray("sources"))
                        .put("metadatas", resp.optJSONArray("metadatas") == null ? new JSONArray() : resp.optJSONArray("metadatas"))
                        .put("search_results", resp.optJSONArray("search_results") == null ? new JSONArray() : resp.optJSONArray("search_results"))
                        .put("used_tools", resp.optJSONArray("used_tools") == null ? new JSONArray() : resp.optJSONArray("used_tools"))
                        .put("tool_trace", resp.optJSONArray("tool_trace") == null ? new JSONArray() : resp.optJSONArray("tool_trace"))
                        .put("search_execution", resp.optJSONObject("search_execution") == null ? new JSONObject() : resp.optJSONObject("search_execution"))
                        .put("retrieval_quality", resp.optJSONObject("retrieval_quality") == null ? new JSONObject() : resp.optJSONObject("retrieval_quality"))
                        .put("structured_data", resp.optBoolean("structured_data", false));
                sse.append("data: ").append(sourcesEvt).append("\n\n");

                String answer = resp.optString("answer", "");
                for (int i = 0; i < answer.length(); i += 60) {
                    sse.append("data: ").append(new JSONObject()
                            .put("type", "chunk")
                            .put("content", answer.substring(i, Math.min(answer.length(), i + 60)))).append("\n\n");
                }

                sse.append("data: ").append(new JSONObject()
                        .put("type", "done")
                        .put("response_time", resp.optInt("response_time", 0))).append("\n\n");

                byte[] payload_bytes = sse.toString().getBytes(StandardCharsets.UTF_8);
                response.setContentLengthLong(payload_bytes.length);
                outputStream.write(payload_bytes);
                outputStream.flush();
            } catch (Exception e) {
                // 响应未提交时以错误事件收尾；若已提交则只能中断（客户端按连接关闭处理）
                try {
                    byte[] err = ("data: " + new JSONObject()
                            .put("type", "error")
                            .put("error", e.getMessage() == null ? "服务暂时不可用" : e.getMessage()) + "\n\n")
                            .getBytes(StandardCharsets.UTF_8);
                    response.setContentLengthLong(err.length);
                    outputStream.write(err);
                    outputStream.flush();
                } catch (Exception ignored) {}
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

    public record RAGResponse(boolean success, String answer, List<String> sources, int responseTime, String model, String error) {
        public static RAGResponse success(String answer, List<String> sources, int responseTime, String model) {
            return new RAGResponse(true, answer, sources, responseTime, model, null);
        }
        public static RAGResponse error(String error) {
            return new RAGResponse(false, null, List.of(), 0, null, error);
        }
    }

    public record RAGStatsResponse(int documentCount, String embeddingModel, String llmModel) {}
}