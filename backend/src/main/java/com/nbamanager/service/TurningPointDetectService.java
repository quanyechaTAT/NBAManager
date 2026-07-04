package com.nbamanager.service;

import com.nbamanager.domain.GameTurningPoint;
import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.repository.GameTurningPointRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TurningPointDetectService {

    private final GameTurningPointRepository repository;

    @Transactional
    public List<GameTurningPoint> detect(String gameId, List<PlayByPlayEvent> events) {
        repository.deleteByGameId(gameId);

        List<GameTurningPoint> points = new ArrayList<>();
        points.addAll(detectRuns(gameId, events));
        points.addAll(detectOvertakes(gameId, events));
        points.addAll(detectClutchSeparations(gameId, events));

        return repository.saveAll(points);
    }

    private List<GameTurningPoint> detectRuns(String gameId, List<PlayByPlayEvent> events) {
        List<GameTurningPoint> runs = new ArrayList<>();

        for (int i = 0; i < events.size(); i++) {
            PlayByPlayEvent startEvent = events.get(i);
            int startHome = startEvent.homeScore();
            int startAway = startEvent.awayScore();

            for (int j = i + 1; j < events.size(); j++) {
                PlayByPlayEvent endEvent = events.get(j);
                int homeDiff = endEvent.homeScore() - startHome;
                int awayDiff = endEvent.awayScore() - startAway;

                boolean homeRun = homeDiff >= 8 && awayDiff == 0;
                boolean awayRun = awayDiff >= 8 && homeDiff == 0;

                if (homeRun || awayRun) {
                    GameTurningPoint point = new GameTurningPoint();
                    point.setGameId(gameId);
                    point.setEventId(Long.parseLong(endEvent.sourceEventId()));
                    point.setPeriod(endEvent.period());
                    point.setGameClock(endEvent.gameClock());
                    point.setType("RUN");
                    point.setConfidence(0.9);
                    if (homeRun) {
                        point.setFactualReason("主队打出 " + homeDiff + "-0 攻击波");
                    } else {
                        point.setFactualReason("客队打出 " + awayDiff + "-0 攻击波");
                    }
                    point.setCitedEventIdsJson("[\"" + startEvent.sourceEventId()
                            + "\",\"" + endEvent.sourceEventId() + "\"]");
                    point.setCreatedAt(LocalDateTime.now());
                    runs.add(point);
                    break; // Only one run per starting position
                }
            }
        }

        return runs;
    }

    private List<GameTurningPoint> detectOvertakes(String gameId, List<PlayByPlayEvent> events) {
        List<GameTurningPoint> overtakePoints = new ArrayList<>();

        for (int i = 1; i < events.size(); i++) {
            PlayByPlayEvent prev = events.get(i - 1);
            PlayByPlayEvent curr = events.get(i);

            if (curr.period() < 4) {
                continue;
            }

            int prevMargin = prev.scoreMargin();
            int currMargin = curr.scoreMargin();

            // Sign change means overtake (both non-zero)
            if (prevMargin != 0 && currMargin != 0
                    && Integer.signum(prevMargin) != Integer.signum(currMargin)) {

                GameTurningPoint point = new GameTurningPoint();
                point.setGameId(gameId);
                point.setEventId(Long.parseLong(curr.sourceEventId()));
                point.setPeriod(curr.period());
                point.setGameClock(curr.gameClock());
                point.setType("OVERTAKE");
                point.setConfidence(0.95);

                String leadingTeam;
                if (currMargin > 0) {
                    leadingTeam = "主队";
                } else {
                    leadingTeam = "客队";
                }
                point.setFactualReason(leadingTeam + "完成反超，比分变为 "
                        + curr.homeScore() + "-" + curr.awayScore());
                point.setCitedEventIdsJson("[\"" + prev.sourceEventId()
                        + "\",\"" + curr.sourceEventId() + "\"]");
                point.setCreatedAt(LocalDateTime.now());
                overtakePoints.add(point);
            }
        }

        return overtakePoints;
    }

    private List<GameTurningPoint> detectClutchSeparations(String gameId,
            List<PlayByPlayEvent> events) {
        List<GameTurningPoint> separations = new ArrayList<>();

        for (int i = 0; i < events.size(); i++) {
            PlayByPlayEvent event = events.get(i);

            if (event.period() < 4) {
                continue;
            }
            if (!isWithinLast5Minutes(event.gameClock())) {
                continue;
            }

            int margin = Math.abs(event.scoreMargin());
            if (margin < 8) {
                continue;
            }

            // Check if previous events had margin <= 2
            int lookback = Math.min(10, i);
            for (int j = i - lookback; j < i; j++) {
                PlayByPlayEvent prev = events.get(j);
                if (Math.abs(prev.scoreMargin()) <= 2) {
                    GameTurningPoint point = new GameTurningPoint();
                    point.setGameId(gameId);
                    point.setEventId(Long.parseLong(event.sourceEventId()));
                    point.setPeriod(event.period());
                    point.setGameClock(event.gameClock());
                    point.setType("CLUTCH_SEPARATION");
                    point.setConfidence(0.85);
                    point.setFactualReason("关键时刻分差拉开至 " + margin + " 分，比分 "
                            + event.homeScore() + "-" + event.awayScore());
                    point.setCitedEventIdsJson("[\"" + prev.sourceEventId()
                            + "\",\"" + event.sourceEventId() + "\"]");
                    point.setCreatedAt(LocalDateTime.now());
                    separations.add(point);
                    break; // Only one separation per event
                }
            }
        }

        return separations;
    }

    private boolean isWithinLast5Minutes(String gameClock) {
        if (gameClock == null || !gameClock.contains(":")) {
            return false;
        }
        String[] parts = gameClock.split(":");
        try {
            int minutes = Integer.parseInt(parts[0].trim());
            int seconds = Integer.parseInt(parts[1].trim());
            return minutes < 5 || (minutes == 5 && seconds == 0);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
