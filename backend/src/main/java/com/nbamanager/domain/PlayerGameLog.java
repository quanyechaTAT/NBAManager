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
@Table(name = "player_game_log", indexes = {
        @Index(name = "idx_gamelog_player", columnList = "playerId"),
        @Index(name = "idx_gamelog_player_season", columnList = "playerId,season")
})
@Getter
@Setter
@NoArgsConstructor
public class PlayerGameLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long playerId;

    @Column(nullable = false, length = 16)
    private String gameId;

    @Column(nullable = false, length = 16)
    private String matchDate;

    @Column(nullable = false, length = 100)
    private String opponent;

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

    private Double fgPct;

    private Double threePct;

    private Double ftPct;

    private Integer plusMinus;

    @Column(length = 4)
    private String result;

    @Column(nullable = false, length = 16)
    private String season;
}
