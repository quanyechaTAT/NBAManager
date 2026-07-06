package com.nbamanager.service;

import com.nbamanager.domain.*;
import com.nbamanager.repository.*;
import com.nbamanager.service.mimo.*;
import com.nbamanager.web.dto.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
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
    private final AiReplayExplanationRepository explanationRepository;
    private final AiGameSummaryRepository summaryRepository;
    private final GameBoxScoreRepository boxScoreRepository;
    private final MimoClient mimoClient;
    private final AiGroundingValidator groundingValidator;

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

    public GameSummaryDto getSummary(String gameId) {
        // 1. Check cache
        var cached = summaryRepository.findByGameIdAndSummaryType(gameId, "FULL");
        if (cached.isPresent()) {
            AiGameSummary s = cached.get();
            return new GameSummaryDto(gameId, "FULL", s.getContent(),
                    s.getCitedEventIdsJson(), s.getCitedStatKeysJson(),
                    s.getModelProvider(), s.getModelName());
        }

        // 2. Collect facts
        ensureData(gameId);
        String factsJson = buildGameFactsJson(gameId);

        // 3. Call Mimo
        MimoClient.ChatResponse resp = mimoClient.chat(ReplayPrompts.SYSTEM_PROMPT,
                ReplayPrompts.buildSummaryPrompt(factsJson));

        if (!resp.success()) {
            log.warn("Mimo summary failed for gameId={}: {}", gameId, resp.error());
            return new GameSummaryDto(gameId, "FULL", null, null, null, resp.model(), null);
        }

        // 4. Validate grounding
        List<TimelineEventDto> timeline = getTimeline(gameId, null);
        AiGroundingValidator.ValidationResult validation = groundingValidator.validate(resp.content(), timeline);
        String content = validation.safeResponse();

        // 5. Cache
        AiGameSummary summary = new AiGameSummary();
        summary.setGameId(gameId);
        summary.setSummaryType("FULL");
        summary.setModelProvider("mimo");
        summary.setModelName(resp.model());
        summary.setPromptVersion("v1");
        summary.setFactsHash(sha256(factsJson));
        summary.setContent(content);
        summary.setCreatedAt(LocalDateTime.now());
        summaryRepository.save(summary);

        return new GameSummaryDto(gameId, "FULL", content, null, null, "mimo", resp.model());
    }

    public EventExplainDto explainEvent(String gameId, Long eventId) {
        PlayByPlay event = playByPlayRepository.findById(eventId).orElse(null);
        if (event == null) return null;

        // 1. Check cache
        var cached = explanationRepository.findByGameIdAndEventId(gameId, eventId);
        if (cached.isPresent()) {
            AiReplayExplanation e = cached.get();
            return new EventExplainDto(gameId, eventId, toTimelineDto(event),
                    e.getExplanation(), e.getCitedEventIdsJson(), e.getCitedStatKeysJson(),
                    e.getModelProvider(), e.getModelName());
        }

        // 2. Build facts
        ensureData(gameId);
        List<PlayByPlay> allEvents = playByPlayRepository.findByGameIdOrderByPeriodAscGameClockDesc(gameId);
        List<TimelineEventDto> timeline = allEvents.stream().map(this::toTimelineDto).collect(Collectors.toList());

        int idx = -1;
        for (int i = 0; i < allEvents.size(); i++) {
            if (allEvents.get(i).getId().equals(eventId)) {
                idx = i;
                break;
            }
        }

        String eventJson = toTimelineDto(event).toString();
        int from = Math.max(0, idx - 5);
        int to = Math.min(allEvents.size(), idx + 6);
        String contextJson = allEvents.subList(from, to).stream()
                .map(e -> toTimelineDto(e).toString())
                .collect(Collectors.joining("\n"));

        // 3. Call Mimo
        MimoClient.ChatResponse resp = mimoClient.chat(ReplayPrompts.SYSTEM_PROMPT,
                ReplayPrompts.buildExplainPrompt(eventJson, contextJson));

        if (!resp.success()) {
            log.warn("Mimo explain failed for eventId={}: {}", eventId, resp.error());
            return new EventExplainDto(gameId, eventId, toTimelineDto(event),
                    null, null, null, resp.model(), null);
        }

        // 4. Validate grounding
        AiGroundingValidator.ValidationResult validation = groundingValidator.validate(resp.content(), timeline);
        String explanation = validation.safeResponse();

        // 5. Cache
        AiReplayExplanation explanationEntity = new AiReplayExplanation();
        explanationEntity.setGameId(gameId);
        explanationEntity.setEventId(eventId);
        explanationEntity.setModelProvider("mimo");
        explanationEntity.setModelName(resp.model());
        explanationEntity.setPromptVersion("v1");
        explanationEntity.setFactsHash(sha256(eventJson + contextJson));
        explanationEntity.setExplanation(explanation);
        explanationEntity.setCreatedAt(LocalDateTime.now());
        explanationRepository.save(explanationEntity);

        return new EventExplainDto(gameId, eventId, toTimelineDto(event),
                explanation, null, null, "mimo", resp.model());
    }

    private String buildGameFactsJson(String gameId) {
        // Timeline events
        List<PlayByPlay> events = playByPlayRepository.findByGameIdOrderByPeriodAscGameClockDesc(gameId);
        JSONArray eventsArr = new JSONArray();
        for (PlayByPlay e : events) {
            JSONObject obj = new JSONObject();
            obj.put("period", e.getPeriod());
            obj.put("clock", e.getGameClock());
            obj.put("type", e.getEventType());
            obj.put("player", e.getPlayerName());
            obj.put("team", e.getTeamName());
            obj.put("desc", e.getDescription());
            obj.put("homeScore", e.getHomeScore());
            obj.put("awayScore", e.getAwayScore());
            eventsArr.put(obj);
        }

        // Box score
        List<GameBoxScore> boxScores = boxScoreRepository.findByGameId(gameId);
        JSONArray boxArr = new JSONArray();
        for (GameBoxScore b : boxScores) {
            JSONObject obj = new JSONObject();
            obj.put("player", b.getPlayerName());
            obj.put("team", b.getTeamName());
            obj.put("pts", b.getPoints());
            obj.put("reb", b.getRebounds());
            obj.put("ast", b.getAssists());
            obj.put("min", b.getMinutes());
            boxArr.put(obj);
        }

        JSONObject root = new JSONObject();
        root.put("gameId", gameId);
        root.put("timeline", eventsArr);
        root.put("boxScore", boxArr);
        return root.toString();
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(input.hashCode());
        }
    }

    private void ensureData(String gameId) {
        boolean hasPbp = !playByPlayRepository.findByGameIdOrderByPeriodAscGameClockDesc(gameId).isEmpty();
        boolean hasMomentum = !momentumRepository.findByGameIdOrderByEventOrderAsc(gameId).isEmpty();

        if (!hasPbp || !hasMomentum) {
            log.info("Data incomplete for gameId={} (pbp={}, momentum={}), triggering collection",
                    gameId, hasPbp, hasMomentum);
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
