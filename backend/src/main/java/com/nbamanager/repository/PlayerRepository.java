package com.nbamanager.repository;

import com.nbamanager.domain.Player;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerRepository extends JpaRepository<Player, Long> {

    Player findByNbaPlayerId(Long nbaPlayerId);

    Player findByNameAndTeamId(String name, Long teamId);

    Player findByName(String name);

    Page<Player> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Player> findByTeamId(Long teamId, Pageable pageable);

    Page<Player> findByPositionIgnoreCase(String position, Pageable pageable);

    Page<Player> findByTeamIdAndPositionIgnoreCase(Long teamId, String position, Pageable pageable);

    Page<Player> findByNameContainingIgnoreCaseAndTeamId(String name, Long teamId, Pageable pageable);

    Page<Player> findByNameContainingIgnoreCaseAndPositionIgnoreCase(String name, String position, Pageable pageable);

    Page<Player> findByNameContainingIgnoreCaseAndTeamIdAndPositionIgnoreCase(String name, Long teamId, String position, Pageable pageable);

    /** 搜索中文名或英文名 */
    @Query("SELECT p FROM Player p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(p.nameEn) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Player> findByNameOrNameEnContainingIgnoreCase(@Param("q") String q, Pageable pageable);

    @Query("SELECT p FROM Player p WHERE (LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(p.nameEn) LIKE LOWER(CONCAT('%', :q, '%'))) AND p.team.id = :teamId")
    Page<Player> findByNameOrNameEnContainingIgnoreCaseAndTeamId(@Param("q") String q, @Param("teamId") Long teamId, Pageable pageable);

    @Query("SELECT p FROM Player p WHERE (LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(p.nameEn) LIKE LOWER(CONCAT('%', :q, '%'))) AND LOWER(p.position) = LOWER(:position)")
    Page<Player> findByNameOrNameEnContainingIgnoreCaseAndPositionIgnoreCase(@Param("q") String q, @Param("position") String position, Pageable pageable);

    @Query("SELECT p FROM Player p WHERE (LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(p.nameEn) LIKE LOWER(CONCAT('%', :q, '%'))) AND p.team.id = :teamId AND LOWER(p.position) = LOWER(:position)")
    Page<Player> findByNameOrNameEnContainingIgnoreCaseAndTeamIdAndPositionIgnoreCase(@Param("q") String q, @Param("teamId") Long teamId, @Param("position") String position, Pageable pageable);

    @Query("SELECT p FROM Player p JOIN FETCH p.team ORDER BY p.pointsPerGame DESC")
    java.util.List<Player> findAllWithTeamSortedByPpg();
}
