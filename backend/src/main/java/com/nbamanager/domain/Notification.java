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
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notification_user_read", columnList = "userId, isRead")
})
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(length = 200)
    private String title;

    @Column(length = 2000)
    private String content;

    private Long relatedId;

    /**
     * 跳转用帖子定位（第 5 轮 P2，修复 COMMENT_LIKE relatedId 语义变更的遗留）。
     *
     * 第 4 轮把 COMMENT_LIKE 的 relatedId 从 postId 改为 commentId（去重键需要），
     * 前端按 relatedId 跳转帖子随之失效。本列与 relatedId 并存：
     * COMMENT_LIKE 存 commentId（去重键）+ postId（跳转用）；其余帖类通知
     * relatedId 本就是 postId，post_id 存同值（冗余但语义统一：
     * 前端规则="有 postId 用 postId，否则回退 relatedId"）。
     * 存量行 post_id 为 NULL，回退 relatedId 后可跳转（存量 COMMENT_LIKE 的
     * relatedId 恰好还是 postId，天然兼容）。
     */
    @Column(name = "post_id")
    private Long postId;

    @Column(nullable = false)
    private Boolean isRead = false;

    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }


}
