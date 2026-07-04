package com.nbamanager.repository;

import com.nbamanager.domain.AiReplayExplanation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiReplayExplanationRepository extends JpaRepository<AiReplayExplanation, Long> {
    Optional<AiReplayExplanation> findByGameIdAndEventId(String gameId, Long eventId);
}
