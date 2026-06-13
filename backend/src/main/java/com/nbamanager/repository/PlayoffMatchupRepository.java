package com.nbamanager.repository;

import com.nbamanager.domain.PlayoffMatchup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayoffMatchupRepository extends JpaRepository<PlayoffMatchup, Long> {

    List<PlayoffMatchup> findBySeasonOrderByRoundAsc(String season);

    List<PlayoffMatchup> findBySeasonAndConferenceOrderByRoundAsc(String season, String conference);

    List<PlayoffMatchup> findBySeasonAndRoundOrderByTeam1SeedAsc(String season, Integer round);
}
