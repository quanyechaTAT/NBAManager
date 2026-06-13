package com.nbamanager.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "playoff_matchups", indexes = {
        @Index(name = "idx_playoff_season", columnList = "season"),
        @Index(name = "idx_playoff_conference", columnList = "conference"),
        @Index(name = "idx_playoff_round", columnList = "round")
})
@Getter
@Setter
@NoArgsConstructor
public class PlayoffMatchup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 赛季，如 "2024-25" */
    @Column(nullable = false, length = 16)
    private String season;

    /** 联盟：East / West / Finals */
    @Column(nullable = false, length = 16)
    private String conference;

    /** 轮次：1=首轮, 2=半决赛, 3=分区决赛, 4=总决赛 */
    @Column(nullable = false)
    private Integer round;

    /** 系列赛ID */
    @Column(length = 20)
    private String seriesId;

    /** 球队1中文名 */
    @Column(length = 80)
    private String team1Name;

    /** 球队1英文名 */
    @Column(name = "team1_name_en", length = 80)
    private String team1NameEn;

    /** 球队1种子排名 */
    private Integer team1Seed;

    /** 球队1胜场 */
    private Integer team1Wins = 0;

    /** 球队2中文名 */
    @Column(length = 80)
    private String team2Name;

    /** 球队2英文名 */
    @Column(name = "team2_name_en", length = 80)
    private String team2NameEn;

    /** 球队2种子排名 */
    private Integer team2Seed;

    /** 球队2胜场 */
    private Integer team2Wins = 0;

    /** 状态：SCHEDULED / IN_PROGRESS / COMPLETED */
    @Column(length = 20)
    private String status = "SCHEDULED";

    /** 晋级球队 */
    @Column(length = 80)
    private String winnerName;

    @Column(nullable = false)
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }

}
