package com.nbamanager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "game_news", indexes = {
        @Index(name = "idx_news_game_start_time", columnList = "gameStartTime"),
        @Index(name = "idx_news_title", columnList = "title")
})
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

    /** 比赛开始时间 */
    @Column(nullable = false)
    private LocalDateTime gameStartTime;

    /** 比赛结束时间 */
    @Column(nullable = false)
    private LocalDateTime gameEndTime;

    /** 创建时间 */
    @Column(nullable = false)
    private LocalDateTime createTime;

    /** 比赛状态：SCHEDULED / LIVE / FINISHED */
    @Column(nullable = false, length = 16)
    private String status = "SCHEDULED";

    /** NBA官方比赛ID（10位数字，如 0022400001） */
    @Column(length = 16)
    private String nbaGameId;

    /** 新闻分类：game/trade/injury/draft/general */
    @Column(length = 20)
    private String category = "general";

    /** 新闻来源链接 */
    @Column(length = 500)
    private String sourceUrl;

    /** 新闻图片链接 */
    @Column(length = 500)
    private String imageUrl;
}
