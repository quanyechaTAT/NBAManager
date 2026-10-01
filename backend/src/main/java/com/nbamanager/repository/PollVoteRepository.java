package com.nbamanager.repository;

import com.nbamanager.domain.PollVote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PollVoteRepository extends JpaRepository<PollVote, Long> {

    boolean existsByPollIdAndUserId(Long pollId, Long userId);

    long countByPollIdAndOptionIndex(Long pollId, Integer optionIndex);

    List<PollVote> findByPollId(Long pollId);

    @Modifying
    @Query("DELETE FROM PollVote v WHERE v.pollId IN :pollIds")
    int deleteByPollIdIn(@Param("pollIds") java.util.Collection<Long> pollIds);
}
