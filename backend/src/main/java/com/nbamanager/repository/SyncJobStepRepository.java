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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SyncJobStep s WHERE s.id = :id")
    Optional<SyncJobStep> findByIdForUpdate(@Param("id") Long id);
}
