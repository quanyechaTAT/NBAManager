package com.nbamanager.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "game_highlight")
@Getter
@Setter
@NoArgsConstructor
public class GameHighlight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String gameId;

    private Long eventId;

    @Column(nullable = false)
    private int period;

    @Column(nullable = false, length = 10)
    private String gameClock;

    @Column(nullable = false, length = 40)
    private String highlightType;

    @Column(length = 200)
    private String title;

    @Column(length = 500)
    private String description;

    private Long playerId;
    private Long teamId;

    @Column(length = 20)
    private String scoreAfter;

    private LocalDateTime createdAt;
}
