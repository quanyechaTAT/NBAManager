package com.nbamanager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbamanager.web.dto.ChatMessageResponse;
import com.nbamanager.web.dto.ChatPresenceResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ChatRedisPubSub {

    private static final Topic CHAT_TOPIC = new ChannelTopic("nba:chat:events");
    private static final Topic PRESENCE_TOPIC = new ChannelTopic("nba:chat:presence");

    private final String instanceId = UUID.randomUUID().toString();

    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final boolean redisAvailable;

    public ChatRedisPubSub(
            @Qualifier("redisTemplate") RedisTemplate<String, Object> redisTemplate,
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
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
                    }
                } catch (Exception e) {
                    log.warn("处理Redis消息失败: {}", e.getMessage());
                }
            };

            container.addMessageListener(chatListener, CHAT_TOPIC);
            container.addMessageListener(chatListener, PRESENCE_TOPIC);

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
            redisTemplate.convertAndSend("nba:chat:events", objectMapper.writeValueAsString(event));
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
            redisTemplate.convertAndSend("nba:chat:presence", objectMapper.writeValueAsString(event));
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
            redisTemplate.convertAndSend("nba:chat:events", objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("Redis删除事件发布失败: {}", e.getMessage());
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
}
