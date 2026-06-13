package com.nbamanager.repository;

import com.nbamanager.domain.PlayoffGame;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayoffGameRepository extends JpaRepository<PlayoffGame, Long> {

    List<PlayoffGame> findByMatchupIdOrderByGameNumberAsc(Long matchupId);
}
