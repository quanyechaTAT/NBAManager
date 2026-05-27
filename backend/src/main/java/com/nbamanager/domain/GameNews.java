package com.nbamanager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "game_news")
@Getter
@Setter
@NoArgsConstructor
public class GameNews {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 资讯标题 */
    @Column(nullable = false, length = 120)
    private String title;

    /** 摘要 */
    @Column(nullable = false, length = 300)
    private String summary;

    /** 详细内容 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 主队名称 */
    @Column(nullable = false, length = 80)
    private String homeTeam;

    /** 客队名称 */
    @Column(nullable = false, length = 80)
    private String awayTeam;

    /** 主队得分（未开始则为 null） */
    @Column(nullable = true)
    private Integer homeScore;

    /** 客队得分（未开始则为 null） */
    @Column(nullable = true)
    private Integer awayScore;

    /** 比赛时间 */
    @Column(nullable = false)
    private LocalDateTime gameTime;

    /**
     * 比赛状态：SCHEDULED-未开始, LIVE-进行中, FINISHED-已结束
     */
    @Column(nullable = false, length = 16)
    private String status;

    /** 创建时间 */
    @Column(nullable = false)
    private LocalDateTime createTime;
}
