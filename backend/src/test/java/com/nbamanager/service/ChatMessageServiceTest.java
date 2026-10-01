package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.ChatMessage;
import com.nbamanager.domain.ChatMessageType;
import com.nbamanager.domain.Role;
import com.nbamanager.domain.UserAccount;
import com.nbamanager.repository.ChatMessageRepository;
import com.nbamanager.repository.UserAccountRepository;
import com.nbamanager.security.UserPrincipal;
import com.nbamanager.web.dto.ChatMessageRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    private ChatMessageService service;
    private UserAccount user;

    @BeforeEach
    void setUp() {
        service = new ChatMessageService(messageRepository, userAccountRepository,
                org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class));
        user = new UserAccount();
        user.setId(7L);
        user.setUsername("tester");
        user.setPassword("password");
        user.setRole(Role.USER);
    }

    @Test
    void preservesLineBreaksWhenSavingTextMessage() {
        when(messageRepository.findByUserIdAndClientMessageId(7L, "message-1"))
                .thenReturn(Optional.empty());
        when(messageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ChatMessage saved = service.save(
                new UserPrincipal(user),
                "game-1",
                new ChatMessageRequest("message-1", ChatMessageType.TEXT, "  first line\nsecond line  ", null),
                null);

        assertThat(saved.getContent()).isEqualTo("  first line\nsecond line  ");
    }

    @Test
    void usesCurrentUserAvatarWhenLoadingHistory() {
        ChatMessage message = new ChatMessage();
        message.setId(11L);
        message.setGameId("game-1");
        message.setUserId(7L);
        message.setUsername("tester");
        message.setAvatarUrl("/uploads/avatars/old.png");
        message.setMessageType(ChatMessageType.TEXT);
        message.setContent("hello");
        message.setCreatedAt(Instant.now());

        user.setAvatarUrl("/uploads/avatars/current.png");
        when(messageRepository.findHistory(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(message));
        when(userAccountRepository.findAllById(any())).thenReturn(List.of(user));

        var history = service.getHistory("game-1", null, 50);

        assertThat(history).singleElement()
                .extracting(response -> response.avatarUrl())
                .isEqualTo("/uploads/avatars/current.png");
    }
}
