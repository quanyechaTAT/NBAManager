package com.nbamanager.service;

import com.nbamanager.security.UserPrincipal;
import com.nbamanager.repository.UserAccountRepository;
import com.nbamanager.web.dto.ChatPresenceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatEventListener {

    private final ChatPresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatRedisPubSub chatRedisPubSub;
    private final UserAccountRepository userAccountRepository;

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        String sessionId = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        log.debug("WebSocket会话已连接: {}", sessionId);
    }

    @EventListener
    public void handleSessionSubscribe(SessionSubscribeEvent event) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        if (destination == null) return;

        // 只处理精确的聊天消息主题，不处理 /presence 子主题
        String gameId = extractChatRoomGameId(destination);
        if (gameId == null) return;

        UserPrincipal user = extractUser(event.getUser());
        if (user == null) {
            log.warn("STOMP SUBSCRIBE 未认证: destination={}", destination);
            return;
        }

        String sessionId = accessor.getSessionId();
        if (sessionId == null) return;

        String avatarUrl = userAccountRepository.findById(user.getId())
                .map(account -> account.getAvatarUrl())
                .orElse(null);
        presenceService.userJoined(sessionId, gameId, user.getId(),
                user.getUsername(), avatarUrl, user.getRole());

        broadcastPresence(gameId);
        log.info("STOMP SUBSCRIBE: sessionId={}, user={}, gameId={}", sessionId, user.getUsername(), gameId);
    }

    @EventListener
    public void handleSessionUnsubscribe(SessionUnsubscribeEvent event) {
        String sessionId = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        if (sessionId == null) return;

        var info = presenceService.getSession(sessionId);
        if (info != null) {
            String gameId = info.gameId();
            presenceService.userLeft(sessionId);
            broadcastPresence(gameId);
            log.debug("用户离开聊天室(取消订阅): gameId={}, user={}", gameId, info.username());
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        if (sessionId == null) return;

        var info = presenceService.getSession(sessionId);
        if (info != null) {
            String gameId = info.gameId();
            presenceService.userLeft(sessionId);
            broadcastPresence(gameId);
            log.debug("用户断开连接: gameId={}, user={}", gameId, info.username());
        }
    }

    private void broadcastPresence(String gameId) {
        ChatPresenceResponse presence = presenceService.getPresence(gameId);
        messagingTemplate.convertAndSend("/topic/chat/" + gameId + "/presence", presence);
        chatRedisPubSub.publishPresence(gameId, presence);
    }

    private UserPrincipal extractUser(Principal principal) {
        if (principal instanceof UserPrincipal up) return up;
        if (principal instanceof UsernamePasswordAuthenticationToken auth) {
            Object p = auth.getPrincipal();
            if (p instanceof UserPrincipal up) return up;
        }
        return null;
    }

    /** 只匹配精确的 /topic/chat/{gameId}，不匹配 /topic/chat/{gameId}/presence 等子主题 */
    private String extractChatRoomGameId(String destination) {
        if (!destination.startsWith("/topic/chat/")) return null;
        String rest = destination.substring("/topic/chat/".length());
        if (rest.isBlank() || rest.contains("/")) return null;
        return rest;
    }
}
