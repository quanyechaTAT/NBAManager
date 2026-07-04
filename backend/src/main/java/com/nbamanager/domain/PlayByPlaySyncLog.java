package com.nbamanager.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "play_by_play_sync_log")
@Getter
@Setter
@NoArgsConstructor
public class PlayByPlaySyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String gameId;

    @Column(nullable = false, length = 40)
    private String source;

    @Column(nullable = false, length = 20)
    private String status;

    private int attemptCount;

    @Column(length = 500)
    private String errorMessage;

    private int eventCount;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime nextRetryAt;
}
