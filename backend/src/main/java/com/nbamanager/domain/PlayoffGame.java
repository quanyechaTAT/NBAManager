package com.nbamanager.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "playoff_games", indexes = {
        @Index(name = "idx_playoff_game_matchup", columnList = "matchup_id"),
        @Index(name = "idx_playoff_game_date", columnList = "game_date")
})
@Getter
@Setter
@NoArgsConstructor
public class PlayoffGame {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的系列赛 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matchup_id")
    private PlayoffMatchup matchup;

    /** 第几场 (1-7) */
    private Integer gameNumber;

    /** 主队名 */
    @Column(length = 80)
    private String homeTeam;

    /** 客队名 */
    @Column(length = 80)
    private String awayTeam;

    /** 主队得分 */
    private Integer homeScore;

    /** 客队得分 */
    private Integer awayScore;

    /** 比赛日期 */
    private LocalDate gameDate;

    /** 状态：SCHEDULED / LIVE / FINISHED */
    @Column(length = 20)
    private String status = "SCHEDULED";

    /** NBA官方比赛ID */
    @Column(length = 20)
    private String nbaGameId;

    @Column(nullable = false)
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }

}
