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
    private final PlayByPlayTranslationService translationService;

    public List<TimelineEventDto> getTimeline(String gameId, Integer period) {
        ensureData(gameId);
        List<PlayByPlay> events;
        if (period != null) {
            events = playByPlayRepository.findByGameIdAndPeriodOrderByEventOrderAsc(gameId, period);
        } else {
            events = playByPlayRepository.findByGameIdOrderByEventOrderAsc(gameId);
        }
        return toTimelineDtos(events);
    }

    public List<MomentumDto> getMomentum(String gameId) {
        ensureData(gameId);
        return momentumRepository.findByGameIdOrderByEventOrderAsc(gameId)
                .stream().map(this::toMomentumDto).collect(Collectors.toList());
    }

    public List<HighlightDto> getHighlights(String gameId) {
        ensureData(gameId);
        return toHighlightDtos(highlightRepository.findByGameIdOrderByPeriodAscGameClockDesc(gameId));
    }

    public List<TurningPointDto> getTurningPoints(String gameId) {
        ensureData(gameId);
        return toTurningPointDtos(turningPointRepository.findByGameIdOrderByPeriodAscGameClockDesc(gameId));
    }

    public GameSummaryDto getSummary(String gameId) {
        // 1. Check cache
        var cached = summaryRepository.findByGameIdAndSummaryType(gameId, "FULL");
        if (cached.isPresent()) {
            AiGameSummary s = cached.get();
            return new GameSummaryDto(gameId, "FULL", sanitizeAiText(s.getContent()),
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
        String content = sanitizeAiText(validation.safeResponse());

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
            return new EventExplainDto(gameId, eventId, toTranslatedTimelineDto(event),
                    sanitizeAiText(e.getExplanation()), e.getCitedEventIdsJson(), e.getCitedStatKeysJson(),
                    e.getModelProvider(), e.getModelName());
        }

        // 2. Build facts (only translate context events, not all 1600+)
        ensureData(gameId);
        List<PlayByPlay> allEvents = playByPlayRepository.findByGameIdOrderByEventOrderAsc(gameId);

        int idx = -1;
        for (int i = 0; i < allEvents.size(); i++) {
            if (allEvents.get(i).getId().equals(eventId)) {
                idx = i;
                break;
            }
        }

        // Only translate the target event + context (5 before, 5 after)
        String eventJson = toTimelineDto(event, translationService.translateText(event.getDescription())).toString();
        int from = Math.max(0, idx - 5);
        int to = Math.min(allEvents.size(), idx + 6);
        List<PlayByPlay> contextEvents = allEvents.subList(from, to);
        List<TimelineEventDto> contextDtos = toTimelineDtos(contextEvents);
        String contextJson = contextDtos.stream()
                .map(TimelineEventDto::toString)
                .collect(Collectors.joining("\n"));

        // 3. Call Mimo
        String explanation;
        String modelName = null;
        MimoClient.ChatResponse resp = mimoClient.chat(ReplayPrompts.SYSTEM_PROMPT,
                ReplayPrompts.buildExplainPrompt(eventJson, contextJson));

        if (resp.success()) {
            // 4. Validate grounding (use context events only, not all 1600+)
            AiGroundingValidator.ValidationResult validation = groundingValidator.validate(resp.content(), contextDtos);
            explanation = sanitizeAiText(validation.safeResponse());
            modelName = resp.model();
        } else {
            // Fallback: generate factual explanation from translated event data
            log.warn("Mimo explain failed for eventId={}: {}, using fallback", eventId, resp.error());
            explanation = buildFallbackExplanation(event, contextDtos);
            modelName = "fallback";
        }

        // 5. Cache
        AiReplayExplanation explanationEntity = new AiReplayExplanation();
        explanationEntity.setGameId(gameId);
        explanationEntity.setEventId(eventId);
        explanationEntity.setModelProvider("mimo");
        explanationEntity.setModelName(modelName);
        explanationEntity.setPromptVersion("v1");
        explanationEntity.setFactsHash(sha256(eventJson + contextJson));
        explanationEntity.setExplanation(explanation);
        explanationEntity.setCreatedAt(LocalDateTime.now());
        explanationRepository.save(explanationEntity);

        return new EventExplainDto(gameId, eventId, toTranslatedTimelineDto(event),
                explanation, null, null, "mimo", modelName);
    }

    private String buildGameFactsJson(String gameId) {
        // Timeline events
        List<PlayByPlay> events = playByPlayRepository.findByGameIdOrderByEventOrderAsc(gameId);
        List<String> translatedDescriptions = translationService.translateTexts(
                events.stream().map(PlayByPlay::getDescription).collect(Collectors.toList())
        );
        JSONArray eventsArr = new JSONArray();
        for (int i = 0; i < events.size(); i++) {
            PlayByPlay e = events.get(i);
            JSONObject obj = new JSONObject();
            obj.put("period", e.getPeriod());
            obj.put("clock", e.getGameClock());
            obj.put("type", e.getEventType());
            obj.put("player", e.getPlayerName());
            obj.put("team", e.getTeamName());
            obj.put("desc", translatedDescriptions.get(i));
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

    private String sanitizeAiText(String text) {
        if (text == null) return null;
        // Remove code fences
        text = text.replaceAll("```\\w*\\n?", "").replaceAll("```", "");
        // Remove bold/italic markers
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "$1").replaceAll("\\*(.+?)\\*", "$1");
        // Remove line-start list markers (* / - / bullet followed by space)
        text = text.replaceAll("(?m)^\\s*[*\\-•]\\s+", "");
        // Remove heading markers
        text = text.replaceAll("(?m)^#{1,6}\\s*", "");
        // Collapse multiple blank lines
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text.trim();
    }

    private String buildFallbackExplanation(PlayByPlay event, List<TimelineEventDto> context) {
        String translated = translationService.translateText(event.getDescription());
        int period = event.getPeriod();
        String clock = event.getGameClock();
        int home = event.getHomeScore();
        int away = event.getAwayScore();
        String player = translationService.translateText(event.getPlayerName());

        StringBuilder sb = new StringBuilder();
        sb.append("第").append(period).append("节 ").append(clock).append("，");
        if (player != null && !player.isEmpty()) {
            sb.append(player).append(" ");
        }
        sb.append(translated).append("。");
        sb.append("当前比分 ").append(home).append("-").append(away).append("。");

        // Add context from surrounding events
        if (context != null && context.size() > 1) {
            long scoringCount = context.stream().filter(TimelineEventDto::isScoringPlay).count();
            if (scoringCount > 0) {
                sb.append("该时段内有 ").append(scoringCount).append(" 次得分。");
            }
        }

        return sb.toString();
    }

    private void ensureData(String gameId) {
        boolean hasPbp = !playByPlayRepository.findByGameIdOrderByEventOrderAsc(gameId).isEmpty();
        boolean hasMomentum = !momentumRepository.findByGameIdOrderByEventOrderAsc(gameId).isEmpty();

        if (!hasPbp || !hasMomentum) {
            log.info("Data incomplete for gameId={} (pbp={}, momentum={}), triggering collection",
                    gameId, hasPbp, hasMomentum);
            collectorService.collect(gameId);
        }
    }

    private List<TimelineEventDto> toTimelineDtos(List<PlayByPlay> events) {
        List<String> descriptions = translationService.translateTexts(
                events.stream().map(PlayByPlay::getDescription).collect(Collectors.toList())
        );
        List<TimelineEventDto> result = new java.util.ArrayList<>(events.size());
        for (int i = 0; i < events.size(); i++) {
            result.add(toTimelineDto(events.get(i), descriptions.get(i)));
        }
        return result;
    }

    private TimelineEventDto toTimelineDto(PlayByPlay p, String description) {
        String original = p.getDescription();
        String translatedPlayerName = translationService.translateText(p.getPlayerName());
        return new TimelineEventDto(
                p.getId(), p.getPeriod(), p.getGameClock(), p.getEventType(),
                p.getActionType(), description, original, description, translatedPlayerName,
                p.getPlayerId(), p.getTeamName(), p.getTeamId(),
                p.getHomeScore(), p.getAwayScore(), p.getScoreMargin(),
                p.getIsScoringPlay(), p.getIsHighlightCandidate(),
                p.getEventOrder(), p.getSource()
        );
    }

    private TimelineEventDto toTranslatedTimelineDto(PlayByPlay p) {
        return toTimelineDto(p, translationService.translateText(p.getDescription()));
    }

    private MomentumDto toMomentumDto(GameMomentumSnapshot m) {
        return new MomentumDto(m.getId(), m.getEventOrder(), m.getPeriod(),
                m.getGameClock(), m.getHomeScore(), m.getAwayScore(), m.getScoreMargin());
    }

    private List<HighlightDto> toHighlightDtos(List<GameHighlight> highlights) {
        List<String> titleTranslations = translationService.translateTexts(
                highlights.stream().map(GameHighlight::getTitle).collect(Collectors.toList())
        );
        List<String> descriptionTranslations = translationService.translateTexts(
                highlights.stream().map(GameHighlight::getDescription).collect(Collectors.toList())
        );
        List<HighlightDto> result = new java.util.ArrayList<>(highlights.size());
        for (int i = 0; i < highlights.size(); i++) {
            GameHighlight h = highlights.get(i);
            result.add(new HighlightDto(h.getId(), h.getPeriod(), h.getGameClock(),
                    h.getHighlightType(), titleTranslations.get(i), descriptionTranslations.get(i),
                    h.getPlayerId(), h.getTeamId(), h.getScoreAfter()));
        }
        return result;
    }

    private List<TurningPointDto> toTurningPointDtos(List<GameTurningPoint> points) {
        List<String> reasonTranslations = translationService.translateTexts(
                points.stream().map(GameTurningPoint::getFactualReason).collect(Collectors.toList())
        );
        List<String> explanationTranslations = translationService.translateTexts(
                points.stream().map(GameTurningPoint::getAiExplanation).collect(Collectors.toList())
        );
        List<TurningPointDto> result = new java.util.ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) {
            GameTurningPoint t = points.get(i);
            result.add(new TurningPointDto(t.getId(), t.getPeriod(), t.getGameClock(),
                    t.getType(), t.getConfidence(), reasonTranslations.get(i),
                    explanationTranslations.get(i), t.getCitedEventIdsJson(),
                    t.getStartEventOrder(), t.getEndEventOrder(),
                    t.getStartHomeScore(), t.getStartAwayScore(),
                    t.getEndHomeScore(), t.getEndAwayScore(),
                    t.getScoreSwing(), t.getImpactScore(),
                    t.getImpactLevel(), t.getTeamName()));
        }
        return result;
    }
}
