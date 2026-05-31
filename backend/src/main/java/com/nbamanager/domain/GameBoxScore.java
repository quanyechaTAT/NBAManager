package com.nbamanager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "game_box_score", indexes = {
        @Index(name = "idx_boxscore_game", columnList = "gameId"),
        @Index(name = "idx_boxscore_player", columnList = "playerId")
})
@Getter
@Setter
@NoArgsConstructor
public class GameBoxScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String gameId;

    @Column(nullable = false)
    private Long playerId;

    @Column(nullable = false, length = 80)
    private String playerName;

    @Column(nullable = false)
    private Long teamId;

    @Column(nullable = false, length = 80)
    private String teamName;

    @Column(length = 10)
    private String minutes;

    @Column(nullable = false)
    private Integer points;

    @Column(nullable = false)
    private Integer rebounds;

    @Column(nullable = false)
    private Integer assists;

    @Column(nullable = false)
    private Integer steals;

    @Column(nullable = false)
    private Integer blocks;

    @Column(nullable = false)
    private Integer turnovers;

    @Column(nullable = false)
    private Integer fgMade;

    @Column(nullable = false)
    private Integer fgAttempted;

    @Column(nullable = false)
    private Double fgPct;

    @Column(nullable = false)
    private Integer threeMade;

    @Column(nullable = false)
    private Integer threeAttempted;

    @Column(nullable = false)
    private Double threePct;

    @Column(nullable = false)
    private Integer ftMade;

    @Column(nullable = false)
    private Integer ftAttempted;

    @Column(nullable = false)
    private Double ftPct;

    @Column(nullable = false)
    private Integer plusMinus;

    @Column(nullable = false)
    private Boolean starter;
}
