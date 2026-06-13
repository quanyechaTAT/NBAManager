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
public class PlayerSeasonStats {

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
    @Column(nullable = false)
    private Integer gamesPlayed = 0;

    /** 首发次数 */
    @Column(nullable = false)
    private Integer gamesStarted = 0;

    /** 场均上场时间 */
    @Column(precision = 4, scale = 1)
    private BigDecimal minutesPerGame = BigDecimal.ZERO;

    /** 场均得分 */
    @Column(precision = 4, scale = 1)
    private BigDecimal pointsPerGame = BigDecimal.ZERO;

    /** 投篮命中率 */
    @Column(precision = 4, scale = 3)
    private BigDecimal fieldGoalPct = BigDecimal.ZERO;

    /** 三分命中率 */
    @Column(precision = 4, scale = 3)
    private BigDecimal threePointPct = BigDecimal.ZERO;

    /** 罚球命中率 */
    @Column(precision = 4, scale = 3)
    private BigDecimal freeThrowPct = BigDecimal.ZERO;

    /** 场均篮板 */
    @Column(precision = 4, scale = 1)
    private BigDecimal reboundsPerGame = BigDecimal.ZERO;

    /** 场均进攻篮板 */
    @Column(precision = 4, scale = 1)
    private BigDecimal offensiveRebounds = BigDecimal.ZERO;

    /** 场均防守篮板 */
    @Column(precision = 4, scale = 1)
    private BigDecimal defensiveRebounds = BigDecimal.ZERO;

    /** 场均助攻 */
    @Column(precision = 4, scale = 1)
    private BigDecimal assistsPerGame = BigDecimal.ZERO;

    /** 场均抢断 */
    @Column(precision = 4, scale = 1)
    private BigDecimal stealsPerGame = BigDecimal.ZERO;

    /** 场均盖帽 */
    @Column(precision = 4, scale = 1)
    private BigDecimal blocksPerGame = BigDecimal.ZERO;

    /** 场均失误 */
    @Column(precision = 4, scale = 1)
    private BigDecimal turnoversPerGame = BigDecimal.ZERO;

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


}
