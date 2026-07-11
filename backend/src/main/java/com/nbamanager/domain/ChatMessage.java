package com.nbamanager.domain;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_game_time", columnList = "gameId,createdAt"),
        @Index(name = "idx_chat_game_id", columnList = "gameId"),
        @Index(name = "idx_chat_created_id", columnList = "createdAt,id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_chat_user_client_msg", columnNames = {"userId", "clientMessageId"})
})
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String gameId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(length = 512)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ChatMessageType messageType;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(length = 512)
    private String imageUrl;

    @Column(length = 64)
    private String clientMessageId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean deleted = false;

    private Instant deletedAt;

    private Long deletedBy;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
