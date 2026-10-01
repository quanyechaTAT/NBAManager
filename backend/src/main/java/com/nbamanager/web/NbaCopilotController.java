package com.nbamanager.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.nbamanager.security.UserPrincipal;
import com.nbamanager.service.CopilotConversationAccessService;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * NBA Copilot 代理。
 *
 * 会话隔离（安全报告 §十二 V1 / 任务书 P0-1）：python 端是全局单用户内存存储，
 * 归属建在 java 层（user_copilot_conversations）——
 * - chat 产生/续用的会话登记为当前用户所有（续用他人 conversationId → 404）；
 * - conversations 列表按归属过滤；
 * - get/delete 校验归属，非本人一律 404（不泄露存在性，与通知 IDOR 处理一致）；
 * - 存量无归属会话迁移期仅 ADMIN 可见/可删（见 CopilotConversationAccessService）。
 */
@RestController
@RequestMapping("/api/nba-copilot")
@Slf4j
@RequiredArgsConstructor
public class NbaCopilotController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_MESSAGE_CHARS = 2_000;
    private static final int MAX_BODY_CHARS = 16_384;

    private final com.nbamanager.service.CopilotService copilotService;
    private final CopilotConversationAccessService conversationAccess;

    /** Copilot 服务地址，可用环境变量 COPILOT_BASE_URL 或配置项 app.copilot.base-url 覆盖 */
    @org.springframework.beans.factory.annotation.Value("${app.copilot.base-url:${COPILOT_BASE_URL:http://127.0.0.1:18998/api/nba-copilot}}")
    private String copilotUrl;

    @PostMapping("/chat")
    public ResponseEntity<String> chat(@RequestBody(required = false) String body,
                                       @AuthenticationPrincipal UserPrincipal user) {
        if (user == null) {
            return jsonError(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }
        ResponseEntity<String> invalid = validateChatRequest(body);
        if (invalid != null) {
            return invalid;
        }
        // 续用已有会话必须归属本人（否则可借他人 conversationId 污染/读取他人上下文）
        String requestedId = extractConversationId(body);
        if (requestedId != null
                && !conversationAccess.accessible(user.getId(), isAdmin(user), requestedId)) {
            return jsonError(HttpStatus.NOT_FOUND, "会话不存在");
        }
        copilotService.ensureServerStarted();
        ResponseEntity<String> response = proxyPost(copilotUrl + "/chat", body);
        registerOwnershipFromResponse(user, response);
        return response;
    }

    @GetMapping("/conversations")
    public ResponseEntity<String> listConversations(@AuthenticationPrincipal UserPrincipal user) {
        if (user == null) {
            return jsonError(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }
        copilotService.ensureServerStarted();
        String raw = proxyGet(copilotUrl + "/conversations");
        return ResponseEntity.ok(filterConversationsForUser(user, raw));
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<String> getConversation(@PathVariable String id,
                                                  @AuthenticationPrincipal UserPrincipal user) {
        if (user == null) {
            return jsonError(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }
        if (!conversationAccess.accessible(user.getId(), isAdmin(user), id)) {
            return jsonError(HttpStatus.NOT_FOUND, "会话不存在");
        }
        copilotService.ensureServerStarted();
        return ResponseEntity.ok(proxyGet(copilotUrl + "/conversations/" + id));
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<String> deleteConversation(@PathVariable String id,
                                                     @AuthenticationPrincipal UserPrincipal user) {
        if (user == null) {
            return jsonError(HttpStatus.UNAUTHORIZED, "未登录或登录已过期");
        }
        if (!conversationAccess.accessible(user.getId(), isAdmin(user), id)) {
            return jsonError(HttpStatus.NOT_FOUND, "会话不存在");
        }
        copilotService.ensureServerStarted();
        ResponseEntity<String> response = proxyDelete(copilotUrl + "/conversations/" + id);
        if (response.getStatusCode().is2xxSuccessful()) {
            // python 端已删除，归属映射同步清理（ADMIN 删存量会话时本就无映射，幂等）
            conversationAccess.remove(user.getId(), id);
        }
        return response;
    }

    // ==================== 会话归属辅助 ====================

    /** chat 成功后把响应中的 conversationId 登记为当前用户所有（登记失败不影响本次回答）。 */
    private void registerOwnershipFromResponse(UserPrincipal user, ResponseEntity<String> response) {
        try {
            if (user == null || !response.getStatusCode().is2xxSuccessful()
                    || response.getBody() == null || response.getBody().isBlank()) {
                return;
            }
            String conversationId = extractConversationId(response.getBody());
            if (conversationId != null && !conversationId.isBlank()) {
                conversationAccess.register(user.getId(), conversationId);
            }
        } catch (Exception e) {
            log.warn("Copilot 会话归属登记失败（不影响回答）: userId={}, error={}",
                    user == null ? null : user.getId(), e.getMessage());
        }
    }

    private String filterConversationsForUser(UserPrincipal user, String raw) {
        Set<String> visible = new HashSet<>(conversationAccess.ownedIds(user.getId()));
        if (isAdmin(user)) {
            // ADMIN 额外可见"存量无归属"会话（迁移期兜底）；他人已归属会话仍不可见
            Set<String> mapped = conversationAccess.allMappedIds();
            for (JsonNode node : parseArray(raw)) {
                String id = node.path("id").asText(null);
                if (id != null && !mapped.contains(id)) {
                    visible.add(id);
                }
            }
        }
        ArrayNode filtered = OBJECT_MAPPER.createArrayNode();
        for (JsonNode node : parseArray(raw)) {
            String id = node.path("id").asText(null);
            if (id != null && visible.contains(id)) {
                filtered.add(node);
            }
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(filtered);
        } catch (Exception e) {
            return "[]";
        }
    }

    private JsonNode parseArray(String raw) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(raw == null || raw.isBlank() ? "[]" : raw);
            return node.isArray() ? node : OBJECT_MAPPER.createArrayNode();
        } catch (Exception e) {
            return OBJECT_MAPPER.createArrayNode();
        }
    }

    /** 从请求/响应 JSON 中取 conversationId；无或非文本返回 null。 */
    static String extractConversationId(String json) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            JsonNode id = node.get("conversationId");
            return id != null && id.isTextual() ? id.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isAdmin(UserPrincipal user) {
        return user != null && user.getAuthorities() != null && user.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    ResponseEntity<String> proxyPost(String urlStr, String body) {
        try {
            URL url = new URI(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(60000);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int status = conn.getResponseCode();
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(readResponseBody(conn, status));
        } catch (Exception e) {
            log.error("Copilot proxy error: {}", e.getMessage());
            return jsonError(HttpStatus.BAD_GATEWAY, "Copilot service unavailable");
        }
    }

    String proxyGet(String urlStr) {
        try {
            URL url = new URI(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(30000);
            return readResponseBody(conn, conn.getResponseCode());
        } catch (Exception e) {
            log.error("Copilot proxy error: {}", e.getMessage());
            return "{\"error\": \"Copilot service unavailable\"}";
        }
    }

    ResponseEntity<String> proxyDelete(String urlStr) {
        try {
            URL url = new URI(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(30000);
            return ResponseEntity.status(conn.getResponseCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(readResponseBody(conn, conn.getResponseCode()));
        } catch (Exception e) {
            log.error("Copilot proxy error: {}", e.getMessage());
            return jsonError(HttpStatus.BAD_GATEWAY, "Copilot service unavailable");
        }
    }

    private ResponseEntity<String> validateChatRequest(String body) {
        if (body == null || body.isBlank()) {
            return jsonError(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        if (body.length() > MAX_BODY_CHARS) {
            return jsonError(HttpStatus.PAYLOAD_TOO_LARGE, "请求体过大");
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode message = root.get("message");
            if (message == null || !message.isTextual() || message.asText().isBlank()) {
                return jsonError(HttpStatus.BAD_REQUEST, "message 不能为空");
            }
            if (message.asText().length() > MAX_MESSAGE_CHARS) {
                return jsonError(HttpStatus.PAYLOAD_TOO_LARGE, "message 不能超过 2000 字符");
            }
        } catch (Exception e) {
            return jsonError(HttpStatus.BAD_REQUEST, "请求体格式错误");
        }
        return null;
    }

    private static ResponseEntity<String> jsonError(HttpStatus status, String message) {
        try {
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(OBJECT_MAPPER.writeValueAsString(Map.of("error", message)));
        } catch (Exception e) {
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"" + message + "\"}");
        }
    }

    private String readResponseBody(HttpURLConnection conn, int status) throws Exception {
        InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        if (stream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        return sb.toString();
    }
}
