package com.nbamanager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbamanager.web.dto.ChatMessageResponse;
import com.nbamanager.web.dto.ChatPresenceResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * 跨实例广播（Redis Pub/Sub）。
 *
 * <h2>发布必须用 StringRedisTemplate</h2>
 * 事件体是<b>已经序列化好的 JSON 文本</b>（{@code objectMapper.writeValueAsString(...)}），
 * 监听侧也直接 {@code new String(message.getBody())} 后自行解析。因此发布必须走
 * 纯字符串序列化器。
 *
 * 此前用应用主 {@code RedisTemplate<String,Object>} 发布，而它的 value serializer 是
 * {@code GenericJackson2JsonRedisSerializer}：传进去的字符串被<b>二次 JSON 编码</b>，
 * 订阅端拿到的是带转义的字符串字面量，{@code objectMapper.readValue(body, Map.class)}
 * 直接抛 "Cannot construct instance of LinkedHashMap ... from String value"。
 * 症状是跨实例的聊天消息/在线状态/身份变更<b>全部静默失效</b>（单实例内仍正常，
 * 所以功能自测很难发现），日志里只有一条 WARN。
 *
 * 实测证据：同一频道用 redis-cli 发布裸 JSON 能被正常处理；由本类发布的事件必定解析失败。
 */
@Service
@Slf4j
public class ChatRedisPubSub {

    private static final Topic CHAT_TOPIC = new ChannelTopic("nba:chat:events");
    private static final Topic PRESENCE_TOPIC = new ChannelTopic("nba:chat:presence");
    private static final Topic IDENTITY_TOPIC = new ChannelTopic("nba:user:identity");

    private final String instanceId = UUID.randomUUID().toString();

    private final RedisTemplate<String, Object> redisTemplate;
    /** 发布事件专用：只做字符串序列化，避免对已是 JSON 文本的事件体二次编码。 */
    private final StringRedisTemplate pubSubTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final ChatPresenceService presenceService;
    private final boolean redisAvailable;

    public ChatRedisPubSub(
            @Qualifier("redisTemplate") RedisTemplate<String, Object> redisTemplate,
            StringRedisTemplate pubSubTemplate,
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper,
            ChatPresenceService presenceService) {
        this.redisTemplate = redisTemplate;
        this.pubSubTemplate = pubSubTemplate;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.presenceService = presenceService;
        this.redisAvailable = testRedis();
    }

    private boolean testRedis() {
        try {
            redisTemplate.hasKey("test");
            return true;
        } catch (Exception e) {
            log.warn("Redis不可用，Pub/Sub已禁用: {}", e.getMessage());
            return false;
        }
    }

    @PostConstruct
    public void init() {
        if (!redisAvailable) return;
        try {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(redisTemplate.getConnectionFactory());

            MessageListener chatListener = (message, pattern) -> {
                try {
                    String channel = new String(message.getChannel());
                    String body = new String(message.getBody());
                    if ("nba:chat:events".equals(channel)) {
                        handleChatEvent(body);
                    } else if ("nba:chat:presence".equals(channel)) {
                        handlePresenceEvent(body);
                    } else if ("nba:user:identity".equals(channel)) {
                        handleIdentityEvent(body);
                    }
                } catch (Exception e) {
                    log.warn("处理Redis消息失败: {}", e.getMessage());
                }
            };

            container.addMessageListener(chatListener, CHAT_TOPIC);
            container.addMessageListener(chatListener, PRESENCE_TOPIC);
            container.addMessageListener(chatListener, IDENTITY_TOPIC);

            container.afterPropertiesSet();
            container.start();
            log.info("Redis Pub/Sub 已启动, instanceId={}", instanceId);
        } catch (Exception e) {
            log.warn("Redis Pub/Sub 启动失败: {}", e.getMessage());
        }
    }

