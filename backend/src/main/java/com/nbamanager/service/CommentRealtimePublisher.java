package com.nbamanager.service;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CommentRealtimePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publish(CommentService.CommentTarget target, String action) {
        String destination = target.gameId() != null
                ? "/topic/comments/game/" + target.gameId()
                : "/topic/comments/post/" + target.postId();
        messagingTemplate.convertAndSend(destination, Map.of("type", "COMMENT_UPDATED", "action", action));
    }
}
