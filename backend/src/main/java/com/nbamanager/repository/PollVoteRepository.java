package com.nbamanager.repository;

import com.nbamanager.domain.PollVote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PollVoteRepository extends JpaRepository<PollVote, Long> {

    boolean existsByPollIdAndUserId(Long pollId, Long userId);

    long countByPollIdAndOptionIndex(Long pollId, Integer optionIndex);

    List<PollVote> findByPollId(Long pollId);
}
