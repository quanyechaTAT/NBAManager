package com.nbamanager.web;

import com.nbamanager.repository.UserAccountRepository;
import com.nbamanager.security.UserPrincipal;
import com.nbamanager.service.ChatPresenceService;
import com.nbamanager.service.ChatRedisPubSub;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024;

    private final UserAccountRepository userAccountRepository;
    private final ChatPresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRedisPubSub chatRedisPubSub;

    @Value("${app.upload.dir:uploads/chat}")
    private String uploadDir;

    @PostMapping("/avatar")
    public ResponseEntity<Map<String, String>> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal user) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("error", "头像文件不能为空且不能超过2MB"));
        }
        String originalName = file.getOriginalFilename();
        String extension = originalName == null ? "" : getExtension(originalName).toLowerCase();
        String contentType = file.getContentType();
        if (!ALLOWED_EXTENSIONS.contains(extension)
                || contentType == null
                || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "仅支持 jpg、jpeg、png、webp、gif 图片"));
        }

        Path directory = Paths.get(uploadDir, "avatars").toAbsolutePath().normalize();
        Files.createDirectories(directory);
        String fileName = UUID.randomUUID() + "." + extension;
        file.transferTo(directory.resolve(fileName).toFile());

        String avatarUrl = "/uploads/avatars/" + fileName;
        var account = userAccountRepository.findById(user.getId()).orElseThrow();
        account.setAvatarUrl(avatarUrl);
        userAccountRepository.save(account);
        for (String gameId : presenceService.updateUserAvatar(user.getId(), avatarUrl)) {
            var presence = presenceService.getPresence(gameId);
            messagingTemplate.convertAndSend("/topic/chat/" + gameId + "/presence", presence);
            chatRedisPubSub.publishPresence(gameId, presence);
        }
        return ResponseEntity.ok(Map.of("avatarUrl", avatarUrl));
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1) : "";
    }
}
