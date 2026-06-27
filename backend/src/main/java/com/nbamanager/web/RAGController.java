package com.nbamanager.web;

import com.nbamanager.domain.RAGConversation;
import com.nbamanager.security.UserPrincipal;
import com.nbamanager.service.RAGConversationService;
import com.nbamanager.service.RAGService;
import com.nbamanager.service.RAGService.RAGResult;
import com.nbamanager.service.RAGService.RAGStats;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * RAG 智能问答接口
 */
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RAGController {

    private final RAGService ragService;
    private final RAGConversationService conversationService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private static final String RAG_SERVER_URL = "http://127.0.0.1:8899";

    /**
     * 智能问答（非流式）
     */
    @GetMapping("/query")
    public ResponseEntity<RAGResponse> query(
            @RequestParam String question,
            @RequestParam(defaultValue = "5") int topK) {
        RAGResult result = ragService.query(question, topK);
        if (result.success()) {
            return ResponseEntity.ok(RAGResponse.success(result.answer(), result.sources(), result.responseTime(), result.model()));
        } else {
            return ResponseEntity.ok(RAGResponse.error(result.error()));
        }
    }

    /**
     * 智能问答（流式 SSE）
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public StreamingResponseBody streamQuery(
            @RequestParam String question,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(required = false) String sessionId,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setContentType("text/event-stream;charset=utf-8");

        return outputStream -> {
            try {
                ragService.ensureServerStarted();
                StringBuilder urlBuilder = new StringBuilder(RAG_SERVER_URL + "/stream?question=");
                urlBuilder.append(java.net.URLEncoder.encode(question, StandardCharsets.UTF_8));
                urlBuilder.append("&top_k=").append(topK);
                if (sessionId != null && !sessionId.isEmpty()) {
                    urlBuilder.append("&session_id=").append(sessionId);
                }
                URL url = new URL(urlBuilder.toString());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(120000);

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6);
                            outputStream.write("data: ".getBytes(StandardCharsets.UTF_8));
                            outputStream.write(data.getBytes(StandardCharsets.UTF_8));
                            outputStream.write("\n\n".getBytes(StandardCharsets.UTF_8));
                            outputStream.flush();
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                try {
                    String errorEvent = "data: {\"type\":\"error\",\"error\":\"" + e.getMessage() + "\"}\n\n";
                    outputStream.write(errorEvent.getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                } catch (Exception ignored) {}
            }
        };
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
        boolean success = ragService.rebuildIndex();
        return ResponseEntity.ok(Map.of("status", success ? "success" : "error", "message", success ? "索引重建完成" : "索引重建失败"));
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
