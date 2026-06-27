package com.nbamanager.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "rag_conversations", indexes = {
        @Index(name = "idx_rag_conv_session", columnList = "sessionId"),
        @Index(name = "idx_rag_conv_share", columnList = "shareId"),
        @Index(name = "idx_rag_conv_user", columnList = "userId")
})
@Getter
@Setter
@NoArgsConstructor
public class RAGConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, length = 64)
    private String sessionId;

    @Column(nullable = false, length = 100)
    private String title;

    /** JSON 格式的消息数组 */
    @Column(columnDefinition = "TEXT")
    private String messagesJson;

    /** 分享用唯一ID */
    @Column(unique = true, length = 32)
    private String shareId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
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
