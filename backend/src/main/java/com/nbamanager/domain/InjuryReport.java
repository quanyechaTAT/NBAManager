package com.nbamanager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "injury_reports", indexes = {
        @Index(name = "idx_injury_player", columnList = "playerId"),
        @Index(name = "idx_injury_team", columnList = "teamName"),
        @Index(name = "idx_injury_status", columnList = "status"),
        @Index(name = "idx_injury_player_status", columnList = "playerId, status")
})
@Getter
@Setter
@NoArgsConstructor
public class InjuryReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long playerId;

    @Column(nullable = false, length = 100)
    private String playerName;

    @Column(nullable = false, length = 50)
    private String teamName;

    @Column(nullable = false, length = 100)
    private String injuryType;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 500)
    private String description;

    private LocalDate startDate;

    private LocalDate expectedReturn;

    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
    }


}
