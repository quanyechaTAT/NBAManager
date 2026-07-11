package com.nbamanager.service;

import com.nbamanager.domain.Role;
import com.nbamanager.web.dto.ChatPresenceResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ChatPresenceService {

    private static final String PRESENCE_PREFIX = "nba:chat:presence:";
    private static final String SESSION_PREFIX = "nba:chat:session:";
    private static final long PRESENCE_TTL_SECONDS = 120;

    // Local cache: sessionId -> presence info
    private final Map<String, SessionInfo> localSessions = new ConcurrentHashMap<>();
    // gameId -> set of sessionIds (local only)
    private final Map<String, Set<String>> gameSessions = new ConcurrentHashMap<>();

    private final RedisTemplate<String, Object> redisTemplate;
    private final boolean redisAvailable;

    public ChatPresenceService(@Qualifier("redisTemplate") RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.redisAvailable = testRedis();
    }

    private boolean testRedis() {
        try {
            redisTemplate.hasKey("test");
            return true;
        } catch (Exception e) {
            log.warn("Redis不可用，presence将仅限本地: {}", e.getMessage());
            return false;
        }
    }

    public record SessionInfo(
            String sessionId,
            String gameId,
            Long userId,
            String username,
            String avatarUrl,
            Role role,
            Instant connectedAt
    ) {}

    public void userJoined(String sessionId, String gameId, Long userId,
                           String username, String avatarUrl, Role role) {
        // Remove from previous game if any
        userLeft(sessionId);

        SessionInfo info = new SessionInfo(sessionId, gameId, userId, username, avatarUrl, role, Instant.now());
        localSessions.put(sessionId, info);
        gameSessions.computeIfAbsent(gameId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);

        // Persist to Redis
        if (redisAvailable) {
            try {
                String sessionKey = SESSION_PREFIX + sessionId;
                Map<String, Object> data = Map.of(
                        "gameId", gameId,
                        "userId", userId,
                        "username", username,
                        "avatarUrl", avatarUrl != null ? avatarUrl : "",
                        "role", role.name(),
                        "connectedAt", info.connectedAt().toString()
                );
                redisTemplate.opsForHash().putAll(sessionKey, data);
                redisTemplate.expire(sessionKey, java.time.Duration.ofSeconds(PRESENCE_TTL_SECONDS));

                String gameKey = PRESENCE_PREFIX + gameId;
                redisTemplate.opsForSet().add(gameKey, sessionId);
                redisTemplate.expire(gameKey, java.time.Duration.ofSeconds(PRESENCE_TTL_SECONDS));
            } catch (Exception e) {
                log.warn("Redis presence写入失败: {}", e.getMessage());
            }
        }
    }

    public void userLeft(String sessionId) {
        SessionInfo info = localSessions.remove(sessionId);
        if (info != null) {
            Set<String> sessions = gameSessions.get(info.gameId());
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) {
                    gameSessions.remove(info.gameId());
                }
            }
            if (redisAvailable) {
                try {
                    redisTemplate.delete(SESSION_PREFIX + sessionId);
                    redisTemplate.opsForSet().remove(PRESENCE_PREFIX + info.gameId(), sessionId);
                } catch (Exception e) {
                    log.warn("Redis presence删除失败: {}", e.getMessage());
                }
            }
        }
    }

    public void refreshTTL(String sessionId) {
        SessionInfo info = localSessions.get(sessionId);
        if (info == null) return;
        if (redisAvailable) {
            try {
                redisTemplate.expire(SESSION_PREFIX + sessionId, java.time.Duration.ofSeconds(PRESENCE_TTL_SECONDS));
                redisTemplate.expire(PRESENCE_PREFIX + info.gameId(), java.time.Duration.ofSeconds(PRESENCE_TTL_SECONDS));
            } catch (Exception e) {
                log.warn("Redis TTL刷新失败: {}", e.getMessage());
            }
        }
    }

    public ChatPresenceResponse getPresence(String gameId) {
        // Collect unique users from local sessions
        Map<Long, ChatPresenceResponse.Member> uniqueUsers = new LinkedHashMap<>();
        for (SessionInfo info : localSessions.values()) {
            if (gameId.equals(info.gameId())) {
                uniqueUsers.putIfAbsent(info.userId(),
                        new ChatPresenceResponse.Member(info.userId(), info.username(), info.avatarUrl(), info.role()));
            }
        }
        return new ChatPresenceResponse(
                gameId,
                uniqueUsers.size(),
                new ArrayList<>(uniqueUsers.values()),
                Instant.now()
        );
    }

    public SessionInfo getSession(String sessionId) {
        return localSessions.get(sessionId);
    }
}
