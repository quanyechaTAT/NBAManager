package com.nbamanager.domain.replay;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "replay_sync_state",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_replay_sync_state_game_id",
                columnNames = "game_id"))
@Getter
@Setter
@NoArgsConstructor
public class ReplaySyncState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false, length = 16)
    private String gameId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReplayPageState status = ReplayPageState.UNAVAILABLE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReplayQualityLevel quality = ReplayQualityLevel.UNAVAILABLE;

    @Column(name = "operation_id", length = 64)
    private String operationId;

    @Column(name = "owner_instance_id", length = 128)
    private String ownerInstanceId;

    @Column(name = "facts_hash", length = 64)
    private String factsHash;

    @Column(length = 40)
    private String source;

    @Column(name = "event_count", nullable = false)
    private int eventCount;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    @Column(name = "last_error_code", length = 64)
    private String lastErrorCode;

    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    @Column(name = "last_error_at")
    private LocalDateTime lastErrorAt;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
