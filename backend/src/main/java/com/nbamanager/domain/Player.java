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
        @Index(name = "idx_player_team_position", columnList = "team_id, position"),
        @Index(name = "idx_player_nba_id", columnList = "nba_player_id"),
        @Index(name = "idx_player_ppg_rank", columnList = "points_per_game DESC, id ASC")
})
@Getter
@Setter
@NoArgsConstructor
public class Player extends DataTracked {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nba_player_id")
    private Long nbaPlayerId;

    @Column(nullable = false, length = 80)
    private String name;

    /** 英文原名 */
    @Column(name = "name_en", length = 80)
    private String nameEn;

    /** 翻译状态：MAPPED/API_TRANSLATED/UNTRANSLATED */
    @Column(name = "translation_status", length = 20)
    private String translationStatus = "UNTRANSLATED";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(length = 16)
    private String position;

    @Column(nullable = true)
    private Double pointsPerGame;

    @Column(nullable = true)
    private Double reboundsPerGame;

    @Column(nullable = true)
    private Double assistsPerGame;

    @Column(nullable = true)
    private Double stealsPerGame;

    @Column(nullable = true)
    private Integer gamesPlayed;

    @Column(nullable = true)
    private Double minutesPerGame;

    @Column(nullable = true)
    private Double fieldGoalPct;

    @Column(nullable = true)
    private Double threePointPct;

    @Column(nullable = true)
    private Double freeThrowPct;

    @Column(nullable = true)
    private Double blocksPerGame;

    @Column(nullable = true)
    private Double turnoversPerGame;

    @Column(nullable = true)
    private Double efficiency;

    @Column(nullable = true)
    private Double trueShootingPct;

    @Column(nullable = true)
    private Double usagePct;

    @Column(length = 8)
    private String jerseyNumber;

    @Column(length = 32)
    private String height;

    @Column(nullable = true)
    private Integer weight;

    @Column(length = 64)
    private String country;

    @Column(nullable = true)
    private Double per;

    @Column(nullable = true)
    private Double winShares;

    @Column(nullable = true)
    private Double vorp;

    @Column(nullable = true)
    private Double bpm;

    @Column(nullable = true)
    private Double offensiveRating;

    @Column(nullable = true)
    private Double defensiveRating;


    public String getPosition() { return publishedValue("position", position); }
    public Double getPointsPerGame() { return publishedValue("pointsPerGame", pointsPerGame); }
    public Double getReboundsPerGame() { return publishedValue("reboundsPerGame", reboundsPerGame); }
    public Double getAssistsPerGame() { return publishedValue("assistsPerGame", assistsPerGame); }
    public Double getStealsPerGame() { return publishedValue("stealsPerGame", stealsPerGame); }
    public Integer getGamesPlayed() { return publishedValue("gamesPlayed", gamesPlayed); }
    public Double getMinutesPerGame() { return publishedValue("minutesPerGame", minutesPerGame); }
    public Double getFieldGoalPct() { return publishedValue("fieldGoalPct", fieldGoalPct); }
    public Double getThreePointPct() { return publishedValue("threePointPct", threePointPct); }
    public Double getFreeThrowPct() { return publishedValue("freeThrowPct", freeThrowPct); }
    public Double getBlocksPerGame() { return publishedValue("blocksPerGame", blocksPerGame); }
    public Double getTurnoversPerGame() { return publishedValue("turnoversPerGame", turnoversPerGame); }
    public Double getEfficiency() { return publishedValue("efficiency", efficiency); }
    public Double getTrueShootingPct() { return publishedValue("trueShootingPct", trueShootingPct); }
    public Double getUsagePct() { return publishedValue("usagePct", usagePct); }
    public String getJerseyNumber() { return publishedValue("jerseyNumber", jerseyNumber); }
    public String getHeight() { return publishedValue("height", height); }
    public Integer getWeight() { return publishedValue("weight", weight); }
    public String getCountry() { return publishedValue("country", country); }
    public Double getPer() { return publishedValue("per", per); }
    public Double getWinShares() { return publishedValue("winShares", winShares); }
    public Double getVorp() { return publishedValue("vorp", vorp); }
    public Double getBpm() { return publishedValue("bpm", bpm); }
    public Double getOffensiveRating() { return publishedValue("offensiveRating", offensiveRating); }
    public Double getDefensiveRating() { return publishedValue("defensiveRating", defensiveRating); }
}
