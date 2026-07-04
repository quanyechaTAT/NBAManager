package com.nbamanager.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ai_game_summary")
@Getter
@Setter
@NoArgsConstructor
public class AiGameSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String gameId;

    @Column(nullable = false, length = 40)
    private String summaryType;

    @Column(length = 40)
    private String modelProvider;

    @Column(length = 60)
    private String modelName;

    @Column(length = 20)
    private String promptVersion;

    @Column(length = 64)
    private String factsHash;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(length = 500)
    private String citedEventIdsJson;

    @Column(length = 500)
    private String citedStatKeysJson;

    private LocalDateTime createdAt;
}
