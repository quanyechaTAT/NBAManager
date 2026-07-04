package com.nbamanager.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ai_conversation_memory")
@Getter
@Setter
@NoArgsConstructor
public class AiConversationMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String conversationId;

    @Column(length = 16)
    private String activeGameId;

    private Long activePlayerId;
    private Long activeTeamId;

    @Column(length = 10)
    private String activeSeason;

    @Column(length = 40)
    private String lastIntent;

    @Column(columnDefinition = "TEXT")
    private String memorySummary;

    @Column(columnDefinition = "JSON")
    private String stateJson;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
