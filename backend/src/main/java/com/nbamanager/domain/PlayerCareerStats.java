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
@Table(name = "player_career_stats", indexes = {
        @Index(name = "idx_career_player", columnList = "playerId")
})
@Getter
@Setter
@NoArgsConstructor
public class PlayerCareerStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long playerId;

    @Column(nullable = false, length = 16)
    private String season;

    @Column(nullable = false, length = 80)
    private String teamName;

    @Column(nullable = false)
    private Integer gamesPlayed;

    @Column(nullable = false)
    private Double minutesPerGame;

    @Column(nullable = false)
    private Double pointsPerGame;

    @Column(nullable = false)
    private Double reboundsPerGame;

    @Column(nullable = false)
    private Double assistsPerGame;

    @Column(nullable = false)
    private Double stealsPerGame;

    @Column(nullable = false)
    private Double blocksPerGame;

    private Double fgPct;

    private Double threePct;

    private Double ftPct;

    private Double efficiency;


}
