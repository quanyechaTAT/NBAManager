package com.nbamanager.service;

import com.nbamanager.domain.AiGameSummary;
import com.nbamanager.domain.GameHighlight;
import com.nbamanager.domain.GameMomentumSnapshot;
import com.nbamanager.domain.GameTurningPoint;
import com.nbamanager.domain.MatchRecord;
import com.nbamanager.domain.PlayByPlay;
import com.nbamanager.domain.replay.ReplayPageState;
import com.nbamanager.domain.replay.ReplayQualityLevel;
import com.nbamanager.domain.replay.ReplaySyncState;
import com.nbamanager.repository.AiGameSummaryRepository;
import com.nbamanager.repository.GameHighlightRepository;
import com.nbamanager.repository.GameMomentumSnapshotRepository;
import com.nbamanager.repository.GameTurningPointRepository;
import com.nbamanager.repository.MatchRecordRepository;
import com.nbamanager.repository.PlayByPlayRepository;
import com.nbamanager.repository.ReplaySyncStateRepository;
import com.nbamanager.web.dto.GameSummaryDto;
import com.nbamanager.web.dto.HighlightDto;
import com.nbamanager.web.dto.MomentumDto;
import com.nbamanager.web.dto.ReplayBootstrapDto;
import com.nbamanager.web.dto.ReplayErrorDto;
import com.nbamanager.web.dto.ReplayQualityDto;
import com.nbamanager.web.dto.TimelineEventDto;
import com.nbamanager.web.dto.TurningPointDto;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReplayReadService {

    private static final Pattern GAME_ID_PATTERN = Pattern.compile("^[0-9]{9,10}$");

    private final MatchRecordRepository matchRepository;
    private final PlayByPlayRepository playByPlayRepository;
    private final GameMomentumSnapshotRepository momentumRepository;
    private final GameHighlightRepository highlightRepository;
    private final GameTurningPointRepository turningPointRepository;
    private final AiGameSummaryRepository summaryRepository;
    private final ReplaySyncStateRepository syncStateRepository;
    private final ReplayQualityService qualityService;

    @Transactional(readOnly = true)
    public ReplayBootstrapDto bootstrap(String gameId) {
        if (!isValidGameId(gameId)) {
            return emptyBootstrap(
                    gameId == null ? "" : gameId,
                    ReplayPageState.UNAVAILABLE,
                    unavailableQuality(null, null),
                    new ReplayErrorDto(
                            "REPLAY_INVALID_GAME_ID",
                            "The game identifier is invalid.",
                            false,
                            null,
                            null));
        }

        Optional<MatchRecord> matchOptional = matchRepository.findByNbaGameId(gameId);
        if (matchOptional.isEmpty()) {
            return emptyBootstrap(
                    gameId,
                    ReplayPageState.UNAVAILABLE,
                    unavailableQuality(null, null),
                    new ReplayErrorDto(
                            "REPLAY_NOT_FOUND",
                            "The requested game was not found.",
                            false,
                            null,
                            null));
        }

        MatchRecord match = matchOptional.get();
        if ("SCHEDULED".equalsIgnoreCase(match.getStatus())) {
            return emptyBootstrap(
                    gameId,
                    ReplayPageState.NOT_STARTED,
                    unavailableQuality(match, null),
                    null);
        }

        ReplaySyncState syncState = syncStateRepository.findByGameId(gameId).orElse(null);
        List<PlayByPlay> events = safeList(
                playByPlayRepository.findByGameIdOrderByEventOrderAsc(gameId));
        LocalDateTime lastSyncedAt = syncState == null ? null : syncState.getCompletedAt();
        ReplayQualityDto quality = qualityService.evaluate(events, match, lastSyncedAt);

        if (events.isEmpty()) {
            if (hasActiveLease(syncState)) {
                return emptyBootstrap(gameId, ReplayPageState.PREPARING, quality, null);
            }
            return emptyBootstrap(
                    gameId,
                    ReplayPageState.UNAVAILABLE,
                    quality,
                    unavailableError(syncState));
        }

        ReplayPageState state = quality.level() == ReplayQualityLevel.FULL
                ? ReplayPageState.READY
                : ReplayPageState.PARTIAL;
        List<TimelineEventDto> timeline = events.stream().map(this::toTimelineDto).toList();

        if (quality.level() == ReplayQualityLevel.SUMMARY_ONLY) {
            return new ReplayBootstrapDto(
                    gameId,
                    state,
                    quality,
                    timeline,
                    List.of(),
                    List.of(),
                    List.of(),
                    null,
                    null);
        }

        List<MomentumDto> momentum = safeList(
                        momentumRepository.findByGameIdOrderByEventOrderAsc(gameId))
                .stream()
                .map(this::toMomentumDto)
                .toList();
        List<HighlightDto> highlights = safeList(
                        highlightRepository.findByGameIdOrderByPeriodAscGameClockDesc(gameId))
                .stream()
                .map(this::toHighlightDto)
                .toList();
        List<TurningPointDto> turningPoints = safeList(
                        turningPointRepository.findByGameIdOrderByPeriodAscGameClockDesc(gameId))
                .stream()
                .map(this::toTurningPointDto)
                .toList();
        GameSummaryDto cachedSummary = quality.level() == ReplayQualityLevel.FULL
                ? findCurrentSummary(gameId, quality.factsHash())
                : null;

        return new ReplayBootstrapDto(
                gameId,
                state,
                quality,
                timeline,
                momentum,
                highlights,
                turningPoints,
                cachedSummary,
                null);
    }

    private boolean isValidGameId(String gameId) {
        return gameId != null && GAME_ID_PATTERN.matcher(gameId).matches();
    }

    private ReplayQualityDto unavailableQuality(MatchRecord match, LocalDateTime lastSyncedAt) {
        return qualityService.evaluate(List.of(), match, lastSyncedAt);
    }

    private boolean hasActiveLease(ReplaySyncState syncState) {
        return syncState != null
                && syncState.getStatus() == ReplayPageState.PREPARING
                && syncState.getLeaseExpiresAt() != null
                && syncState.getLeaseExpiresAt().isAfter(LocalDateTime.now());
    }

    private ReplayErrorDto unavailableError(ReplaySyncState syncState) {
        if (syncState == null) {
            return new ReplayErrorDto(
                    "REPLAY_SOURCE_UNAVAILABLE",
                    "Replay data is currently unavailable.",
                    true,
                    null,
                    null);
        }
        String code = safeErrorCode(syncState.getLastErrorCode());
        return new ReplayErrorDto(
                code,
                safeErrorMessage(code),
                isRetryable(code),
                syncState.getLastErrorAt() != null
                        ? syncState.getLastErrorAt()
                        : syncState.getCompletedAt(),
                syncState.getNextRetryAt());
    }

    private String safeErrorCode(String code) {
        if (code == null) {
            return "REPLAY_SOURCE_UNAVAILABLE";
        }
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "REPLAY_SOURCE_UNAVAILABLE",
                    "REPLAY_DATA_REJECTED",
                    "REPLAY_PREPARATION_IN_PROGRESS" -> code.toUpperCase(Locale.ROOT);
            default -> "REPLAY_SOURCE_UNAVAILABLE";
        };
    }

    private String safeErrorMessage(String code) {
        return switch (code) {
            case "REPLAY_DATA_REJECTED" -> "The available replay data did not pass validation.";
            case "REPLAY_PREPARATION_IN_PROGRESS" -> "Replay preparation is still in progress.";
            default -> "Replay data is currently unavailable.";
        };
    }

    private boolean isRetryable(String code) {
        return !"REPLAY_DATA_REJECTED".equals(code);
    }

    private ReplayBootstrapDto emptyBootstrap(
            String gameId,
            ReplayPageState state,
            ReplayQualityDto quality,
            ReplayErrorDto error) {
        return new ReplayBootstrapDto(
                gameId,
                state,
                quality,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                error);
    }

    private GameSummaryDto findCurrentSummary(String gameId, String factsHash) {
        if (factsHash == null) {
            return null;
        }
        try {
            return summaryRepository.findByGameIdAndSummaryType(gameId, "FULL")
                    .filter(summary -> Objects.equals(factsHash, summary.getFactsHash()))
                    .filter(summary -> summary.getContent() != null && !summary.getContent().isBlank())
                    .filter(this::hasGroundingCitations)
                    .map(this::toSummaryDto)
                    .orElse(null);
        } catch (DataAccessException exception) {
            // Cached AI is optional. Corrupt legacy cache rows must not take down factual replay data.
            log.warn("Ignoring unreadable replay summary cache for gameId={}: {}",
                    gameId, exception.getClass().getSimpleName());
            return null;
        }
    }

    private boolean hasGroundingCitations(AiGameSummary summary) {
        return hasNonEmptyJsonArray(summary.getCitedEventIdsJson())
                || hasNonEmptyJsonArray(summary.getCitedStatKeysJson());
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

    private TimelineEventDto toTimelineDto(PlayByPlay event) {
        String description = event.getDescription();
        return new TimelineEventDto(
                event.getId(),
                event.getPeriod(),
                event.getGameClock(),
                event.getEventType(),
                event.getActionType(),
                description,
                description,
                description,
                event.getPlayerName(),
                event.getPlayerId(),
                event.getTeamName(),
                event.getTeamId(),
                event.getHomeScore(),
                event.getAwayScore(),
                event.getScoreMargin(),
                event.getIsScoringPlay(),
                event.getIsHighlightCandidate(),
                event.getEventOrder(),
                event.getSource());
    }

    private MomentumDto toMomentumDto(GameMomentumSnapshot snapshot) {
        return new MomentumDto(
                snapshot.getId(),
                snapshot.getEventOrder(),
                snapshot.getPeriod(),
                snapshot.getGameClock(),
                snapshot.getHomeScore(),
                snapshot.getAwayScore(),
                snapshot.getScoreMargin());
    }

    private HighlightDto toHighlightDto(GameHighlight highlight) {
        return new HighlightDto(
                highlight.getId(),
                highlight.getPeriod(),
                highlight.getGameClock(),
                highlight.getHighlightType(),
                highlight.getTitle(),
                highlight.getDescription(),
                highlight.getPlayerId(),
                highlight.getTeamId(),
                highlight.getScoreAfter(),
                highlight.getEventId());
    }

    private TurningPointDto toTurningPointDto(GameTurningPoint point) {
        return new TurningPointDto(
                point.getId(),
                point.getPeriod(),
                point.getGameClock(),
                point.getType(),
                point.getConfidence(),
                point.getFactualReason(),
                point.getAiExplanation(),
                point.getCitedEventIdsJson(),
                point.getStartEventOrder(),
                point.getEndEventOrder(),
                point.getStartHomeScore(),
                point.getStartAwayScore(),
                point.getEndHomeScore(),
                point.getEndAwayScore(),
                point.getScoreSwing(),
                point.getImpactScore(),
                point.getImpactLevel(),
                point.getTeamName());
    }

    private GameSummaryDto toSummaryDto(AiGameSummary summary) {
        return new GameSummaryDto(
                summary.getGameId(),
                summary.getSummaryType(),
                summary.getContent(),
                summary.getCitedEventIdsJson(),
                summary.getCitedStatKeysJson(),
                summary.getModelProvider(),
                summary.getModelName());
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
