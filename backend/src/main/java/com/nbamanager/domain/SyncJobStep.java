package com.nbamanager.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sync_job_steps", indexes = {
        @Index(name = "idx_sync_step_job", columnList = "job_id,step_order")
})
@Getter
@Setter
@NoArgsConstructor
public class SyncJobStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(nullable = false, length = 32)
    private String module;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(nullable = false, length = 20)
    private String status = "QUEUED";

    private int progress;

    @Column(length = 32)
    private String stage;

    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;

    private int fetchedCount;
    private int insertedCount;
    private int updatedCount;
    private int skippedCount;

    private LocalDateTime dataAsOf;

    @Column(length = 32)
    private String source;

    @Column(length = 32)
    private String errorCode;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Version
    private Long version;
}
