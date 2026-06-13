package com.nbamanager.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketController {


    private final SimpMessagingTemplate messagingTemplate;

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
}
