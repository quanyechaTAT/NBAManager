package com.nbamanager.service;

import com.nbamanager.domain.ChatMessage;
import com.nbamanager.domain.ChatMessageType;
import com.nbamanager.repository.ChatMessageRepository;
import com.nbamanager.repository.UserAccountRepository;
import com.nbamanager.security.UserPrincipal;
import com.nbamanager.web.dto.ChatMessageRequest;
import com.nbamanager.web.dto.ChatMessageResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMessageService {

    private final ChatMessageRepository repo;
    private final UserAccountRepository userAccountRepository;
    private final org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;
    private static final int MAX_CONTENT_LENGTH = 500;
    private static final int RATE_LIMIT_COUNT = 5;
    private static final int RATE_LIMIT_SECONDS = 10;
    private static final String RATE_LIMIT_KEY_PREFIX = "nba:chat:rl:";
    // 原子窗口计数：INCR + 首次 PEXPIRE。DB 计数在并发下存在读-判竞态，压测中可被穿透
    private static final String RATE_LIMIT_LUA =
            "local v = redis.call('INCR', KEYS[1]) " +
            "if v == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end " +
            "return v";
    private static final Pattern UNSAFE_CONTROL_CHARS = Pattern.compile("[\\p{Cc}\\p{Cf}&&[^\\n\\t]]");

    @Transactional
    public ChatMessage save(UserPrincipal user, String gameId, ChatMessageRequest req, String avatarUrl) {
        // Validate game ID format
        if (gameId == null || gameId.isBlank() || gameId.length() > 64) {
            throw new IllegalArgumentException("无效的比赛ID");
        }
        // Validate message type
        ChatMessageType type = req.type();
        if (type == null) {
            type = ChatMessageType.TEXT;
        }
        // Sanitize content
        String content = sanitizeContent(req.content(), type);
        String imageUrl = req.imageUrl();
        if (type == ChatMessageType.IMAGE) {
            if (imageUrl == null || imageUrl.isBlank()) {
                throw new IllegalArgumentException("图片消息必须包含图片URL");
            }
            // 白名单（安全报告 §十二 12.3 I4）：只允许本服务 /uploads/ 下的图片路径，
            // 拒绝 javascript:、外域跟踪 URL 与任意协议注入
            if (!imageUrl.startsWith("/uploads/")) {
                throw new IllegalArgumentException("图片URL仅允许本站 /uploads/ 路径");
            }
        }
        // Duplicate check
        if (req.clientMessageId() != null && !req.clientMessageId().isBlank()) {
            var existing = repo.findByUserIdAndClientMessageId(user.getId(), req.clientMessageId());
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        // Fetch current identity from account table (not from stale WebSocket Principal)
        var account = userAccountRepository.findById(user.getId()).orElse(null);
        String currentUsername = account != null ? account.getUsername() : user.getUsername();
        String currentAvatarUrl = account != null ? account.getAvatarUrl() : avatarUrl;

        ChatMessage msg = new ChatMessage();
        msg.setGameId(gameId);
        msg.setUserId(user.getId());
        msg.setUsername(currentUsername);
        msg.setAvatarUrl(currentAvatarUrl);
        msg.setMessageType(type);
        msg.setContent(content);
        msg.setImageUrl(imageUrl);
        msg.setClientMessageId(req.clientMessageId());
        msg.setCreatedAt(Instant.now());
        return repo.save(msg);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getHistory(String gameId, Long beforeId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        var page = PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "id"));
        List<ChatMessage> messages = repo.findHistory(gameId, beforeId, page);
        Set<Long> userIds = messages.stream().map(ChatMessage::getUserId).collect(Collectors.toSet());
        // 批量加载当前身份（用户名 + 头像），覆盖历史快照
        Map<Long, com.nbamanager.domain.UserAccount> identityMap = userAccountRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(
                        com.nbamanager.domain.UserAccount::getId,
                        account -> account));
        return messages.stream()
                .map(message -> {
                    com.nbamanager.domain.UserAccount account = identityMap.get(message.getUserId());
                    String currentUsername = account != null ? account.getUsername() : message.getUsername();
                    String currentAvatar = account != null && account.getAvatarUrl() != null ? account.getAvatarUrl() : null;
                    return toResponseWithIdentity(message, currentUsername, currentAvatar);
                })
                .toList();
    }

    @Transactional
    public ChatMessageResponse softDelete(Long messageId, Long deletedBy) {
        ChatMessage msg = repo.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("消息不存在"));
        msg.setDeleted(true);
        msg.setDeletedAt(Instant.now());
        msg.setDeletedBy(deletedBy);
        repo.save(msg);
        return toResponse(msg);
    }

    public boolean isRateLimited(Long userId) {
        // Redis 原子窗口（拒绝的消息也计入窗口，防刷更严）；Redis 不可用时回退 DB 计数
        try {
            Long count = stringRedisTemplate.execute(
                    new org.springframework.data.redis.core.script.DefaultRedisScript<>(RATE_LIMIT_LUA, Long.class),
                    java.util.List.of(RATE_LIMIT_KEY_PREFIX + userId),
                    String.valueOf(RATE_LIMIT_SECONDS * 1000));
            if (count != null) {
                return count > RATE_LIMIT_COUNT;
            }
        } catch (Exception e) {
            log.warn("聊天限流 Redis 不可用，回退 DB 计数: {}", e.getMessage());
        }
        Instant since = Instant.now().minusSeconds(RATE_LIMIT_SECONDS);
        long count = repo.countRecentByUser(userId, since);
        return count >= RATE_LIMIT_COUNT;
    }

    private String sanitizeContent(String content, ChatMessageType type) {
        if (type == ChatMessageType.IMAGE) {
            return content == null ? "" : content.trim();
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        // Remove control characters
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        String cleaned = UNSAFE_CONTROL_CHARS.matcher(normalized).replaceAll("");
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        if (cleaned.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("消息内容超过" + MAX_CONTENT_LENGTH + "字符限制");
        }
        return cleaned;
    }

    public ChatMessageResponse toResponse(ChatMessage msg) {
        return toResponse(msg, msg.getAvatarUrl());
    }

    private ChatMessageResponse toResponse(ChatMessage msg, String currentAvatarUrl) {
        return new ChatMessageResponse(
                msg.getId(), msg.getClientMessageId(), msg.getGameId(),
                msg.getUserId(), msg.getUsername(),
                currentAvatarUrl == null || currentAvatarUrl.isBlank() ? null : currentAvatarUrl,
                null, msg.getMessageType(),
                msg.isDeleted() ? null : msg.getContent(),
                msg.isDeleted() ? null : msg.getImageUrl(),
                msg.getCreatedAt(), msg.isDeleted());
    }

    private ChatMessageResponse toResponseWithIdentity(ChatMessage msg, String currentUsername, String currentAvatarUrl) {
        return new ChatMessageResponse(
                msg.getId(), msg.getClientMessageId(), msg.getGameId(),
                msg.getUserId(), currentUsername,
                currentAvatarUrl == null || currentAvatarUrl.isBlank() ? null : currentAvatarUrl,
                null, msg.getMessageType(),
                msg.isDeleted() ? null : msg.getContent(),
                msg.isDeleted() ? null : msg.getImageUrl(),
                msg.getCreatedAt(), msg.isDeleted());
    }
}
