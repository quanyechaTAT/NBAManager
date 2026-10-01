package com.nbamanager.event;

import com.nbamanager.security.UserIdentityCache;
import com.nbamanager.service.ChatPresenceService;
import com.nbamanager.service.ChatRedisPubSub;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 用户身份变更（用户名 / 头像）的统一出口。
 *
 * 缓存：帖子列表基座里带着每篇帖子的 username / avatarUrl，而缓存条目是跨用户共享的
 * （键里没有 userId），所以身份一变必须整体失效"posts"，否则改名/换头像后
 * 列表最长会显示到 TTL 到期为止。放在 AFTER_COMMIT 监听器上是刻意的：
 * 事务回滚时不应失效缓存；而这样写也天然覆盖了所有发布该事件的写入口
 * （AuthController 改用户名、ProfileService 上传/重置头像），新增入口无需再逐个补注解。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserIdentityEventListener {

    private final ChatPresenceService presenceService;
    private final ChatRedisPubSub chatRedisPubSub;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserIdentityCache userIdentityCache;

    @CacheEvict(value = "posts", allEntries = true)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserIdentityUpdated(UserIdentityUpdatedEvent event) {
        // 改名事件只携带新用户名；改名旧键由 AuthController 提交前清理，
        // 这里清理新键，覆盖其他发布者以及事务提交后的重建窗口。
        userIdentityCache.evict(event.username());
        try {
            Set<String> affectedGames = presenceService.updateUserIdentity(
                    event.userId(), event.username(), event.avatarUrl());
            for (String gameId : affectedGames) {
                var presence = presenceService.getPresence(gameId);
                messagingTemplate.convertAndSend("/topic/chat/" + gameId + "/presence", presence);
            }
            chatRedisPubSub.publishUserIdentity(event.userId(), event.username(), event.avatarUrl());
            messagingTemplate.convertAndSend("/topic/user-identity",
                    Map.of("type", "USER_IDENTITY_UPDATED",
                            "userId", event.userId(),
                            "username", event.username() != null ? event.username() : "",
                            "avatarUrl", event.avatarUrl() != null ? event.avatarUrl() : "",
                            "updatedAt", LocalDateTime.now().toString()));
        } catch (Exception e) {
            log.warn("身份变更事件处理失败: {}", e.getMessage());
        }
    }
}
