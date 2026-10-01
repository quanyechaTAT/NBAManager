package com.nbamanager.event;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nbamanager.service.ChatPresenceService;
import com.nbamanager.security.UserIdentityCache;
import com.nbamanager.service.ChatRedisPubSub;
import com.nbamanager.web.dto.ChatPresenceResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class UserIdentityEventListenerTest {

    @Mock private ChatPresenceService presenceService;
    @Mock private ChatRedisPubSub chatRedisPubSub;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private UserIdentityCache userIdentityCache;

    @InjectMocks
    private UserIdentityEventListener listener;

    @Test
    void broadcastsIdentityToStompAndRedis() {
        when(presenceService.updateUserIdentity(7L, "newname", "/uploads/avatars/new.png"))
                .thenReturn(Set.of("game-1"));
        when(presenceService.getPresence("game-1"))
                .thenReturn(new ChatPresenceResponse("game-1", 1, List.of(), Instant.now()));

        listener.onUserIdentityUpdated(new UserIdentityUpdatedEvent(7L, "newname", "/uploads/avatars/new.png"));

        verify(userIdentityCache).evict("newname");
        verify(chatRedisPubSub).publishUserIdentity(7L, "newname", "/uploads/avatars/new.png");
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/user-identity"),
                org.mockito.ArgumentMatchers.any(Map.class));
    }

    @Test
    void redisFailureDoesNotBreakHandler() {
        when(presenceService.updateUserIdentity(7L, "newname", null))
                .thenReturn(Set.of());
        org.mockito.Mockito.doThrow(new RuntimeException("Redis down"))
                .when(chatRedisPubSub).publishUserIdentity(7L, "newname", null);

        // Should not throw - exception is caught internally
        listener.onUserIdentityUpdated(new UserIdentityUpdatedEvent(7L, "newname", null));

        // Presence update still happened
        verify(presenceService).updateUserIdentity(7L, "newname", null);
    }

    @Test
    void stompBroadcastIncludesAllFields() {
        when(presenceService.updateUserIdentity(7L, "newname", "/uploads/avatars/new.png"))
                .thenReturn(Set.of("game-1"));
        when(presenceService.getPresence("game-1"))
                .thenReturn(new ChatPresenceResponse("game-1", 1, List.of(), Instant.now()));

        listener.onUserIdentityUpdated(new UserIdentityUpdatedEvent(7L, "newname", "/uploads/avatars/new.png"));

        // Verify STOMP broadcast to /topic/user-identity (2 calls: identity + presence)
        verify(messagingTemplate, org.mockito.Mockito.times(2)).convertAndSend(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Object.class));

        // Verify Redis publish
        verify(chatRedisPubSub).publishUserIdentity(7L, "newname", "/uploads/avatars/new.png");
    }
}
