package com.nbamanager.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "player_shot_chart", indexes = {
        @Index(name = "idx_shot_player_season", columnList = "nba_player_id,season"),
        @Index(name = "idx_shot_game", columnList = "game_id")
})
@Getter
@Setter
@NoArgsConstructor
public class PlayerShotChart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long playerId;

    @Column(nullable = false)
    private Long nbaPlayerId;

    @Column(nullable = false, length = 16)
    private String season;

    @Column(length = 20)
    private String gameId;

    @Column(nullable = false, precision = 5, scale = 2)
    private java.math.BigDecimal x;

    @Column(nullable = false, precision = 5, scale = 2)
    private java.math.BigDecimal y;

    @Column(nullable = false)
    private Boolean made = false;

    @Column(length = 30)
    private String zone;

    @Column(length = 30)
    private String shotType;

    private LocalDate gameDate;

    private Integer period;

    @Column(nullable = false)
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }
}
