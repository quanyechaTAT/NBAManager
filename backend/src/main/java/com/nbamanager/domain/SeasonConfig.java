package com.nbamanager.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "season_config", indexes = {
        @Index(name = "idx_is_current", columnList = "is_current"),
        @Index(name = "idx_sync_status", columnList = "sync_status")
})
@Getter
@Setter
@NoArgsConstructor
public class SeasonConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** 赛季，如 "2025-26" */
    @Column(nullable = false, unique = true, length = 16)
    private String season;

    /** 是否为当前赛季 */
    @Column(nullable = false)
    private Boolean isCurrent = false;

    /** 同步状态：PENDING/SYNCING/COMPLETED/FAILED */
    @Column(nullable = false, length = 20)
    private String syncStatus = "PENDING";

    /** 最后同步时间 */
    private LocalDateTime lastSyncTime;

    /** 已同步球员数 */
    @Column(nullable = false)
    private Integer playerCount = 0;

    /** 已同步球队数 */
    @Column(nullable = false)
    private Integer teamCount = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }


}
