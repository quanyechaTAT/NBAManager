package com.nbamanager.repository;

import com.nbamanager.domain.SyncJobStep;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SyncJobStepRepository extends JpaRepository<SyncJobStep, Long> {

    List<SyncJobStep> findByJobIdOrderByStepOrder(Long jobId);

    @Query("SELECT s FROM SyncJobStep s WHERE s.status = 'SUCCESS' AND s.finishedAt IS NOT NULL "
            + "AND NOT EXISTS (SELECT newer.id FROM SyncJobStep newer WHERE newer.module = s.module "
            + "AND newer.status = 'SUCCESS' AND (newer.finishedAt > s.finishedAt "
            + "OR (newer.finishedAt = s.finishedAt AND newer.id > s.id)))")
    List<SyncJobStep> findLatestSuccessfulSteps();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SyncJobStep s WHERE s.id = :id")
    Optional<SyncJobStep> findByIdForUpdate(@Param("id") Long id);
}
