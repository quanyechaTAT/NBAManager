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
public class PlayerCareerStats extends DataTracked {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long playerId;

    @Column(nullable = false, length = 16)
    private String season;

    @Column(nullable = false, length = 80)
    private String teamName;

    @Column(nullable = true)
    private Integer gamesPlayed;

    @Column(nullable = true)
    private Double minutesPerGame;

    @Column(nullable = true)
    private Double pointsPerGame;

    @Column(nullable = true)
    private Double reboundsPerGame;

    @Column(nullable = true)
    private Double assistsPerGame;

    @Column(nullable = true)
    private Double stealsPerGame;

    @Column(nullable = true)
    private Double blocksPerGame;

    private Double fgPct;

    private Double threePct;

    private Double ftPct;

    private Double efficiency;


    public Integer getGamesPlayed() { return publishedValue("gamesPlayed", gamesPlayed); }
    public Double getMinutesPerGame() { return publishedValue("minutesPerGame", minutesPerGame); }
    public Double getPointsPerGame() { return publishedValue("pointsPerGame", pointsPerGame); }
    public Double getReboundsPerGame() { return publishedValue("reboundsPerGame", reboundsPerGame); }
    public Double getAssistsPerGame() { return publishedValue("assistsPerGame", assistsPerGame); }
    public Double getStealsPerGame() { return publishedValue("stealsPerGame", stealsPerGame); }
    public Double getBlocksPerGame() { return publishedValue("blocksPerGame", blocksPerGame); }
    public Double getFgPct() { return publishedValue("fgPct", fgPct); }
    public Double getThreePct() { return publishedValue("threePct", threePct); }
    public Double getFtPct() { return publishedValue("ftPct", ftPct); }
    public Double getEfficiency() { return publishedValue("efficiency", efficiency); }
}
