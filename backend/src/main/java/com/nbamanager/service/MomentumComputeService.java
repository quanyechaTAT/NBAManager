package com.nbamanager.service;

import com.nbamanager.domain.GameMomentumSnapshot;
import com.nbamanager.domain.provider.PlayByPlayEvent;
import com.nbamanager.repository.GameMomentumSnapshotRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MomentumComputeService {

    private final GameMomentumSnapshotRepository repository;

    @Transactional
    public List<GameMomentumSnapshot> compute(String gameId, List<PlayByPlayEvent> events) {
        repository.deleteByGameId(gameId);

        List<GameMomentumSnapshot> snapshots = new ArrayList<>();
        for (PlayByPlayEvent event : events) {
            GameMomentumSnapshot snapshot = new GameMomentumSnapshot();
            snapshot.setGameId(gameId);
            snapshot.setEventOrder(event.eventOrder());
            snapshot.setPeriod(event.period());
            snapshot.setGameClock(event.gameClock());
            snapshot.setHomeScore(event.homeScore());
            snapshot.setAwayScore(event.awayScore());
            snapshot.setScoreMargin(event.scoreMargin());
            snapshot.setCreatedAt(LocalDateTime.now());
            snapshots.add(snapshot);
        }

        repository.saveAll(snapshots);
        return snapshots;
    }
}
