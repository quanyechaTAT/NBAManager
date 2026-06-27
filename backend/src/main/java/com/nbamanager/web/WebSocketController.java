package com.nbamanager.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    /** 每个比赛的聊天消息缓存 */
    private final Map<String, java.util.List<Map<String, Object>>> chatHistory = new ConcurrentHashMap<>();

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

    /**
     * 接收客户端发送的聊天消息，广播到 /topic/chat/{gameId}
     * 前端通过 STOMP 发送到 /app/chat/{gameId}
     */
    @MessageMapping("/chat/{gameId}")
    public void handleChatMessage(
            @DestinationVariable String gameId,
            @Payload Map<String, Object> payload) {
        log.info("收到聊天消息: gameId={}, payload={}", gameId, payload);

        // 构建广播消息
        Map<String, Object> message = new java.util.HashMap<>(payload);
        message.putIfAbsent("gameId", gameId);
        message.putIfAbsent("timestamp", System.currentTimeMillis());
        message.putIfAbsent("id", java.util.UUID.randomUUID().toString());

        // 保存到历史
        chatHistory.computeIfAbsent(gameId, k -> new java.util.ArrayList<>()).add(message);

        // 广播到订阅该比赛的客户端
        messagingTemplate.convertAndSend("/topic/chat/" + gameId, message);
    }

    /** 获取指定比赛的聊天历史 */
    public java.util.List<Map<String, Object>> getChatHistory(String gameId) {
        return chatHistory.getOrDefault(gameId, java.util.List.of());
    }
}
