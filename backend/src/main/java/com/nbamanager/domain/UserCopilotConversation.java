package com.nbamanager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Copilot 会话归属（安全报告 §十二 V1）。
 *
 * python 端（nba_copilot_app）是全局单用户内存存储，无法表达归属；
 * java 层在 {@code POST /api/nba-copilot/chat} 产生/续用会话时登记
 * (user_id, conversation_id) 绑定，conversations 系列端点据此过滤与鉴权，
 * 消除"任何登录用户可读/删任意用户会话"的水平越权。
 * user_id+conversation_id 唯一：并发登记同一归属时靠唯一约束兜底。
 */
@Entity
@Table(name = "user_copilot_conversations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_copilot_conv", columnNames = {"userId", "conversationId"}),
        indexes = {
                @Index(name = "idx_user_copilot_user", columnList = "userId"),
                @Index(name = "idx_user_copilot_conv", columnList = "conversationId")
        })
@Getter
@Setter
@NoArgsConstructor
public class UserCopilotConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String conversationId;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
