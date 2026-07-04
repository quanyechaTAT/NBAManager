package com.nbamanager.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "game_turning_point")
@Getter
@Setter
@NoArgsConstructor
public class GameTurningPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String gameId;

    private Long eventId;

    @Column(nullable = false)
    private int period;

    @Column(nullable = false, length = 10)
    private String gameClock;

    @Column(nullable = false, length = 40)
    private String type;

    private double confidence;

    @Column(length = 500)
    private String factualReason;

    @Column(columnDefinition = "TEXT")
    private String aiExplanation;

    @Column(length = 500)
    private String citedEventIdsJson;

    private LocalDateTime createdAt;
}
