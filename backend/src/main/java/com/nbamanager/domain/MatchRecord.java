package com.nbamanager.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "match_records")
@Getter
@Setter
@NoArgsConstructor
public class MatchRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String homeTeam;

    @Column(nullable = false, length = 80)
    private String awayTeam;

    @Column(nullable = false)
    private Integer homeScore;

    @Column(nullable = false)
    private Integer awayScore;

    @Column(nullable = false)
    private LocalDate matchDate;

    /** 赛季，如 "2025-26" */
    @Column(nullable = false, length = 16)
    private String season;

    /** SCHEDULED / LIVE / FINISHED */
    @Column(nullable = false, length = 16)
    private String status;
}
