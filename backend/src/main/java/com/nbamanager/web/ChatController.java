package com.nbamanager.web;

import com.nbamanager.security.UserPrincipal;
import com.nbamanager.service.ChatMessageService;
import com.nbamanager.service.ChatPresenceService;
import com.nbamanager.exception.ApiException;
import com.nbamanager.util.ImageUploadValidator;
import com.nbamanager.web.dto.ChatMessageResponse;
import com.nbamanager.web.dto.ChatPresenceResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatMessageService chatMessageService;
    private final ChatPresenceService chatPresenceService;
    private final WebSocketController webSocketController;
    private final com.nbamanager.service.support.RedisWindowRateLimiter rateLimiter;

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    /**
     * 合法比赛 ID：纯数字，9 位（ESPN，如 401881722）或 10 位（NBA 官方，如 0042500204）。
     *
     * 该形状来自库中真实数据（match_records.nba_game_id 实测只有 9 位和 10 位两种长度），
     * 之前用的是 [A-Za-z0-9_-]{1,80}，过宽：CON / 0 / -1 / 1e9 等都会被接受并在
     * uploads/chat/{gameId}/ 下落盘（安全报告 §九 S4-R2~R5）。
     *
     * 注意这里限定 9~10 位而不是 \d{1,12}：后者仍会放行 "0" 这类单字符输入，
     * 而验收要求 gameId=0 必须被拒。长度与真实 ID 对齐后，
     * CON / 0 / -1 / 1e9 全部落到 400。
     */
    private static final String SAFE_GAME_ID = "\\d{9,10}";

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @GetMapping("/games/{gameId}/messages")
    public ResponseEntity<Map<String, Object>> getHistory(
            @PathVariable String gameId,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "50") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        List<ChatMessageResponse> messages = chatMessageService.getHistory(gameId, beforeId, safeLimit);
        boolean hasMore = messages.size() == safeLimit;
        return ResponseEntity.ok(Map.of(
                "messages", messages,
                "hasMore", hasMore
        ));
    }

    @GetMapping("/games/{gameId}/presence")
    public ResponseEntity<ChatPresenceResponse> getPresence(@PathVariable String gameId) {
        return ResponseEntity.ok(chatPresenceService.getPresence(gameId));
    }

    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<Map<String, Object>> deleteMessage(
            @PathVariable Long messageId,
            @AuthenticationPrincipal UserPrincipal user) {
        if (user.getRole() != com.nbamanager.domain.Role.ADMIN) {
            return ResponseEntity.status(403).build();
        }
        ChatMessageResponse response = chatMessageService.softDelete(messageId, user.getId());
        // Broadcast delete to all connected clients
        webSocketController.broadcastMessageDeleted(response.gameId(), messageId);
        return ResponseEntity.ok(Map.of("success", true, "message", response));
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("gameId") String gameId,
            @AuthenticationPrincipal UserPrincipal user) {
        // 上传频控（安全报告 §十二 V5）：同用户 60s 内最多 10 次；Redis 不可用放行
        rateLimiter.check("nba:upload:rl:" + user.getId(), 10, 60_000, "上传过于频繁，请稍后再试");
        String ext = ImageUploadValidator.validateImage(file, MAX_FILE_SIZE, "聊天图片", "5MB");
        String safeGameId = validateGameId(gameId);

        String fileName = UUID.randomUUID() + "." + ext;
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path dir = root.resolve("chat").resolve(safeGameId).normalize();
        if (!dir.startsWith(root)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "无效的比赛 ID");
        }

        Path filePath = dir.resolve(fileName).normalize();
        if (!filePath.startsWith(dir)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "无效的文件名");
        }
        try {
            Files.createDirectories(dir);
            file.transferTo(filePath.toFile());
        } catch (IOException | IllegalStateException e) {
            log.warn("聊天图片上传失败: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_REQUEST, "聊天图片上传失败");
        }

        String url = "/uploads/chat/" + safeGameId + "/" + fileName;
        return ResponseEntity.ok(Map.of("url", url));
    }

    private String validateGameId(String gameId) {
        if (gameId == null || !gameId.matches(SAFE_GAME_ID)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "无效的比赛 ID");
        }
        return gameId;
    }
}
