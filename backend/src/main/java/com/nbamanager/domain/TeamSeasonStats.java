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
public class TeamSeasonStats extends DataTracked {

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
    @Column(nullable = true)
    private Integer wins ;

    /** 负场 */
    @Column(nullable = true)
    private Integer losses ;

    /** 胜率 */
    @Column(precision = 4, scale = 3)
    private BigDecimal winPct ;

    /** 联盟排名 */
    private Integer conferenceRank;

    /** 分区排名 */
    private Integer divisionRank;

    /** 联盟排名 */
    private Integer leagueRank;

    /** 场均得分 */
    @Column(precision = 4, scale = 1)
    private BigDecimal pointsPerGame ;

    /** 场均失分 */
    @Column(precision = 4, scale = 1)
    private BigDecimal opponentsPointsPerGame ;

    /** 场均篮板 */
    @Column(precision = 4, scale = 1)
    private BigDecimal reboundsPerGame ;

    /** 场均助攻 */
    @Column(precision = 4, scale = 1)
    private BigDecimal assistsPerGame ;

    /** 主场战绩 */
    @Column(name = "home_record", length = 16)
    private String homeRecord;

    /** 客场战绩 */
    @Column(name = "away_record", length = 16)
    private String awayRecord;

    /** 近况连胜/连败，如 "3连胜" */
    @Column(length = 16)
    private String streak;

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


    public Integer getWins() { return wins != null ? wins : publishedValue("wins", wins); }
    public Integer getLosses() { return losses != null ? losses : publishedValue("losses", losses); }
    public BigDecimal getWinPct() { return winPct != null ? winPct : publishedValue("winPct", winPct); }
    public Integer getConferenceRank() { return conferenceRank != null ? conferenceRank : publishedValue("conferenceRank", conferenceRank); }
    public Integer getDivisionRank() { return divisionRank != null ? divisionRank : publishedValue("divisionRank", divisionRank); }
    public Integer getLeagueRank() { return leagueRank != null ? leagueRank : publishedValue("leagueRank", leagueRank); }
    public BigDecimal getPointsPerGame() { return pointsPerGame != null ? pointsPerGame : publishedValue("pointsPerGame", pointsPerGame); }
    public BigDecimal getOpponentsPointsPerGame() { return opponentsPointsPerGame != null ? opponentsPointsPerGame : publishedValue("opponentsPointsPerGame", opponentsPointsPerGame); }
    public BigDecimal getReboundsPerGame() { return reboundsPerGame != null ? reboundsPerGame : publishedValue("reboundsPerGame", reboundsPerGame); }
    public BigDecimal getAssistsPerGame() { return assistsPerGame != null ? assistsPerGame : publishedValue("assistsPerGame", assistsPerGame); }
    public String getHomeRecord() { return (homeRecord != null && !homeRecord.isBlank()) ? homeRecord : publishedValue("homeRecord", homeRecord); }
    public String getAwayRecord() { return (awayRecord != null && !awayRecord.isBlank()) ? awayRecord : publishedValue("awayRecord", awayRecord); }
    public String getStreak() { return (streak != null && !streak.isBlank()) ? streak : publishedValue("streak", streak); }
}
