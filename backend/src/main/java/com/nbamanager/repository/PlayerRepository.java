package com.nbamanager.repository;

import com.nbamanager.domain.Player;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayerRepository extends JpaRepository<Player, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Player> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "team")
    Page<Player> findAll(org.springframework.data.jpa.domain.Specification<Player> specification, Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "team")
    Page<Player> findAll(Pageable pageable);

    long countByTranslationStatus(String translationStatus);

    Player findByNbaPlayerId(Long nbaPlayerId);

    Player findByNameAndTeamId(String name, Long teamId);

    Player findByName(String name);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "team")
    Page<Player> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "team")
    Page<Player> findByTeamId(Long teamId, Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "team")
    Page<Player> findByPositionIgnoreCase(String position, Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "team")
    Page<Player> findByTeamIdAndPositionIgnoreCase(Long teamId, String position, Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "team")
    Page<Player> findByNameContainingIgnoreCaseAndTeamId(String name, Long teamId, Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "team")
    Page<Player> findByNameContainingIgnoreCaseAndPositionIgnoreCase(String name, String position, Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "team")
    Page<Player> findByNameContainingIgnoreCaseAndTeamIdAndPositionIgnoreCase(String name, Long teamId, String position, Pageable pageable);

    /** 搜索中文名或英文名 */
    @Query("SELECT p FROM Player p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(p.nameEn) LIKE LOWER(CONCAT('%', :q, '%'))")
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "team")
    Page<Player> findByNameOrNameEnContainingIgnoreCase(@Param("q") String q, Pageable pageable);

    @Query("SELECT p FROM Player p WHERE (LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(p.nameEn) LIKE LOWER(CONCAT('%', :q, '%'))) AND p.team.id = :teamId")
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "team")
    Page<Player> findByNameOrNameEnContainingIgnoreCaseAndTeamId(@Param("q") String q, @Param("teamId") Long teamId, Pageable pageable);

    @Query("SELECT p FROM Player p WHERE (LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(p.nameEn) LIKE LOWER(CONCAT('%', :q, '%'))) AND LOWER(p.position) = LOWER(:position)")
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "team")
    Page<Player> findByNameOrNameEnContainingIgnoreCaseAndPositionIgnoreCase(@Param("q") String q, @Param("position") String position, Pageable pageable);

    @Query("SELECT p FROM Player p WHERE (LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(p.nameEn) LIKE LOWER(CONCAT('%', :q, '%'))) AND p.team.id = :teamId AND LOWER(p.position) = LOWER(:position)")
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "team")
    Page<Player> findByNameOrNameEnContainingIgnoreCaseAndTeamIdAndPositionIgnoreCase(@Param("q") String q, @Param("teamId") Long teamId, @Param("position") String position, Pageable pageable);

    @Query("SELECT p FROM Player p JOIN FETCH p.team WHERE p.pointsPerGame IS NOT NULL ORDER BY p.pointsPerGame DESC")
    java.util.List<Player> findAllWithTeamSortedByPpg();
}
