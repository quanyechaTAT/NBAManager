package com.nbamanager.repository;

import com.nbamanager.domain.AiGameSummary;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiGameSummaryRepository extends JpaRepository<AiGameSummary, Long> {
    Optional<AiGameSummary> findByGameIdAndSummaryType(String gameId, String summaryType);
}
