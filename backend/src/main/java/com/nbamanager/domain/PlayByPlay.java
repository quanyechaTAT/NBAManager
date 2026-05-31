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
@Table(name = "play_by_play", indexes = {
        @Index(name = "idx_pbp_game", columnList = "gameId"),
        @Index(name = "idx_pbp_game_period", columnList = "gameId,period")
})
@Getter
@Setter
@NoArgsConstructor
public class PlayByPlay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String gameId;

    @Column(nullable = false)
    private Integer period;

    @Column(nullable = false, length = 10)
    private String gameClock;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Integer homeScore;

    @Column(nullable = false)
    private Integer awayScore;

    @Column(length = 20)
    private String eventType;

    private Long playerId;

    @Column(length = 80)
    private String playerName;

    private Long teamId;
}
