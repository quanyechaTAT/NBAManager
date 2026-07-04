package com.nbamanager.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "game_momentum_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class GameMomentumSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String gameId;

    @Column(nullable = false)
    private int eventOrder;

    @Column(nullable = false)
    private int period;

    @Column(nullable = false, length = 10)
    private String gameClock;

    @Column(nullable = false)
    private int homeScore;

    @Column(nullable = false)
    private int awayScore;

    @Column(nullable = false)
    private int scoreMargin;

    private Long leaderTeamId;

    private LocalDateTime createdAt;
}
