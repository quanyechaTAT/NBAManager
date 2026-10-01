package com.nbamanager.service;

import com.nbamanager.domain.AiGameSummary;
import com.nbamanager.domain.AiReplayExplanation;
import com.nbamanager.domain.GameBoxScore;
import com.nbamanager.domain.GameHighlight;
import com.nbamanager.domain.GameMomentumSnapshot;
import com.nbamanager.domain.GameTurningPoint;
import com.nbamanager.domain.PlayByPlay;
import com.nbamanager.repository.AiGameSummaryRepository;
import com.nbamanager.repository.AiReplayExplanationRepository;
import com.nbamanager.repository.GameBoxScoreRepository;
import com.nbamanager.repository.GameHighlightRepository;
import com.nbamanager.repository.GameMomentumSnapshotRepository;
import com.nbamanager.repository.GameTurningPointRepository;
import com.nbamanager.repository.PlayByPlayRepository;
import com.nbamanager.service.deepseek.AiGroundingValidator;
import com.nbamanager.service.deepseek.DeepSeekClient;
import com.nbamanager.service.deepseek.ReplayPrompts;
import com.nbamanager.web.dto.EventExplainDto;
import com.nbamanager.web.dto.GameSummaryDto;
import com.nbamanager.web.dto.HighlightDto;
import com.nbamanager.web.dto.MomentumDto;
import com.nbamanager.web.dto.TimelineEventDto;
import com.nbamanager.web.dto.TurningPointDto;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
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

    public static final String PROMPT_VERSION = "replay-ai-v2";

    private final PlayByPlayRepository playByPlayRepository;
    private final GameMomentumSnapshotRepository momentumRepository;
    private final GameTurningPointRepository turningPointRepository;
    private final GameHighlightRepository highlightRepository;
    private final AiReplayExplanationRepository explanationRepository;
    private final AiGameSummaryRepository summaryRepository;
    private final GameBoxScoreRepository boxScoreRepository;
    private final DeepSeekClient deepSeekClient;
    private final AiGroundingValidator groundingValidator;
    private final PlayByPlayTranslationService translationService;

    public List<TimelineEventDto> getTimeline(String gameId, Integer period) {
        List<PlayByPlay> events;
        if (period != null) {
            events = playByPlayRepository.findByGameIdAndPeriodOrderByEventOrderAsc(gameId, period);
        } else {
            events = playByPlayRepository.findByGameIdOrderByEventOrderAsc(gameId);
        }
        return toTimelineDtos(events);
    }

    public List<MomentumDto> getMomentum(String gameId) {
        return momentumRepository.findByGameIdOrderByEventOrderAsc(gameId)
                .stream().map(this::toMomentumDto).collect(Collectors.toList());
    }

    public List<HighlightDto> getHighlights(String gameId) {
        return toHighlightDtos(highlightRepository.findByGameIdOrderByPeriodAscGameClockDesc(gameId));
    }

    public List<TurningPointDto> getTurningPoints(String gameId) {
        return toTurningPointDtos(turningPointRepository.findByGameIdOrderByPeriodAscGameClockDesc(gameId));
    }

    public GameSummaryDto getSummary(String gameId) {
        return generateSummary(gameId);
    }

    public GameSummaryDto generateSummary(String gameId) {
        List<PlayByPlay> events = playByPlayRepository.findByGameIdOrderByEventOrderAsc(gameId);
        if (events.isEmpty()) {
            return new GameSummaryDto(gameId, "FULL", null, null, null, null, null);
        }

        List<TimelineEventDto> timeline = toTimelineDtos(events);
        String factsJson = buildGameFactsJson(gameId, timeline);
        String factsHash = sha256(factsJson);
        String modelName = deepSeekClient.configuredModel();

        var cached = summaryRepository
                .findFirstByGameIdAndSummaryTypeAndFactsHashAndPromptVersionAndModelNameOrderByCreatedAtDesc(
                        gameId, "FULL", factsHash, PROMPT_VERSION, modelName)
                .filter(this::hasGroundingCitations);
        if (cached.isPresent()) {
            return toSummaryDto(cached.get());
        }

        DeepSeekClient.ChatResponse resp = deepSeekClient.chat(
                ReplayPrompts.SYSTEM_PROMPT,
                ReplayPrompts.buildSummaryPrompt(factsJson));
        String responseModel = resp.model() == null ? modelName : resp.model();

        if (!resp.success()) {
            log.warn("DeepSeek summary failed for gameId={}: {}", gameId, resp.error());
            return new GameSummaryDto(gameId, "FULL", null, null, null, "deepseek", responseModel);
        }

        AiGroundingValidator.ValidationResult validation =
                groundingValidator.validate(resp.content(), timeline);
        if (!validation.valid()) {
            return new GameSummaryDto(
                    gameId,
                    "FULL",
                    sanitizeAiText(validation.safeResponse()),
                    null,
                    null,
                    "deepseek",
                    responseModel);
        }

        AiGameSummary summary = new AiGameSummary();
        summary.setGameId(gameId);
        summary.setSummaryType("FULL");
        summary.setModelProvider("deepseek");
        summary.setModelName(responseModel);
        summary.setPromptVersion(PROMPT_VERSION);
        summary.setFactsHash(factsHash);
        summary.setContent(sanitizeAiText(validation.safeResponse()));
        summary.setCitedEventIdsJson(validation.citedEventIdsJson());
        summary.setCitedStatKeysJson(validation.citedStatKeysJson());
        summary.setCreatedAt(LocalDateTime.now());
        summaryRepository.save(summary);

        return toSummaryDto(summary);
    }

    public EventExplainDto explainEvent(String gameId, Long eventId) {
        PlayByPlay event = playByPlayRepository.findByIdAndGameId(eventId, gameId).orElse(null);
        if (event == null) {
            return null;
        }

        List<PlayByPlay> allEvents = playByPlayRepository.findByGameIdOrderByEventOrderAsc(gameId);
        int idx = -1;
        for (int i = 0; i < allEvents.size(); i++) {
            if (Objects.equals(allEvents.get(i).getId(), eventId)) {
                idx = i;
                break;
            }
        }

        int from = idx < 0 ? 0 : Math.max(0, idx - 5);
        int to = idx < 0 ? Math.min(allEvents.size(), 1) : Math.min(allEvents.size(), idx + 6);
        List<PlayByPlay> contextEvents = allEvents.subList(from, to);
        if (contextEvents.stream().noneMatch(item -> Objects.equals(item.getId(), eventId))) {
            contextEvents = List.of(event);
        }

        TimelineEventDto targetDto = toTranslatedTimelineDto(event);
        List<TimelineEventDto> contextDtos = toTimelineDtos(contextEvents);
        String eventJson = toFactObject(targetDto).toString();
        String contextJson = new JSONArray(contextDtos.stream().map(this::toFactObject).toList()).toString();
        String factsHash = sha256(eventJson + contextJson);
        String modelName = deepSeekClient.configuredModel();

        var cached = explanationRepository
                .findFirstByGameIdAndEventIdAndFactsHashAndPromptVersionAndModelNameOrderByCreatedAtDesc(
                        gameId, eventId, factsHash, PROMPT_VERSION, modelName)
                .filter(this::hasGroundingCitations);
        if (cached.isPresent()) {
            AiReplayExplanation e = cached.get();
            return new EventExplainDto(gameId, eventId, targetDto,
                    sanitizeAiText(e.getExplanation()), e.getCitedEventIdsJson(), e.getCitedStatKeysJson(),
                    e.getModelProvider(), e.getModelName());
        }

        DeepSeekClient.ChatResponse resp = deepSeekClient.chat(
                ReplayPrompts.SYSTEM_PROMPT,
                ReplayPrompts.buildExplainPrompt(eventJson, contextJson));
        String responseModel = resp.model() == null ? modelName : resp.model();

        String explanation;
        String citedEventIdsJson;
        String citedStatKeysJson = null;
        String modelProvider = "deepseek";
        if (resp.success()) {
            AiGroundingValidator.ValidationResult validation =
                    groundingValidator.validate(resp.content(), contextDtos);
            if (validation.valid()) {
                explanation = sanitizeAiText(validation.safeResponse());
                citedEventIdsJson = validation.citedEventIdsJson();
                citedStatKeysJson = validation.citedStatKeysJson();
            } else {
                explanation = buildFallbackExplanation(event, contextDtos);
                citedEventIdsJson = fallbackCitation(eventId);
                responseModel = "fallback";
                modelProvider = "local";
            }
        } else {
            log.warn("DeepSeek explain failed for eventId={}: {}, using fallback", eventId, resp.error());
            explanation = buildFallbackExplanation(event, contextDtos);
            citedEventIdsJson = fallbackCitation(eventId);
            responseModel = "fallback";
            modelProvider = "local";
        }

        AiReplayExplanation explanationEntity = new AiReplayExplanation();
        explanationEntity.setGameId(gameId);
        explanationEntity.setEventId(eventId);
        explanationEntity.setModelProvider(modelProvider);
        explanationEntity.setModelName(responseModel);
        explanationEntity.setPromptVersion(PROMPT_VERSION);
        explanationEntity.setFactsHash(factsHash);
        explanationEntity.setExplanation(explanation);
        explanationEntity.setCitedEventIdsJson(citedEventIdsJson);
        explanationEntity.setCitedStatKeysJson(citedStatKeysJson);
        explanationEntity.setCreatedAt(LocalDateTime.now());
        explanationRepository.save(explanationEntity);

        return new EventExplainDto(gameId, eventId, targetDto,
                explanation, citedEventIdsJson, citedStatKeysJson, modelProvider, responseModel);
    }

    private String buildGameFactsJson(String gameId, List<TimelineEventDto> timeline) {
        JSONArray eventsArr = new JSONArray(timeline.stream().map(this::toFactObject).toList());
        JSONArray boxArr = new JSONArray();
        for (GameBoxScore boxScore : boxScoreRepository.findByGameId(gameId)) {
            JSONObject obj = new JSONObject();
            obj.put("player", boxScore.getPlayerName());
            obj.put("team", boxScore.getTeamName());
            obj.put("pts", boxScore.getPoints());
            obj.put("reb", boxScore.getRebounds());
            obj.put("ast", boxScore.getAssists());
            obj.put("min", boxScore.getMinutes());
            boxArr.put(obj);
        }

        JSONObject root = new JSONObject();
        root.put("gameId", gameId);
        root.put("timeline", eventsArr);
        root.put("boxScore", boxArr);
        return root.toString();
    }

    private JSONObject toFactObject(TimelineEventDto event) {
        JSONObject obj = new JSONObject();
        obj.put("citationKey", event.id() == null ? null : "event:" + event.id());
        obj.put("eventId", event.id());
        obj.put("eventOrder", event.eventOrder());
        obj.put("period", event.period());
        obj.put("clock", event.gameClock());
        obj.put("type", event.eventType());
        obj.put("actionType", event.actionType());
        obj.put("player", event.playerName());
        obj.put("team", event.teamName());
        obj.put("description", event.description());
        obj.put("homeScore", event.homeScore());
        obj.put("awayScore", event.awayScore());
        obj.put("scoreMargin", event.scoreMargin());
        obj.put("source", event.source());
        return obj;
    }

    private boolean hasGroundingCitations(AiGameSummary summary) {
        return hasNonEmptyJsonArray(summary.getCitedEventIdsJson())
                || hasNonEmptyJsonArray(summary.getCitedStatKeysJson());
    }

    private boolean hasGroundingCitations(AiReplayExplanation explanation) {
        return hasNonEmptyJsonArray(explanation.getCitedEventIdsJson())
                || hasNonEmptyJsonArray(explanation.getCitedStatKeysJson());
    }

    private boolean hasNonEmptyJsonArray(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            return new JSONArray(value).length() > 0;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private String fallbackCitation(Long eventId) {
        return new JSONArray(List.of(eventId)).toString();
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
        return text.replaceAll("```\\w*\\n?", "")
                .replace("```", "")
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("\\*(.+?)\\*", "$1")
                .replaceAll("(?m)^\\s*[-*]\\s+", "")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String buildFallbackExplanation(PlayByPlay event, List<TimelineEventDto> context) {
        String translated = safeText(translationService.translateText(event.getDescription()));
        String player = safeText(translationService.translateText(event.getPlayerName()));
        StringBuilder sb = new StringBuilder();
        sb.append("第").append(event.getPeriod()).append("节 ")
                .append(event.getGameClock()).append("，");
        if (!player.isBlank()) {
            sb.append(player).append(" ");
        }
        sb.append(translated).append("。当前比分 ")
                .append(event.getHomeScore()).append("-").append(event.getAwayScore()).append("。");
        if (context != null && context.size() > 1) {
            long scoringCount = context.stream().filter(TimelineEventDto::isScoringPlay).count();
            if (scoringCount > 0) {
                sb.append("附近片段内共有 ").append(scoringCount).append(" 次得分事件。");
            }
        }
        return sb.toString();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private List<TimelineEventDto> toTimelineDtos(List<PlayByPlay> events) {
        List<String> originals = events.stream().map(PlayByPlay::getDescription).collect(Collectors.toList());
        List<String> descriptions = translationService.translateTexts(originals);
        if (descriptions == null || descriptions.size() != events.size()) {
            descriptions = originals;
        }
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
                    h.getPlayerId(), h.getTeamId(), h.getScoreAfter(), h.getEventId()));
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

    private GameSummaryDto toSummaryDto(AiGameSummary summary) {
        return new GameSummaryDto(
                summary.getGameId(),
                summary.getSummaryType(),
                sanitizeAiText(summary.getContent()),
                summary.getCitedEventIdsJson(),
                summary.getCitedStatKeysJson(),
                summary.getModelProvider(),
                summary.getModelName());
    }
}
