package com.nbamanager.service;

import com.nbamanager.domain.GameTurningPoint;
import com.nbamanager.domain.PlayByPlay;
import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.repository.GameTurningPointRepository;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.json.JSONArray;

@Service
@RequiredArgsConstructor
@Slf4j
public class TurningPointDetectService {

    private final GameTurningPointRepository repository;

    private static final int MAX_RESULTS = 6;
    private static final int RUN_MIN_POINTS = 8;
    private static final int RUN_NET_MIN = 6;
    private static final int RUN_MAX_EVENTS = 20;

    @Transactional
    public List<GameTurningPoint> detect(String gameId, List<PlayByPlayEvent> events) {
        return detect(gameId, events, List.of());
    }

    @Transactional
    public List<GameTurningPoint> detect(String gameId, List<PlayByPlayEvent> events,
                                         List<PlayByPlay> persistedFacts) {
        repository.deleteByGameId(gameId);

        if (events == null || events.size() < 4) {
            return List.of();
        }

        List<GameTurningPoint> candidates = new ArrayList<>();
        candidates.addAll(detectScoringRuns(gameId, events));
        candidates.addAll(detectLeadChanges(gameId, events));
        candidates.addAll(detectClutchMoments(gameId, events));
        candidates.addAll(detectMaxMarginSwing(gameId, events));

        // Deduplicate, rank, limit
        List<GameTurningPoint> result = deduplicate(candidates);
        result = result.stream()
                .sorted(Comparator.comparingDouble(GameTurningPoint::getImpactScore).reversed())
                .limit(MAX_RESULTS)
                .collect(Collectors.toList());

        // Re-sort by event order for display
        result.sort(Comparator.comparingInt(GameTurningPoint::getStartEventOrder));

        attachEventReferences(result, persistedFacts);
        repository.saveAll(result);
        log.info("Detected {} turning points for gameId={}", result.size(), gameId);
        return result;
    }

