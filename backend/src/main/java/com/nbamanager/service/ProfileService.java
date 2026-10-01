package com.nbamanager.service;

import com.nbamanager.domain.UserAccount;
import com.nbamanager.event.UserIdentityUpdatedEvent;
import com.nbamanager.exception.ApiException;
import com.nbamanager.repository.UserAccountRepository;
import com.nbamanager.util.ImageUploadValidator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024;

    private final UserAccountRepository userAccountRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final com.nbamanager.service.support.RedisWindowRateLimiter rateLimiter;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    public Map<String, String> uploadAvatar(Long userId, MultipartFile file) throws Exception {
        // 上传频控（安全报告 §十二 V5）：同用户 60s 内最多 10 次；Redis 不可用放行
        rateLimiter.check("nba:upload:rl:" + userId, 10, 60_000, "上传过于频繁，请稍后再试");

        String extension = ImageUploadValidator.validateImage(file, MAX_FILE_SIZE, "头像", "2MB");

        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path directory = root.resolve("avatars").normalize();
        Files.createDirectories(directory);

        String fileName = UUID.randomUUID() + "." + extension;
        file.transferTo(directory.resolve(fileName).toFile());

        String avatarUrl = "/uploads/avatars/" + fileName;
        UserAccount account = userAccountRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "用户不存在"));
        String oldAvatarUrl = account.getAvatarUrl();
        account.setAvatarUrl(avatarUrl);
        userAccountRepository.save(account);

        // 旧头像文件同步删除（安全报告 §十二 V5：此前从不删除，磁盘慢性泄漏）；
        // 放在落库成功之后，删除失败只告警不影响本次上传
        deleteOwnAvatarFile(oldAvatarUrl);

        // 事务提交后 AFTER_COMMIT 监听器才会广播
        eventPublisher.publishEvent(new UserIdentityUpdatedEvent(
                userId, account.getUsername(), avatarUrl));

        return Map.of("avatarUrl", avatarUrl);
    }

    @Transactional
    public java.util.Map<String, Object> resetAvatar(Long userId) {
        UserAccount account = userAccountRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "用户不存在"));
        String oldAvatarUrl = account.getAvatarUrl();
        account.setAvatarUrl(null);
        userAccountRepository.save(account);
        deleteOwnAvatarFile(oldAvatarUrl);

        eventPublisher.publishEvent(new UserIdentityUpdatedEvent(
                userId, account.getUsername(), null));

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("avatarUrl", null);
        return result;
    }

    /**
     * 删除该用户当前头像文件（安全报告 §十二 V5）。
     *
     * 双重校验后才允许删除：URL 必须指向 /uploads/avatars/，文件名必须是
     * 本服务生成的 UUID 格式——防止被改库的 avatarUrl（如 ../../、共享静态资源）
     * 借删除通道扩大成任意文件删除。
     */
    private void deleteOwnAvatarFile(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return;
        }
        try {
            if (!avatarUrl.startsWith("/uploads/avatars/")) {
                return;
            }
            String fileName = avatarUrl.substring("/uploads/avatars/".length());
            // UUID.randomUUID().toString() 形如 36 位 8-4-4-4-12；扩展名 ≤5 字符
            if (!fileName.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.[A-Za-z0-9]{1,5}")) {
                log.warn("头像文件名非本服务 UUID 格式，跳过删除: {}", avatarUrl);
                return;
            }
            Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path target = root.resolve("avatars").resolve(fileName).normalize();
            if (!target.startsWith(root.resolve("avatars"))) {
                return;
            }
            Files.deleteIfExists(target);
        } catch (Exception e) {
            log.warn("旧头像文件删除失败（不影响账户更新）: {} error={}", avatarUrl, e.getMessage());
        }
    }
}
