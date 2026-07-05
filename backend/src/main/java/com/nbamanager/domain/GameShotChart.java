package com.nbamanager.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "game_shot_chart")
@Getter
@Setter
@NoArgsConstructor
public class GameShotChart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String gameId;

    private Long teamId;

    @Column(length = 80)
    private String teamName;

    private Long playerId;

    @Column(length = 80)
    private String playerName;

    private Integer period;

    @Column(length = 10)
    private String gameClock;

    private Integer locX;
    private Integer locY;
    private Double chartX;
    private Double chartY;

    private Boolean made;

    @Column(length = 40)
    private String shotType;

    @Column(length = 40)
    private String actionType;

    private Integer points;
    private Integer distance;

    @Column(length = 40)
    private String source;

    private LocalDateTime createdAt;
}
