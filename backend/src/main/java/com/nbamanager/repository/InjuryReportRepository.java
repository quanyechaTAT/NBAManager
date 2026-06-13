package com.nbamanager.repository;

import com.nbamanager.domain.InjuryReport;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InjuryReportRepository extends JpaRepository<InjuryReport, Long> {

    List<InjuryReport> findByPlayerId(Long playerId);

    List<InjuryReport> findByTeamName(String teamName);

    List<InjuryReport> findByStatus(String status);

    List<InjuryReport> findByPlayerIdAndStatus(Long playerId, String status);
}
