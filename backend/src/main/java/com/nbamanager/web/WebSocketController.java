package com.nbamanager.web;

import com.nbamanager.repository.UserAccountRepository;
import com.nbamanager.security.UserPrincipal;
import com.nbamanager.service.ChatMessageService;
import com.nbamanager.service.ChatPresenceService;
import com.nbamanager.web.dto.ChatMessageRequest;
import com.nbamanager.web.dto.ChatMessageResponse;
import java.security.Principal;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageService chatMessageService;
    private final UserAccountRepository userAccountRepository;
    private final com.nbamanager.service.ChatRedisPubSub chatRedisPubSub;

    /**
     * 接收客户端发送的聊天消息
     * 前端通过 STOMP 发送到 /app/chat/{gameId}
     */
    @MessageMapping("/chat/{gameId}")
    public void handleChatMessage(
            @DestinationVariable String gameId,
            ChatMessageRequest payload,
            Principal principal) {

        UserPrincipal user = extractUser(principal);
        if (user == null) {
            log.warn("未认证用户尝试发送消息: principal={}", principal != null ? principal.getName() : null);
            return;
        }

        log.info("Chat SEND received: gameId={}, userId={}, clientMessageId={}",
                gameId, user.getId(), payload.clientMessageId());

        // Rate limit check
        if (chatMessageService.isRateLimited(user.getId())) {
            messagingTemplate.convertAndSendToUser(
                    user.getUsername(), "/queue/chat-errors",
                    Map.of("error", "RATE_LIMITED", "message", "发送过于频繁，请稍后再试"));
            return;
        }

        // Lookup avatar from user account
        String avatarUrl = userAccountRepository.findById(user.getId())
                .map(acc -> acc.getAvatarUrl()).orElse(null);

        try {
            com.nbamanager.domain.ChatMessage saved = chatMessageService.save(user, gameId, payload, avatarUrl);

            ChatMessageResponse response = new ChatMessageResponse(
                    saved.getId(), saved.getClientMessageId(), saved.getGameId(),
                    saved.getUserId(), saved.getUsername(), saved.getAvatarUrl(),
                    user.getRole(), saved.getMessageType(), saved.isDeleted() ? null : saved.getContent(),
                    saved.isDeleted() ? null : saved.getImageUrl(), saved.getCreatedAt(), saved.isDeleted());

            messagingTemplate.convertAndSend("/topic/chat/" + gameId, response);
            chatRedisPubSub.publishMessage(gameId, response);
            log.info("Chat MESSAGE broadcasted: gameId={}, messageId={}, userId={}, clientMessageId={}",
                    gameId, saved.getId(), user.getId(), saved.getClientMessageId());
        } catch (IllegalArgumentException e) {
            messagingTemplate.convertAndSendToUser(
                    user.getUsername(), "/queue/chat-errors",
                    Map.of("error", "INVALID_MESSAGE", "message", e.getMessage(),
                            "clientMessageId", payload.clientMessageId() != null ? payload.clientMessageId() : ""));
        } catch (Exception e) {
            log.error("处理聊天消息失败: {}", e.getMessage(), e);
            messagingTemplate.convertAndSendToUser(
                    user.getUsername(), "/queue/chat-errors",
                    Map.of("error", "INTERNAL_ERROR", "message", "消息发送失败，请重试",
                            "clientMessageId", payload.clientMessageId() != null ? payload.clientMessageId() : ""));
        }
    }

    @MessageExceptionHandler
    @SendToUser("/queue/chat-errors")
    public Map<String, String> handleException(Exception ex) {
        return Map.of("error", "EXCEPTION", "message", ex.getMessage() != null ? ex.getMessage() : "未知错误");
    }

    /** 广播消息删除事件 */
    public void broadcastMessageDeleted(String gameId, Long messageId) {
        messagingTemplate.convertAndSend("/topic/chat/" + gameId,
                Map.of("type", "MESSAGE_DELETED", "messageId", messageId));
        chatRedisPubSub.publishMessageDeleted(gameId, messageId);
    }

    private UserPrincipal extractUser(Principal principal) {
        if (principal instanceof UserPrincipal up) return up;
        if (principal instanceof UsernamePasswordAuthenticationToken auth) {
            Object p = auth.getPrincipal();
            if (p instanceof UserPrincipal up) return up;
        }
        return null;
    }

    // ===== 以下为其他模块使用的广播方法（保留兼容） =====

    /** 广播比分更新到 /topic/scores */
    public void broadcastScoreUpdate(Object scoreData) {
        messagingTemplate.convertAndSend("/topic/scores", scoreData);
        log.debug("广播比分更新");
    }

    /** 广播新闻到 /topic/news */
    public void broadcastNews(Object newsData) {
        messagingTemplate.convertAndSend("/topic/news", newsData);
        log.debug("广播新闻更新");
    }

    /** 广播实时赛况到 /topic/playbyplay/{gameId} */
    public void broadcastPlayByPlay(String gameId, Object eventData) {
        messagingTemplate.convertAndSend("/topic/playbyplay/" + gameId, eventData);
    }
}
