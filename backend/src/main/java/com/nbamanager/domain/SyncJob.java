package com.nbamanager.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sync_jobs", indexes = {
        @Index(name = "idx_sync_job_module", columnList = "module"),
        @Index(name = "idx_sync_job_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
public class SyncJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String module;

    @Column(nullable = false, length = 16)
    private String triggerType; // MANUAL, SCHEDULED, STARTUP_CATCHUP

    @Column(nullable = false, length = 16)
    private String status; // QUEUED, RUNNING, SUCCESS, PARTIAL_FAILED, FAILED, CANCELLED, INTERRUPTED

    @Column(nullable = false)
    private int progress = 0;

    @Column(length = 32)
    private String stage;

    @Column(length = 64)
    private String requestedBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime heartbeatAt;

    private LocalDateTime dataAsOf;

    @Column(length = 32)
    private String source;

    private int fetchedCount;
    private int insertedCount;
    private int updatedCount;
    private int skippedCount;

    @Column(length = 32)
    private String errorCode;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private boolean cancelRequested = false;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
