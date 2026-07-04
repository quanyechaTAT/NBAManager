package com.nbamanager.service;

import com.nbamanager.domain.*;
import com.nbamanager.repository.*;
import com.nbamanager.web.dto.*;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiReplayService {

    private final PlayByPlayRepository playByPlayRepository;
    private final GameMomentumSnapshotRepository momentumRepository;
    private final GameTurningPointRepository turningPointRepository;
    private final GameHighlightRepository highlightRepository;
    private final PlayByPlayCollectorService collectorService;

    public List<TimelineEventDto> getTimeline(String gameId, Integer period) {
        ensureData(gameId);
        List<PlayByPlay> events;
        if (period != null) {
            events = playByPlayRepository.findByGameIdAndPeriodOrderByGameClockDesc(gameId, period);
        } else {
            events = playByPlayRepository.findByGameIdOrderByPeriodAscGameClockDesc(gameId);
        }
        return events.stream().map(this::toTimelineDto).collect(Collectors.toList());
    }

    public List<MomentumDto> getMomentum(String gameId) {
        ensureData(gameId);
        return momentumRepository.findByGameIdOrderByEventOrderAsc(gameId)
                .stream().map(this::toMomentumDto).collect(Collectors.toList());
    }

    public List<HighlightDto> getHighlights(String gameId) {
        ensureData(gameId);
        return highlightRepository.findByGameIdOrderByPeriodAscGameClockDesc(gameId)
                .stream().map(this::toHighlightDto).collect(Collectors.toList());
    }

    public List<TurningPointDto> getTurningPoints(String gameId) {
        ensureData(gameId);
        return turningPointRepository.findByGameIdOrderByPeriodAscGameClockDesc(gameId)
                .stream().map(this::toTurningPointDto).collect(Collectors.toList());
    }

    // Non-AI summary stub - returns empty for now, will be filled by Mimo in Phase 3
    public GameSummaryDto getSummary(String gameId) {
        return new GameSummaryDto(gameId, "FULL", null, null, null, null, null);
    }

    // Non-AI explain stub - returns event facts only, AI explanation in Phase 3
    public EventExplainDto explainEvent(String gameId, Long eventId) {
        PlayByPlay event = playByPlayRepository.findById(eventId).orElse(null);
        if (event == null) return null;
        return new EventExplainDto(
                gameId, eventId, toTimelineDto(event),
                null, null, null, null, null
        );
    }

    private void ensureData(String gameId) {
        if (playByPlayRepository.findByGameIdOrderByPeriodAscGameClockDesc(gameId).isEmpty()) {
            log.info("No PlayByPlay data for gameId={}, triggering collection", gameId);
            collectorService.collect(gameId);
        }
    }

    private TimelineEventDto toTimelineDto(PlayByPlay p) {
        return new TimelineEventDto(
                p.getId(), p.getPeriod(), p.getGameClock(), p.getEventType(),
                p.getActionType(), p.getDescription(), p.getPlayerName(),
                p.getPlayerId(), p.getTeamName(), p.getTeamId(),
                p.getHomeScore(), p.getAwayScore(), p.getScoreMargin(),
                p.getIsScoringPlay(), p.getIsHighlightCandidate(),
                p.getEventOrder(), p.getSource()
        );
    }

    private MomentumDto toMomentumDto(GameMomentumSnapshot m) {
        return new MomentumDto(m.getId(), m.getEventOrder(), m.getPeriod(),
                m.getGameClock(), m.getHomeScore(), m.getAwayScore(), m.getScoreMargin());
    }

    private HighlightDto toHighlightDto(GameHighlight h) {
        return new HighlightDto(h.getId(), h.getPeriod(), h.getGameClock(),
                h.getHighlightType(), h.getTitle(), h.getDescription(),
                h.getPlayerId(), h.getTeamId(), h.getScoreAfter());
    }

    private TurningPointDto toTurningPointDto(GameTurningPoint t) {
        return new TurningPointDto(t.getId(), t.getPeriod(), t.getGameClock(),
                t.getType(), t.getConfidence(), t.getFactualReason(),
                t.getAiExplanation(), t.getCitedEventIdsJson());
    }
}
