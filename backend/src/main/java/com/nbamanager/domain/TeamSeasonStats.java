package com.nbamanager.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "team_season_stats", indexes = {
        @Index(name = "idx_tss_season", columnList = "season"),
        @Index(name = "idx_tss_conference_season", columnList = "conference,season,wins DESC")
})
@Getter
@Setter
@NoArgsConstructor
public class TeamSeasonStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String season;

    private Long nbaTeamId;

    @Column(nullable = false, length = 80)
    private String teamName;

    @Column(length = 80)
    private String teamNameEn;

    @Column(length = 20)
    private String conference;

    @Column(length = 20)
    private String division;

    /** 胜场 */
    @Column(nullable = false)
    private Integer wins = 0;

    /** 负场 */
    @Column(nullable = false)
    private Integer losses = 0;

    /** 胜率 */
    @Column(precision = 4, scale = 3)
    private BigDecimal winPct = BigDecimal.ZERO;

    /** 联盟排名 */
    private Integer conferenceRank;

    /** 分区排名 */
    private Integer divisionRank;

    /** 联盟排名 */
    private Integer leagueRank;

    /** 场均得分 */
    @Column(precision = 4, scale = 1)
    private BigDecimal pointsPerGame = BigDecimal.ZERO;

    /** 场均失分 */
    @Column(precision = 4, scale = 1)
    private BigDecimal opponentsPointsPerGame = BigDecimal.ZERO;

    /** 场均篮板 */
    @Column(precision = 4, scale = 1)
    private BigDecimal reboundsPerGame = BigDecimal.ZERO;

    /** 场均助攻 */
    @Column(precision = 4, scale = 1)
    private BigDecimal assistsPerGame = BigDecimal.ZERO;

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
