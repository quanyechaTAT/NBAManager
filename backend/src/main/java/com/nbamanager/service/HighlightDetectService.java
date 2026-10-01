package com.nbamanager.service;

import com.nbamanager.domain.GameHighlight;
import com.nbamanager.domain.PlayByPlay;
import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.repository.GameHighlightRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class HighlightDetectService {

    private final GameHighlightRepository repository;

    @Transactional
    public List<GameHighlight> detect(String gameId, List<PlayByPlayEvent> events) {
        return detect(gameId, events, List.of());
    }

    @Transactional
    public List<GameHighlight> detect(String gameId, List<PlayByPlayEvent> events,
                                      List<PlayByPlay> persistedFacts) {
        repository.deleteByGameId(gameId);

        Map<Integer, Long> eventIdsByOrder = new HashMap<>();
        if (persistedFacts != null) {
            for (PlayByPlay fact : persistedFacts) {
                if (fact != null && fact.getEventOrder() != null && fact.getId() != null) {
                    eventIdsByOrder.put(fact.getEventOrder(), fact.getId());
                }
            }
        }

        List<GameHighlight> highlights = new ArrayList<>();
        for (PlayByPlayEvent event : events) {
            if (!event.isHighlightCandidate()) {
                continue;
            }
            String type = classifyHighlight(event);
            String title = buildTitle(event, type);

            GameHighlight highlight = new GameHighlight();
            highlight.setGameId(gameId);
            highlight.setEventId(eventIdsByOrder.get(event.eventOrder()));
            highlight.setPeriod(event.period());
            highlight.setGameClock(event.gameClock());
            highlight.setHighlightType(type);
            highlight.setTitle(title);
            highlight.setDescription(event.description());
            highlight.setPlayerId(event.playerId());
            highlight.setTeamId(event.teamId());
            highlight.setScoreAfter(event.homeScore() + "-" + event.awayScore());
            highlight.setCreatedAt(LocalDateTime.now());
            highlights.add(highlight);
        }

        return repository.saveAll(highlights);
    }

    private String classifyHighlight(PlayByPlayEvent event) {
        String eventType = event.eventType() != null ? event.eventType().toLowerCase() : "";
        String description = event.description() != null ? event.description() : "";

        if (eventType.contains("dunk")) {
            return "DUNK";
        }
        if (eventType.contains("block") || description.contains("BLOCK")) {
            return "BLOCK";
        }
        if (eventType.contains("3pt") && description.contains("MAKES")) {
            return "THREE_PT";
        }
        if (eventType.contains("steal") || description.contains("STEAL")) {
            return "STEAL";
        }
        if (event.period() >= 4 && isLast30Seconds(event.gameClock())
                && description.contains("MAKES")) {
            return "GAME_WINNER";
        }
        return "HIGHLIGHT";
    }

    private boolean isLast30Seconds(String gameClock) {
        if (gameClock == null || !gameClock.contains(":")) {
            return false;
        }
        String[] parts = gameClock.split(":");
        try {
            int minutes = Integer.parseInt(parts[0].trim());
            int seconds = Integer.parseInt(parts[1].trim());
            return minutes == 0 && seconds <= 30;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String buildTitle(PlayByPlayEvent event, String type) {
        String playerName = event.playerName() != null ? event.playerName() : "球员";
        return switch (type) {
            case "DUNK" -> playerName + " 扣篮";
            case "BLOCK" -> playerName + " 盖帽";
            case "THREE_PT" -> playerName + " 三分命中";
            case "STEAL" -> playerName + " 抢断";
            case "GAME_WINNER" -> playerName + " 绝杀";
            default -> playerName + " 精彩表现";
        };
    }
}
