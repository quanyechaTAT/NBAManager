package com.nbamanager.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "player_season_stats", indexes = {
        @Index(name = "idx_pss_season", columnList = "season"),
        @Index(name = "idx_pss_team_season", columnList = "team_name,season"),
        @Index(name = "idx_pss_nba_player", columnList = "nba_player_id"),
        @Index(name = "idx_pss_season_points", columnList = "season,points_per_game DESC"),
        @Index(name = "idx_pss_season_rebounds", columnList = "season,rebounds_per_game DESC"),
        @Index(name = "idx_pss_season_assists", columnList = "season,assists_per_game DESC")
})
@Getter
@Setter
@NoArgsConstructor
public class PlayerSeasonStats extends DataTracked {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long nbaPlayerId;

    @Column(nullable = false, length = 16)
    private String season;

    @Column(length = 80)
    private String playerName;

    @Column(length = 80)
    private String playerNameEn;

    @Column(length = 80)
    private String teamName;

    @Column(length = 80)
    private String teamNameEn;

    @Column(length = 20)
    private String position;

    @Column(length = 10)
    private String jerseyNumber;

    /** 出场次数 */
    @Column(nullable = true)
    private Integer gamesPlayed;

    /** 首发次数 */
    @Column(nullable = true)
    private Integer gamesStarted;

    /** 场均上场时间 */
    @Column(precision = 4, scale = 1)
    private BigDecimal minutesPerGame;

    /** 场均得分 */
    @Column(precision = 4, scale = 1)
    private BigDecimal pointsPerGame;

    /** 投篮命中率 */
    @Column(precision = 4, scale = 3)
    private BigDecimal fieldGoalPct;

    /** 三分命中率 */
    @Column(precision = 4, scale = 3)
    private BigDecimal threePointPct;

    /** 罚球命中率 */
    @Column(precision = 4, scale = 3)
    private BigDecimal freeThrowPct;

    /** 场均篮板 */
    @Column(precision = 4, scale = 1)
    private BigDecimal reboundsPerGame;

    /** 场均进攻篮板 */
    @Column(precision = 4, scale = 1)
    private BigDecimal offensiveRebounds;

    /** 场均防守篮板 */
    @Column(precision = 4, scale = 1)
    private BigDecimal defensiveRebounds;

    /** 场均助攻 */
    @Column(precision = 4, scale = 1)
    private BigDecimal assistsPerGame;

    /** 场均抢断 */
    @Column(precision = 4, scale = 1)
    private BigDecimal stealsPerGame;

    /** 场均盖帽 */
    @Column(precision = 4, scale = 1)
    private BigDecimal blocksPerGame;

    /** 场均失误 */
    @Column(precision = 4, scale = 1)
    private BigDecimal turnoversPerGame;

    /** 翻译状态 */
    @Column(length = 20)
    private String translationStatus = "UNTRANSLATED";

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }


    public Integer getGamesPlayed() { return publishedValue("gamesPlayed", gamesPlayed); }
    public Integer getGamesStarted() { return publishedValue("gamesStarted", gamesStarted); }
    public BigDecimal getMinutesPerGame() { return publishedValue("minutesPerGame", minutesPerGame); }
    public BigDecimal getPointsPerGame() { return publishedValue("pointsPerGame", pointsPerGame); }
    public BigDecimal getFieldGoalPct() { return publishedValue("fieldGoalPct", fieldGoalPct); }
    public BigDecimal getThreePointPct() { return publishedValue("threePointPct", threePointPct); }
    public BigDecimal getFreeThrowPct() { return publishedValue("freeThrowPct", freeThrowPct); }
    public BigDecimal getReboundsPerGame() { return publishedValue("reboundsPerGame", reboundsPerGame); }
    public BigDecimal getOffensiveRebounds() { return publishedValue("offensiveRebounds", offensiveRebounds); }
    public BigDecimal getDefensiveRebounds() { return publishedValue("defensiveRebounds", defensiveRebounds); }
    public BigDecimal getAssistsPerGame() { return publishedValue("assistsPerGame", assistsPerGame); }
    public BigDecimal getStealsPerGame() { return publishedValue("stealsPerGame", stealsPerGame); }
    public BigDecimal getBlocksPerGame() { return publishedValue("blocksPerGame", blocksPerGame); }
    public BigDecimal getTurnoversPerGame() { return publishedValue("turnoversPerGame", turnoversPerGame); }
}
