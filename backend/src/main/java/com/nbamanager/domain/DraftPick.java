package com.nbamanager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "draft_picks", indexes = {
        @Index(name = "idx_draft_year", columnList = "year"),
        @Index(name = "idx_draft_team", columnList = "teamName"),
        @Index(name = "idx_draft_player", columnList = "playerName"),
        @Index(name = "idx_draft_year_round", columnList = "year, round")
})
@Getter
@Setter
@NoArgsConstructor
public class DraftPick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 选秀年份 */
    @Column(nullable = false)
    private Integer year;

    /** 轮次（1 或 2） */
    @Column(nullable = false)
    private Integer round;

    /** 顺位 */
    @Column(nullable = false)
    private Integer pickNumber;

    /** 选中的球队 */
    @Column(nullable = false, length = 80)
    private String teamName;

    /** 球队英文名 */
    @Column(name = "team_name_en", length = 80)
    private String teamNameEn;

    /** 选中的球员名（可能为空如果还没选） */
    @Column(length = 100)
    private String playerName;

    /** 球员英文名 */
    @Column(name = "player_name_en", length = 100)
    private String playerNameEn;

    /** NBA球员ID（用于头像和跳转） */
    @Column(name = "nba_player_id")
    private Long nbaPlayerId;

    /** 备注 */
    @Column(length = 500)
    private String notes;

    /** 创建时间 */
    @Column(nullable = false)
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }


}
