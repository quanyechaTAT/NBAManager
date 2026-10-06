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
public class PlayerGameLog extends DataTracked {

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

    @Column(nullable = false)
    private boolean isHome = false;

    @Column(length = 10)
    private String minutes;

    @Column(nullable = true)
    private Integer points;

    @Column(nullable = true)
    private Integer rebounds;

    @Column(nullable = true)
    private Integer assists;

    @Column(nullable = true)
    private Integer steals;

    @Column(nullable = true)
    private Integer blocks;

    @Column(nullable = true)
    private Integer turnovers;

    private Double fgPct;

    private Double threePct;

    private Double ftPct;

    private Integer plusMinus;

    @Column(length = 4)
    private String result;

    @Column(nullable = false, length = 16)
    private String season;


    public Integer getPoints() { return publishedValue("points", points); }
    public Integer getRebounds() { return publishedValue("rebounds", rebounds); }
    public Integer getAssists() { return publishedValue("assists", assists); }
    public Integer getSteals() { return publishedValue("steals", steals); }
    public Integer getBlocks() { return publishedValue("blocks", blocks); }
    public Integer getTurnovers() { return publishedValue("turnovers", turnovers); }
    public Double getFgPct() { return publishedValue("fgPct", fgPct); }
    public Double getThreePct() { return publishedValue("threePct", threePct); }
    public Double getFtPct() { return publishedValue("ftPct", ftPct); }
    public Integer getPlusMinus() { return publishedValue("plusMinus", plusMinus); }
}