    public void publishMessage(String gameId, ChatMessageResponse response) {
        if (!redisAvailable) return;
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "MESSAGE");
            event.put("gameId", gameId);
            event.put("data", response);
            event.put("sourceInstanceId", instanceId);
            pubSubTemplate.convertAndSend("nba:chat:events", objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("Redis消息发布失败: {}", e.getMessage());
        }
    }

    public void publishPresence(String gameId, ChatPresenceResponse presence) {
        if (!redisAvailable) return;
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "PRESENCE");
            event.put("gameId", gameId);
            event.put("data", presence);
            event.put("sourceInstanceId", instanceId);
            pubSubTemplate.convertAndSend("nba:chat:presence", objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("Redis presence发布失败: {}", e.getMessage());
        }
    }

    public void publishMessageDeleted(String gameId, Long messageId) {
        if (!redisAvailable) return;
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "MESSAGE_DELETED");
            event.put("gameId", gameId);
            event.put("messageId", messageId);
            event.put("sourceInstanceId", instanceId);
            pubSubTemplate.convertAndSend("nba:chat:events", objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("Redis删除事件发布失败: {}", e.getMessage());
        }
    }

    public void publishUserIdentity(Long userId, String username, String avatarUrl) {
        if (!redisAvailable) return;
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("type", "USER_IDENTITY_UPDATED");
            event.put("userId", userId);
            event.put("username", username);
            event.put("avatarUrl", avatarUrl);
            event.put("updatedAt", java.time.LocalDateTime.now().toString());
            event.put("sourceInstanceId", instanceId);
            pubSubTemplate.convertAndSend("nba:user:identity", objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("Redis身份事件发布失败: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleChatEvent(String body) {
        try {
            Map<String, Object> event = objectMapper.readValue(body, Map.class);
            // 忽略本实例发布的事件，避免重复广播
            if (instanceId.equals(event.get("sourceInstanceId"))) {
                return;
            }
            String type = (String) event.get("type");
            String gameId = (String) event.get("gameId");

            if ("MESSAGE".equals(type)) {
                Map<String, Object> data = (Map<String, Object>) event.get("data");
                messagingTemplate.convertAndSend("/topic/chat/" + gameId, data);
                log.debug("Redis消息已转发到本地STOMP: gameId={}", gameId);
            } else if ("MESSAGE_DELETED".equals(type)) {
                Long messageId = ((Number) event.get("messageId")).longValue();
                messagingTemplate.convertAndSend("/topic/chat/" + gameId,
                        Map.of("type", "MESSAGE_DELETED", "messageId", messageId));
            }
        } catch (Exception e) {
            log.warn("处理聊天事件失败: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void handlePresenceEvent(String body) {
        try {
            Map<String, Object> event = objectMapper.readValue(body, Map.class);
            if (instanceId.equals(event.get("sourceInstanceId"))) {
                return;
            }
            String gameId = (String) event.get("gameId");
            Map<String, Object> data = (Map<String, Object>) event.get("data");
            messagingTemplate.convertAndSend("/topic/chat/" + gameId + "/presence", data);
        } catch (Exception e) {
            log.warn("处理presence事件失败: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleIdentityEvent(String body) {
        try {
            Map<String, Object> event = objectMapper.readValue(body, Map.class);
            if (instanceId.equals(event.get("sourceInstanceId"))) {
                return;
            }
            Long userId = ((Number) event.get("userId")).longValue();
            String username = (String) event.get("username");
            String avatarUrl = (String) event.get("avatarUrl");

            // Update local presence and broadcast affected rooms
            Set<String> affectedGames = presenceService.updateUserIdentity(userId, username, avatarUrl);
            for (String gameId : affectedGames) {
                var presence = presenceService.getPresence(gameId);
                messagingTemplate.convertAndSend("/topic/chat/" + gameId + "/presence", presence);
            }

            // Broadcast identity event to all STOMP subscribers
            messagingTemplate.convertAndSend("/topic/user-identity", event);
            log.debug("跨实例身份事件已处理: userId={}", userId);
        } catch (Exception e) {
            log.warn("处理身份事件失败: {}", e.getMessage());
        }
    }
}
