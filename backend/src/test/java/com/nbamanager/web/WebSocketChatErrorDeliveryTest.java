package com.nbamanager.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.ChatMessageType;
import com.nbamanager.domain.Role;
import com.nbamanager.repository.UserAccountRepository;
import com.nbamanager.security.UserPrincipal;
import com.nbamanager.service.ChatMessageService;
import com.nbamanager.service.ChatPresenceService;
import com.nbamanager.web.dto.ChatMessageRequest;
import java.security.Principal;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;

/**
 * 聊天错误回执投递路径回归测试（压测报告 6241 条 ERROR 的直接来源）。
 *
 * 旧实现用 {@code convertAndSendToUser(..., "/queue/chat-errors")}：
 * UserDestinationMessageHandler 会二次派发 /user 目的地，消息在
 * OrderedMessageChannelDecorator（setPreserveReceiveOrder(true) 安装）里断言失败，
 * 抛 "Expected mutable SimpMessageHeaderAccessor" 并刷 ERROR；客户端也收不到回执。
 *
 * 现实现直接投递到该解析路径最终使用的目标 {@code /queue/chat-errors-user<sessionId>}。
 * 本测试锁定两个关键行为：
 * 1) 在线时按会话逐个投递，且目的地形状正确（与 Spring 解析结果一致）；
 * 2) 用户已离线时不再尝试投递（这正是离线压测噪声的来源）。
 */
@ExtendWith(MockitoExtension.class)
class WebSocketChatErrorDeliveryTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private ChatMessageService chatMessageService;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private ChatPresenceService chatPresenceService;
    @Mock private com.nbamanager.service.ChatRedisPubSub chatRedisPubSub;
    @Mock private SimpUserRegistry simpUserRegistry;
    @Mock private SimpUser simpUser;
    @Mock private SimpSession sessionA;
    @Mock private SimpSession sessionB;

    private WebSocketController controller;
    /** 生产链路中 STOMP 帧的 principal 是 UsernamePasswordAuthenticationToken（内含 UserPrincipal）。 */
    private Principal principal;

    @BeforeEach
    void setUp() {
        controller = new WebSocketController(messagingTemplate, chatMessageService,
                userAccountRepository, chatRedisPubSub, simpUserRegistry);
        UserPrincipal userPrincipal = new UserPrincipal(userAccount("tester"));
        principal = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                userPrincipal, null, userPrincipal.getAuthorities());
        // lenient：异常回执相关用例不经过限流分支，避免 UnnecessaryStubbing
        lenient().when(chatMessageService.isRateLimited(any())).thenReturn(true);
    }

    @Test
    void rateLimitedReceiptIsDeliveredToEverySessionQueue() {
        when(simpUserRegistry.getUser("tester")).thenReturn(simpUser);
        when(simpUser.getSessions()).thenReturn(Set.of(sessionA, sessionB));
        when(sessionA.getId()).thenReturn("sess-a");
        when(sessionB.getId()).thenReturn("sess-b");

        controller.handleChatMessage("401881722",
                new ChatMessageRequest("cm-1", ChatMessageType.TEXT, "hi", null), principal);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> payload = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq("/queue/chat-errors-usersess-a"), payload.capture());
        verify(messagingTemplate).convertAndSend(eq("/queue/chat-errors-usersess-b"), any(Map.class));
        assertThat(payload.getValue()).containsEntry("error", "RATE_LIMITED");
    }

    @Test
    void noDeliveryWhenUserHasNoActiveSession() {
        // 用户在发送中途断开：注册表查不到 -> 不投递，也不会触发框架 ERROR
        when(simpUserRegistry.getUser("tester")).thenReturn(null);

        controller.handleChatMessage("401881722",
                new ChatMessageRequest("cm-2", ChatMessageType.TEXT, "hi", null), principal);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void registryFailureDoesNotBreakMessageHandling() {
        when(simpUserRegistry.getUser("tester")).thenThrow(new IllegalStateException("registry down"));

        // 不应抛异常到调用方（否则 STOMP 帧处理会失败）
        controller.handleChatMessage("401881722",
                new ChatMessageRequest("cm-3", ChatMessageType.TEXT, "hi", null), principal);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void receiptsNeverUseUserDestinationRedispatchPath() {
        when(simpUserRegistry.getUser("tester")).thenReturn(simpUser);
        when(simpUser.getSessions()).thenReturn(Set.of(sessionA));
        when(sessionA.getId()).thenReturn("sess-a");

        controller.handleChatMessage("401881722",
                new ChatMessageRequest("cm-4", ChatMessageType.TEXT, "hi", null), principal);

        // /user 目的地会走 UserDestinationMessageHandler 二次派发，是 ERROR 的来源
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    private static com.nbamanager.domain.UserAccount userAccount(String username) {
        com.nbamanager.domain.UserAccount account = new com.nbamanager.domain.UserAccount();
        account.setId(7L);
        account.setUsername(username);
        account.setRole(Role.USER);
        account.setPassword("x");
        return account;
    }
    // ==================== F1 回归：STOMP 异常回执不再走 @SendToUser ====================

    @Test
    void messageExceptionHandlerDeliversDirectlyWithoutUserDestinationRedispatch() {
        // 缺陷根因：handleException 上的 @SendToUser 会让 SendToMethodReturnValueHandler
        // 调 convertAndSendToUser，进而被 UserDestinationMessageHandler 二次派发，
        // 在 OrderedMessageChannelDecorator 上断言失败刷 ERROR（安全报告 §九 S1）。
        // 修复后必须走直投，且绝不调用 convertAndSendToUser。
        when(simpUserRegistry.getUser("tester")).thenReturn(simpUser);
        when(simpUser.getSessions()).thenReturn(Set.of(sessionA, sessionB));
        when(sessionA.getId()).thenReturn("sess-a");
        when(sessionB.getId()).thenReturn("sess-b");

        controller.handleException(new IllegalStateException("bad json"), principal);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> payload = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq("/queue/chat-errors-usersess-a"), payload.capture());
        verify(messagingTemplate).convertAndSend(eq("/queue/chat-errors-usersess-b"), any(Map.class));
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
        assertThat(payload.getValue())
                .containsEntry("error", "EXCEPTION")
                .containsEntry("message", "bad json");
    }

    @Test
    void messageExceptionHandlerSkipsDeliveryWhenUserOffline() {
        // 离线用户不应产生任何投递尝试（这正是框架 ERROR 噪声的来源之一）
        when(simpUserRegistry.getUser("tester")).thenReturn(null);

        controller.handleException(new IllegalStateException("bad json"), principal);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void messageExceptionHandlerToleratesUnknownPrincipal() {
        // 无法识别用户时只记日志，不应抛异常（否则会再次进入异常处理流程）
        controller.handleException(new IllegalStateException("bad json"), null);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void messageExceptionHandlerFallsBackToGenericMessageWhenCauseHasNoText() {
        when(simpUserRegistry.getUser("tester")).thenReturn(simpUser);
        when(simpUser.getSessions()).thenReturn(Set.of(sessionA));
        when(sessionA.getId()).thenReturn("sess-a");

        controller.handleException(new IllegalStateException(), principal);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> payload = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq("/queue/chat-errors-usersess-a"), payload.capture());
        assertThat(payload.getValue()).containsEntry("message", "未知错误");
    }
}