    private void attachEventReferences(List<GameTurningPoint> points, List<PlayByPlay> persistedFacts) {
        if (points == null || points.isEmpty() || persistedFacts == null || persistedFacts.isEmpty()) {
            return;
        }
        Map<Integer, Long> idsByOrder = new HashMap<>();
        for (PlayByPlay fact : persistedFacts) {
            if (fact != null && fact.getEventOrder() != null && fact.getId() != null) {
                idsByOrder.put(fact.getEventOrder(), fact.getId());
            }
        }
        for (GameTurningPoint point : points) {
            Long endId = idsByOrder.get(point.getEndEventOrder());
            point.setEventId(endId);
            JSONArray cited = new JSONArray();
            int start = point.getStartEventOrder() == null ? Integer.MIN_VALUE : point.getStartEventOrder();
            int end = point.getEndEventOrder() == null ? start : point.getEndEventOrder();
            idsByOrder.entrySet().stream()
                    .filter(entry -> entry.getKey() >= start && entry.getKey() <= end)
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> cited.put(entry.getValue()));
            if (cited.length() > 0) {
                point.setCitedEventIdsJson(cited.toString());
            }
        }
    }

    // --- Run Detection (sliding window) ---
    private List<GameTurningPoint> detectScoringRuns(String gameId, List<PlayByPlayEvent> events) {
        List<GameTurningPoint> points = new ArrayList<>();
        List<PlayByPlayEvent> scoringEvents = new ArrayList<>();
        for (PlayByPlayEvent e : events) {
            if (e.isScoringPlay()) scoringEvents.add(e);
        }

        for (int i = 0; i < scoringEvents.size(); i++) {
            PlayByPlayEvent start = scoringEvents.get(i);
            for (int j = i + 1; j < Math.min(i + RUN_MAX_EVENTS, scoringEvents.size()); j++) {
                PlayByPlayEvent end = scoringEvents.get(j);
                int homeRun = end.homeScore() - start.homeScore();
                int awayRun = end.awayScore() - start.awayScore();
                int homeNet = homeRun - awayRun;
                int awayNet = awayRun - homeRun;

                if (homeRun >= RUN_MIN_POINTS && homeNet >= RUN_NET_MIN) {
                    points.add(createRunPoint(gameId, start, end, homeRun, awayRun, true));
                    break;
                }
                if (awayRun >= RUN_MIN_POINTS && awayNet >= RUN_NET_MIN) {
                    points.add(createRunPoint(gameId, start, end, awayRun, homeRun, false));
                    break;
                }
            }
        }
        return points;
    }

    private GameTurningPoint createRunPoint(String gameId, PlayByPlayEvent start, PlayByPlayEvent end,
                                            int runFor, int runAgainst, boolean isHome) {
        GameTurningPoint tp = new GameTurningPoint();
        tp.setGameId(gameId);
        tp.setPeriod(end.period());
        tp.setGameClock(end.gameClock());
        tp.setType("RUN");
        tp.setStartEventOrder(start.eventOrder());
        tp.setEndEventOrder(end.eventOrder());
        tp.setStartHomeScore(start.homeScore());
        tp.setStartAwayScore(start.awayScore());
        tp.setEndHomeScore(end.homeScore());
        tp.setEndAwayScore(end.awayScore());
        tp.setScoreSwing(Math.abs(runFor - runAgainst));
        tp.setTeamName(isHome ? "主队" : "客队");

        String reason = (isHome ? "主队" : "客队") + "打出 " + runFor + "-" + runAgainst + " 攻击波";
        tp.setFactualReason(reason);

        int impactScore = calculateRunImpact(start, end, runFor, runAgainst, isHome);
        tp.setImpactScore((double) impactScore);
        tp.setImpactLevel(impactScore >= 50 ? "HIGH" : impactScore >= 30 ? "MEDIUM" : "LOW");
        tp.setConfidence(Math.min(0.98, 0.65 + impactScore / 100.0));
        tp.setAiExplanation(buildRunExplanation(start, end, runFor, runAgainst, isHome));
        tp.setCreatedAt(LocalDateTime.now());
        return tp;
    }

    // --- Lead Change Detection ---
    private List<GameTurningPoint> detectLeadChanges(String gameId, List<PlayByPlayEvent> events) {
        List<GameTurningPoint> points = new ArrayList<>();
        for (int i = 1; i < events.size(); i++) {
            PlayByPlayEvent prev = events.get(i - 1);
            PlayByPlayEvent curr = events.get(i);
            if (!curr.isScoringPlay()) continue;

            int prevMargin = prev.homeScore() - prev.awayScore();
            int currMargin = curr.homeScore() - curr.awayScore();

            boolean isTie = prevMargin != 0 && currMargin == 0;
            boolean isLeadChange = (prevMargin < 0 && currMargin > 0) || (prevMargin > 0 && currMargin < 0);

            if (isTie || isLeadChange) {
                GameTurningPoint tp = new GameTurningPoint();
                tp.setGameId(gameId);
                tp.setPeriod(curr.period());
                tp.setGameClock(curr.gameClock());
                tp.setType(isLeadChange ? "LEAD_CHANGE" : "TIE");
                tp.setStartEventOrder(prev.eventOrder());
                tp.setEndEventOrder(curr.eventOrder());
                tp.setStartHomeScore(prev.homeScore());
                tp.setStartAwayScore(prev.awayScore());
                tp.setEndHomeScore(curr.homeScore());
                tp.setEndAwayScore(curr.awayScore());
                tp.setScoreSwing(Math.abs(currMargin - prevMargin));

                int impactScore = calculateLeadChangeImpact(curr, isLeadChange);
                tp.setImpactScore((double) impactScore);
                tp.setImpactLevel(impactScore >= 40 ? "HIGH" : impactScore >= 20 ? "MEDIUM" : "LOW");
                tp.setConfidence(Math.min(0.98, 0.65 + impactScore / 100.0));
                tp.setFactualReason(isLeadChange ? "比分反超" : "比分追平");
                tp.setAiExplanation(buildLeadChangeExplanation(prev, curr, isLeadChange));
                tp.setCreatedAt(LocalDateTime.now());
                points.add(tp);
            }
        }
        return points;
    }

    // --- Clutch Moment Detection ---
    private List<GameTurningPoint> detectClutchMoments(String gameId, List<PlayByPlayEvent> events) {
        List<GameTurningPoint> points = new ArrayList<>();
        for (PlayByPlayEvent e : events) {
            if (!e.isScoringPlay()) continue;
            if (e.period() < 4) continue;

            int clockSec = clockToSeconds(e.gameClock());
            if (clockSec > 300) continue;

            int margin = Math.abs(e.homeScore() - e.awayScore());
            if (margin > 8) continue;

            String desc = (e.description() != null ? e.description() : "").toLowerCase();
            int bonus = 0;
            if (desc.contains("3pt") || desc.contains("three")) bonus += 15;
            if (desc.contains("dunk")) bonus += 10;
            if (margin == 0) bonus += 30; // tie
            if (clockSec <= 60) bonus += 20;

            int impactScore = 20 + bonus;
            GameTurningPoint tp = new GameTurningPoint();
            tp.setGameId(gameId);
            tp.setPeriod(e.period());
            tp.setGameClock(e.gameClock());
            tp.setType("CLUTCH_SCORE");
            tp.setStartEventOrder(e.eventOrder());
            tp.setEndEventOrder(e.eventOrder());
            tp.setStartHomeScore(e.homeScore());
            tp.setStartAwayScore(e.awayScore());
            tp.setEndHomeScore(e.homeScore());
            tp.setEndAwayScore(e.awayScore());
            tp.setScoreSwing(0);
            tp.setImpactScore((double) impactScore);
            tp.setImpactLevel(impactScore >= 40 ? "HIGH" : "MEDIUM");
            tp.setConfidence(Math.min(0.98, 0.65 + impactScore / 100.0));
            tp.setFactualReason("关键时刻得分");
            tp.setAiExplanation("第四节最后5分钟，分差仅" + margin + "分时的关键得分。");
            tp.setCreatedAt(LocalDateTime.now());
            points.add(tp);
        }
        return points;
    }

    // --- Max Margin Swing ---
    private List<GameTurningPoint> detectMaxMarginSwing(String gameId, List<PlayByPlayEvent> events) {
        List<GameTurningPoint> points = new ArrayList<>();
        int maxMargin = 0;
        PlayByPlayEvent maxEvent = null;
        for (PlayByPlayEvent e : events) {
            int margin = Math.abs(e.homeScore() - e.awayScore());
            if (margin > maxMargin) {
                maxMargin = margin;
                maxEvent = e;
            }
        }
        if (maxEvent != null && maxMargin >= 15) {
            GameTurningPoint tp = new GameTurningPoint();
            tp.setGameId(gameId);
            tp.setPeriod(maxEvent.period());
            tp.setGameClock(maxEvent.gameClock());
            tp.setType("MARGIN_BREAK");
            tp.setStartEventOrder(maxEvent.eventOrder());
            tp.setEndEventOrder(maxEvent.eventOrder());
            tp.setStartHomeScore(maxEvent.homeScore());
            tp.setStartAwayScore(maxEvent.awayScore());
            tp.setEndHomeScore(maxEvent.homeScore());
            tp.setEndAwayScore(maxEvent.awayScore());
            tp.setScoreSwing(maxMargin);
            tp.setImpactScore((double) (maxMargin * 3));
            tp.setImpactLevel(maxMargin >= 20 ? "HIGH" : "MEDIUM");
            tp.setConfidence(Math.min(0.98, 0.65 + maxMargin * 3.0 / 100.0));
            tp.setFactualReason("建立全场最大 " + maxMargin + " 分领先");
            tp.setAiExplanation("分差扩大到" + maxMargin + "分，是比赛走势的阶段性高点。");
            tp.setCreatedAt(LocalDateTime.now());
            points.add(tp);
        }
        return points;
    }

    // --- Deduplication ---
    private List<GameTurningPoint> deduplicate(List<GameTurningPoint> points) {
        List<GameTurningPoint> sorted = points.stream()
                .sorted(Comparator.comparingDouble(GameTurningPoint::getImpactScore).reversed())
                .collect(Collectors.toList());

        List<GameTurningPoint> result = new ArrayList<>();
        for (GameTurningPoint p : sorted) {
            boolean dup = result.stream().anyMatch(existing ->
                    existing.getType().equals(p.getType())
                    && existing.getPeriod() == p.getPeriod()
                    && existing.getTeamName() != null && existing.getTeamName().equals(p.getTeamName())
                    && Math.abs(existing.getEndEventOrder() - p.getEndEventOrder()) <= 5
            );
            if (!dup) result.add(p);
        }
        return result;
    }

    // --- Impact Scoring ---
    private int calculateRunImpact(PlayByPlayEvent start, PlayByPlayEvent end, int runFor, int runAgainst, boolean isHome) {
        int netSwing = runFor - runAgainst;
        int score = netSwing * 8 + runFor * 2;
        score += periodWeight(end.period());
        score += clutchWeight(end.period(), end.gameClock());
        return score;
    }

    private int calculateLeadChangeImpact(PlayByPlayEvent event, boolean isLeadChange) {
        int score = isLeadChange ? 30 : 15;
        score += periodWeight(event.period());
        score += clutchWeight(event.period(), event.gameClock());
        return score;
    }

    private int periodWeight(int period) {
        return switch (period) {
            case 1 -> 0; case 2 -> 5; case 3 -> 10; case 4 -> 20;
            default -> 25; // OT
        };
    }

    private int clutchWeight(int period, String gameClock) {
        if (period < 4) return 0;
        int sec = clockToSeconds(gameClock);
        if (sec <= 120) return 35;
        if (sec <= 300) return 20;
        return 0;
    }

    // --- Rule-based Explanations ---
    private String buildRunExplanation(PlayByPlayEvent start, PlayByPlayEvent end, int runFor, int runAgainst, boolean isHome) {
        String team = isHome ? "主队" : "客队";
        return String.format("这段 %d-%d 攻击波让%s净胜 %d 分，在第%d节 %s 时段改变了比赛走势。",
                runFor, runAgainst, team, runFor - runAgainst, end.period(), end.gameClock());
    }

    private String buildLeadChangeExplanation(PlayByPlayEvent prev, PlayByPlayEvent curr, boolean isLeadChange) {
        return String.format("第%d节 %s，比分从 %d-%d 变为 %d-%d，%s。",
                curr.period(), curr.gameClock(),
                prev.homeScore(), prev.awayScore(),
                curr.homeScore(), curr.awayScore(),
                isLeadChange ? "完成反超" : "比分追平");
    }

    private int clockToSeconds(String gameClock) {
        if (gameClock == null || gameClock.isEmpty()) return 999;
        try {
            String[] parts = gameClock.split(":");
            return Integer.parseInt(parts[0].trim()) * 60 + Integer.parseInt(parts[1].trim());
        } catch (Exception e) {
            return 999;
        }
    }
}
