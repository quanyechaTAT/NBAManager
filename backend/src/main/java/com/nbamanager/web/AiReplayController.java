package com.nbamanager.web;

import com.nbamanager.service.AiReplayService;
import com.nbamanager.web.dto.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai-replay")
@RequiredArgsConstructor
@Slf4j
public class AiReplayController {

    private final AiReplayService aiReplayService;

    @GetMapping("/games/{gameId}/timeline")
    public List<TimelineEventDto> getTimeline(
            @PathVariable String gameId,
            @RequestParam(required = false) Integer period) {
        return aiReplayService.getTimeline(gameId, period);
    }

    @GetMapping("/games/{gameId}/momentum")
    public List<MomentumDto> getMomentum(@PathVariable String gameId) {
        return aiReplayService.getMomentum(gameId);
    }

    @GetMapping("/games/{gameId}/highlights")
    public List<HighlightDto> getHighlights(@PathVariable String gameId) {
        return aiReplayService.getHighlights(gameId);
    }

    @GetMapping("/games/{gameId}/turning-points")
    public List<TurningPointDto> getTurningPoints(@PathVariable String gameId) {
        return aiReplayService.getTurningPoints(gameId);
    }

    @GetMapping("/games/{gameId}/summary")
    public GameSummaryDto getSummary(@PathVariable String gameId) {
        return aiReplayService.getSummary(gameId);
    }

    @PostMapping("/games/{gameId}/events/{eventId}/explain")
    public EventExplainDto explainEvent(@PathVariable String gameId, @PathVariable Long eventId) {
        return aiReplayService.explainEvent(gameId, eventId);
    }
}
