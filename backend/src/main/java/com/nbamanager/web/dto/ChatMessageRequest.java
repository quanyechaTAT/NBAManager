package com.nbamanager.web.dto;

import com.nbamanager.domain.ChatMessageType;

public record ChatMessageRequest(
        String clientMessageId,
        ChatMessageType type,
        String content,
        String imageUrl
) {}
