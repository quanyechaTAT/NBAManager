package com.nbamanager.repository;

import com.nbamanager.domain.Player;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    Player findByNbaPlayerId(Long nbaPlayerId);

    Page<Player> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Player> findByTeamId(Long teamId, Pageable pageable);

    Page<Player> findByPositionIgnoreCase(String position, Pageable pageable);

    Page<Player> findByTeamIdAndPositionIgnoreCase(Long teamId, String position, Pageable pageable);

    Page<Player> findByNameContainingIgnoreCaseAndTeamId(String name, Long teamId, Pageable pageable);

    Page<Player> findByNameContainingIgnoreCaseAndPositionIgnoreCase(String name, String position, Pageable pageable);

    Page<Player> findByNameContainingIgnoreCaseAndTeamIdAndPositionIgnoreCase(String name, Long teamId, String position, Pageable pageable);
}
