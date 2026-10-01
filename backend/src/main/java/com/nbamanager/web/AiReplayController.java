package com.nbamanager.web;

import com.nbamanager.domain.replay.ReplayPageState;
import com.nbamanager.service.AiReplayService;
import com.nbamanager.service.ReplayPreparationService;
import com.nbamanager.service.ReplayReadService;
import com.nbamanager.web.dto.EventExplainDto;
import com.nbamanager.web.dto.GameSummaryDto;
import com.nbamanager.web.dto.MomentumDto;
import com.nbamanager.web.dto.ReplayBootstrapDto;
import com.nbamanager.web.dto.ReplayPreparationDto;
import com.nbamanager.web.dto.TimelineEventDto;
import com.nbamanager.web.dto.HighlightDto;
import com.nbamanager.web.dto.TurningPointDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-replay")
@RequiredArgsConstructor
@Slf4j
public class AiReplayController {

    private final AiReplayService aiReplayService;
    private final ReplayReadService replayReadService;
    private final ReplayPreparationService replayPreparationService;

    @GetMapping("/games/{gameId}/bootstrap")
    public ReplayBootstrapDto bootstrap(@PathVariable String gameId) {
        return replayReadService.bootstrap(gameId);
    }

    @PostMapping("/games/{gameId}/prepare")
    public ResponseEntity<ReplayPreparationDto> prepare(@PathVariable String gameId) {
        ReplayPreparationDto result = replayPreparationService.prepare(gameId);
        HttpStatus status = result.state() == ReplayPageState.PREPARING
                ? HttpStatus.ACCEPTED
                : HttpStatus.OK;
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (result.retryAfterSeconds() != null) {
            response.header("Retry-After", String.valueOf(result.retryAfterSeconds()));
        }
        return response.body(result);
    }

    @GetMapping("/games/{gameId}/timeline")
    public List<TimelineEventDto> getTimeline(
            @PathVariable String gameId,
            @RequestParam(required = false) Integer period) {
        List<TimelineEventDto> events = replayReadService.bootstrap(gameId).timeline();
        return period == null ? events : events.stream().filter(event -> period.equals(event.period())).toList();
    }

    @GetMapping("/games/{gameId}/momentum")
    public List<MomentumDto> getMomentum(@PathVariable String gameId) {
        return replayReadService.bootstrap(gameId).momentum();
    }

    @GetMapping("/games/{gameId}/highlights")
    public List<HighlightDto> getHighlights(@PathVariable String gameId) {
        return replayReadService.bootstrap(gameId).highlights();
    }

    @GetMapping("/games/{gameId}/turning-points")
    public List<TurningPointDto> getTurningPoints(@PathVariable String gameId) {
        return replayReadService.bootstrap(gameId).turningPoints();
    }

    @GetMapping("/games/{gameId}/summary")
    public GameSummaryDto getSummary(@PathVariable String gameId) {
        return replayReadService.bootstrap(gameId).cachedSummary();
    }

    @PostMapping("/games/{gameId}/summary")
    public GameSummaryDto generateSummary(@PathVariable String gameId) {
        return aiReplayService.generateSummary(gameId);
    }

    @PostMapping("/games/{gameId}/events/{eventId}/explain")
    public EventExplainDto explainEvent(@PathVariable String gameId, @PathVariable Long eventId) {
        return aiReplayService.explainEvent(gameId, eventId);
    }
}
