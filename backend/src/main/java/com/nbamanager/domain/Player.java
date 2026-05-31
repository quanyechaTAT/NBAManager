package com.nbamanager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "players", indexes = {
        @Index(name = "idx_player_name", columnList = "name"),
        @Index(name = "idx_player_team", columnList = "team_id"),
        @Index(name = "idx_player_position", columnList = "position"),
        @Index(name = "idx_player_team_position", columnList = "team_id, position")
})
@Getter
@Setter
@NoArgsConstructor
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false, length = 16)
    private String position;

    @Column(nullable = false)
    private Double pointsPerGame;

    @Column(nullable = false)
    private Double reboundsPerGame;

    @Column(nullable = false)
    private Double assistsPerGame;

    @Column(nullable = false)
    private Double stealsPerGame;

    @Column(nullable = false)
    private Integer gamesPlayed;

    @Column(nullable = false)
    private Double minutesPerGame;

    @Column(nullable = false)
    private Double fieldGoalPct;

    @Column(nullable = false)
    private Double threePointPct;

    @Column(nullable = false)
    private Double freeThrowPct;

    @Column(nullable = false)
    private Double blocksPerGame;

    @Column(nullable = false)
    private Double turnoversPerGame;

    @Column(nullable = false)
    private Double efficiency;

    @Column(nullable = false)
    private Double trueShootingPct;

    @Column(nullable = false)
    private Double usagePct;

    @Column(nullable = false)
    private Integer jerseyNumber;

    @Column(nullable = false, length = 32)
    private String height;

    @Column(nullable = false)
    private Integer weight;

    @Column(nullable = false, length = 64)
    private String country;
}
