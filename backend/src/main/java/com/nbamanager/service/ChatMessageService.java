package com.nbamanager.service;

import com.nbamanager.domain.ChatMessage;
import com.nbamanager.domain.ChatMessageType;
import com.nbamanager.repository.ChatMessageRepository;
import com.nbamanager.security.UserPrincipal;
import com.nbamanager.web.dto.ChatMessageRequest;
import com.nbamanager.web.dto.ChatMessageResponse;
import java.time.Instant;
import java.util.List;
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
    private static final int MAX_CONTENT_LENGTH = 500;
    private static final int RATE_LIMIT_COUNT = 5;
    private static final int RATE_LIMIT_SECONDS = 10;
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cc}\\p{Cf}]");

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
        }
        // Duplicate check
        if (req.clientMessageId() != null && !req.clientMessageId().isBlank()) {
            var existing = repo.findByUserIdAndClientMessageId(user.getId(), req.clientMessageId());
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        ChatMessage msg = new ChatMessage();
        msg.setGameId(gameId);
        msg.setUserId(user.getId());
        msg.setUsername(user.getUsername());
        msg.setAvatarUrl(avatarUrl);
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
        return messages.stream().map(this::toResponse).toList();
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
        String cleaned = CONTROL_CHARS.matcher(content).replaceAll("").trim();
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        if (cleaned.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("消息内容超过" + MAX_CONTENT_LENGTH + "字符限制");
        }
        return cleaned;
    }

    public ChatMessageResponse toResponse(ChatMessage msg) {
        return new ChatMessageResponse(
                msg.getId(),
                msg.getClientMessageId(),
                msg.getGameId(),
                msg.getUserId(),
                msg.getUsername(),
                msg.getAvatarUrl(),
                null, // role will be enriched at broadcast time
                msg.getMessageType(),
                msg.isDeleted() ? null : msg.getContent(),
                msg.isDeleted() ? null : msg.getImageUrl(),
                msg.getCreatedAt(),
                msg.isDeleted()
        );
    }
}
