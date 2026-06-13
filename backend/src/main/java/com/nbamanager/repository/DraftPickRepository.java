package com.nbamanager.repository;

import com.nbamanager.domain.DraftPick;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DraftPickRepository extends JpaRepository<DraftPick, Long> {

    List<DraftPick> findByYear(Integer year);

    List<DraftPick> findByYearAndRound(Integer year, Integer round);

    List<DraftPick> findByTeamName(String teamName);

    List<DraftPick> findByPlayerName(String playerName);

    /** 按年份+轮次+顺位判断是否已存在 */
    boolean existsByYearAndRoundAndPickNumber(Integer year, Integer round, Integer pickNumber);
}
