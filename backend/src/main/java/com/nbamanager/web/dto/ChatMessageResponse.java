package com.nbamanager.web.dto;

import com.nbamanager.domain.ChatMessageType;
import com.nbamanager.domain.Role;
import java.time.Instant;

public record ChatMessageResponse(
        Long id,
        String clientMessageId,
        String gameId,
        Long userId,
        String username,
        String avatarUrl,
        Role role,
        ChatMessageType type,
        String content,
        String imageUrl,
        Instant createdAt,
        boolean deleted
) {}
