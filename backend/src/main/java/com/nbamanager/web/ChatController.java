package com.nbamanager.web;

import com.nbamanager.security.UserPrincipal;
import com.nbamanager.service.ChatMessageService;
import com.nbamanager.service.ChatPresenceService;
import com.nbamanager.web.dto.ChatMessageResponse;
import com.nbamanager.web.dto.ChatPresenceResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatMessageService chatMessageService;
    private final ChatPresenceService chatPresenceService;
    private final WebSocketController webSocketController;

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    @Value("${app.upload.dir:uploads/chat}")
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
            @AuthenticationPrincipal UserPrincipal user) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件为空"));
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件大小超过5MB限制"));
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "无效的文件名"));
        }

        String ext = getExtension(originalName).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            return ResponseEntity.badRequest().body(Map.of("error", "仅支持 jpg/jpeg/png/webp/gif 格式"));
        }

        // Validate MIME type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "无效的文件类型"));
        }

        String fileName = UUID.randomUUID() + "." + ext;
        Path dir = Paths.get(uploadDir, gameId);
        Files.createDirectories(dir);
        Path filePath = dir.resolve(fileName);
        file.transferTo(filePath.toFile());

        String url = "/uploads/chat/" + gameId + "/" + fileName;
        return ResponseEntity.ok(Map.of("url", url));
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }
}
