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

    @Column(columnDefinition = "TEXT")
    private String citedEventIdsJson;

    private Integer startEventOrder;
    private Integer endEventOrder;
    private Integer startHomeScore;
    private Integer startAwayScore;
    private Integer endHomeScore;
    private Integer endAwayScore;
    private Integer scoreSwing;
    private Double impactScore;

    @Column(length = 20)
    private String impactLevel; // HIGH / MEDIUM / LOW

    @Column(length = 100)
    private String teamName;

    private LocalDateTime createdAt;
}
