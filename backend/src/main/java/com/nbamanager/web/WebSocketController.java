package com.nbamanager.web;

import com.nbamanager.repository.UserAccountRepository;
import com.nbamanager.security.UserPrincipal;
import com.nbamanager.service.ChatMessageService;
import com.nbamanager.service.ChatPresenceService;
import com.nbamanager.web.dto.ChatMessageRequest;
import com.nbamanager.web.dto.ChatMessageResponse;
import java.security.Principal;
import java.util.Map;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageService chatMessageService;
    private final UserAccountRepository userAccountRepository;
    private final com.nbamanager.service.ChatRedisPubSub chatRedisPubSub;
    private final SimpUserRegistry simpUserRegistry;

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
            sendChatError(user, Map.of("error", "RATE_LIMITED", "message", "发送过于频繁，请稍后再试",
                    "clientMessageId", payload.clientMessageId() != null ? payload.clientMessageId() : ""));
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
            sendChatError(user, Map.of("error", "INVALID_MESSAGE", "message", e.getMessage(),
                    "clientMessageId", payload.clientMessageId() != null ? payload.clientMessageId() : ""));
        } catch (Exception e) {
            log.error("处理聊天消息失败: {}", e.getMessage(), e);
            sendChatError(user, Map.of("error", "INTERNAL_ERROR", "message", "消息发送失败，请重试",
                    "clientMessageId", payload.clientMessageId() != null ? payload.clientMessageId() : ""));
        }
    }

    /**
     * 给当前用户回执（限流/参数错误/内部错误）。
     *
     * 这里刻意不走 {@code convertAndSendToUser}：
     * UserDestinationMessageHandler 会把 /user 目的地重新派发一次，而该消息在
     * OrderedMessageChannelDecorator（由 setPreserveReceiveOrder(true) 安装）里会触发
     * Spring 的 "Expected mutable SimpMessageHeaderAccessor" 断言失败并刷 ERROR
     * （压测报告记录 6241 条，占日志 2.2%）。实测：客户端发送后立刻断开时必现。
     *
     * 改为直接投递到 UserDestinationMessageHandler 解析后本就会使用的目标
     * {@code /queue/chat-errors-user<sessionId>}（broker 已注册 /queue），
     * 既保留回执能力，又绕开该缺陷路径；同时天然跳过已断开的会话（无订阅者）。
     */
    private void sendChatError(UserPrincipal user, Map<String, String> payload) {
        SimpUser simpUser;
        try {
            simpUser = simpUserRegistry.getUser(user.getUsername());
        } catch (Exception e) {
            log.debug("查询在线用户失败，跳过回执: username={}, error={}", user.getUsername(), e.getMessage());
            return;
        }
        if (simpUser == null) {
            log.debug("用户已离线，跳过回执: username={}, error={}", user.getUsername(), payload.get("error"));
            return;
        }
        for (SimpSession session : simpUser.getSessions()) {
            messagingTemplate.convertAndSend("/queue/chat-errors-user" + session.getId(), payload);
        }
    }

    /**
     * STOMP 处理异常的统一回执。
     *
     * 注意：这里【不能】用 @SendToUser("/queue/chat-errors")。
     * SendToMethodReturnValueHandler 会改用 convertAndSendToUser 发送，消息随后被
     * UserDestinationMessageHandler 对 /user 目的地做二次派发；而
     * WebSocketConfig.setPreserveReceiveOrder(true) 安装的 OrderedMessageChannelDecorator
     * 在 setTaskHeader() 中断言消息头必须是"可变 SimpMessageHeaderAccessor"，
     * 二次派发用的是不可变访问器，于是每条回执按该用户在线会话数抛
     * "Expected mutable SimpMessageHeaderAccessor" ERROR
     * （安全报告 §九 S1：10 会话 x 10 非法帧 = 100 条）。
     *
     * 因此改为与 sendChatError() 一致的直投：逐个在线会话发送到
     * /queue/chat-errors-user<sessionId>（broker 已注册 /queue），彻底绕开二次派发；
     * 用户已离线则跳过，不产生任何投递尝试。
     */
    @MessageExceptionHandler
    public void handleException(Exception ex, Principal principal) {
        UserPrincipal user = extractUser(principal);
        if (user == null) {
            log.warn("STOMP 处理异常但无法识别用户，跳过回执: {}", ex.getMessage());
            return;
        }
        String message = ex.getMessage() != null ? ex.getMessage() : "未知错误";
        sendChatError(user, Map.of("error", "EXCEPTION", "message", message));
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
