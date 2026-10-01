package com.nbamanager.repository;

import com.nbamanager.domain.PlayoffMatchup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayoffMatchupRepository extends JpaRepository<PlayoffMatchup, Long> {

    long countBySeason(String season);

    List<PlayoffMatchup> findBySeasonOrderByRoundAscSeriesIdAscIdAsc(String season);

    List<PlayoffMatchup> findBySeasonAndConferenceOrderByRoundAscSeriesIdAscIdAsc(String season, String conference);

    default List<PlayoffMatchup> findBySeasonOrderByRoundAsc(String season) {
        return findBySeasonOrderByRoundAscSeriesIdAscIdAsc(season);
    }

    default List<PlayoffMatchup> findBySeasonAndConferenceOrderByRoundAsc(String season, String conference) {
        return findBySeasonAndConferenceOrderByRoundAscSeriesIdAscIdAsc(season, conference);
    }

    List<PlayoffMatchup> findBySeasonAndRoundOrderByTeam1SeedAsc(String season, Integer round);
}
