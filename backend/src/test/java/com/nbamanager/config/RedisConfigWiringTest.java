package com.nbamanager.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.types.Expiration;

class RedisConfigWiringTest {
    @Test void wrappedManagerInitializesNamedTtlsAndRetainsDefaultPolicy() {
        var factory = mock(RedisConnectionFactory.class);
        var connection = mock(RedisConnection.class, RETURNS_DEEP_STUBS);
        when(factory.getConnection()).thenReturn(connection);
        var config = new RedisConfig(1800, 60, 60);
        var manager = config.cacheManager(factory, config.jacksonRedisSerializer(), 128, 10000);
        manager.getCache("playerList").put("players", "public DTO");
        manager.getCache("matchRecord").put("empty", java.util.List.of());
        manager.getCache("unknown").put("default", "value");
        var ttl = ArgumentCaptor.forClass(Expiration.class);
        verify(connection.stringCommands(), times(3)).set(any(byte[].class), any(byte[].class), ttl.capture(), eq(RedisStringCommands.SetOption.UPSERT));
        assertThat(ttl.getAllValues().stream().map(Expiration::getExpirationTimeInMilliseconds)).containsExactly(60000L, 5000L, 1800000L);
    }
}
