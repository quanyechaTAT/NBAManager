package com.nbamanager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nbamanager.domain.Role;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

@ExtendWith(MockitoExtension.class)
class ChatPresenceServiceTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;

    @Test
    void updatesAvatarForEveryLocalSessionAndReturnsAffectedGames() {
        when(redisTemplate.hasKey("test")).thenThrow(new IllegalStateException("offline"));
        ChatPresenceService service = new ChatPresenceService(redisTemplate);
        service.userJoined("session-1", "game-1", 7L, "tester", "/old.png", Role.USER);
        service.userJoined("session-2", "game-2", 7L, "tester", "/old.png", Role.USER);

        Set<String> games = service.updateUserAvatar(7L, "/new.png");

        assertThat(games).containsExactlyInAnyOrder("game-1", "game-2");
        assertThat(service.getPresence("game-1").members().get(0).avatarUrl()).isEqualTo("/new.png");
        assertThat(service.getPresence("game-2").members().get(0).avatarUrl()).isEqualTo("/new.png");
    }
}
