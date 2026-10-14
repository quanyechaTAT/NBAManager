package com.nbamanager.repository;

import com.nbamanager.domain.SyncJob;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SyncJobRepository extends JpaRepository<SyncJob, Long> {

    Optional<SyncJob> findTopByModuleOrderByCreatedAtDesc(String module);

    Optional<SyncJob> findTopByModuleAndStatusOrderByFinishedAtDesc(String module, String status);

    List<SyncJob> findTop20ByOrderByCreatedAtDesc();

    List<SyncJob> findByStatusIn(List<String> statuses);

    @Query(value = "SELECT j.* FROM sync_jobs j JOIN (SELECT id, ROW_NUMBER() OVER "
            + "(PARTITION BY module ORDER BY created_at DESC, id DESC) AS row_rank FROM sync_jobs) ranked "
            + "ON ranked.id = j.id AND ranked.row_rank = 1", nativeQuery = true)
    List<SyncJob> findLatestPerModule();

    @Query(value = "SELECT j.* FROM sync_jobs j JOIN (SELECT id, ROW_NUMBER() OVER "
            + "(PARTITION BY module ORDER BY finished_at DESC, id DESC) AS row_rank FROM sync_jobs "
            + "WHERE status = 'SUCCESS' AND finished_at IS NOT NULL) ranked "
            + "ON ranked.id = j.id AND ranked.row_rank = 1", nativeQuery = true)
    List<SyncJob> findLatestSuccessfulPerModule();

    // Select candidates without locking the status index, then lock by primary key.
    // A range UPDATE deadlocked with workers updating a job already locked by ID.
    @Query("SELECT j.id FROM SyncJob j WHERE j.status IN ('QUEUED','RUNNING','CANCELLING') "
            + "AND COALESCE(j.heartbeatAt, j.startedAt, j.createdAt) < :cutoff ORDER BY j.id")
    List<Long> findStaleJobIds(@Param("cutoff") java.time.LocalDateTime cutoff);

    default int markStaleRunningAsInterrupted(java.time.LocalDateTime cutoff) {
        int interrupted = 0;
        for (Long id : findStaleJobIds(cutoff)) interrupted += markStaleJobAsInterrupted(id, cutoff);
        return interrupted;
    }

    @Modifying
    @Transactional
    @Query("UPDATE SyncJob j SET j.status = 'INTERRUPTED', j.finishedAt = CURRENT_TIMESTAMP, "
           + "j.errorCode = 'INTERRUPTED', j.errorMessage = 'Task heartbeat expired', j.version = j.version + 1 "
           + "WHERE j.id = :id AND j.status IN ('QUEUED','RUNNING','CANCELLING') "
           + "AND COALESCE(j.heartbeatAt, j.startedAt, j.createdAt) < :cutoff")
    int markStaleJobAsInterrupted(@Param("id") Long id, @Param("cutoff") java.time.LocalDateTime cutoff);

    @Query("SELECT j FROM SyncJob j WHERE j.module = :module AND j.status IN ('QUEUED','RUNNING','CANCELLING') ORDER BY j.createdAt DESC")
    Optional<SyncJob> findActiveByModule(@Param("module") String module);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT j FROM SyncJob j WHERE j.id = :id")
    Optional<SyncJob> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT j FROM SyncJob j WHERE "
            + "(:module IS NULL OR j.module = :module) AND "
            + "(:triggerType IS NULL OR j.triggerType = :triggerType) AND "
            + "(:status IS NULL OR j.status = :status)")
    Page<SyncJob> search(
            @Param("module") String module,
            @Param("triggerType") String triggerType,
            @Param("status") String status,
            Pageable pageable);
}
