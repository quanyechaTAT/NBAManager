package com.nbamanager.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "game_id_mapping", indexes = {
        @Index(name = "idx_espn_id", columnList = "espn_game_id"),
        @Index(name = "idx_nba_id", columnList = "nba_game_id")
})
@Getter
@Setter
@NoArgsConstructor
public class GameIdMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String espnGameId;

    @Column(nullable = false, length = 20)
    private String nbaGameId;

    @Column(length = 80)
    private String homeTeam;

    @Column(length = 80)
    private String awayTeam;

    private LocalDate matchDate;


}
