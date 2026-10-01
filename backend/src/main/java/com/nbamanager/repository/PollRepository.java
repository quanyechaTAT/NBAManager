package com.nbamanager.repository;

import com.nbamanager.domain.Poll;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PollRepository extends JpaRepository<Poll, Long> {

    Optional<Poll> findByPostId(Long postId);

    List<Poll> findByPostIdIn(java.util.Collection<Long> postIds);

    @Modifying
    @Query("DELETE FROM Poll p WHERE p.postId IN :postIds")
    int deleteByPostIdIn(@Param("postIds") java.util.Collection<Long> postIds);

    @Query("SELECT p.id FROM Poll p WHERE p.postId IN :postIds")
    List<Long> findIdsByPostIdIn(@Param("postIds") java.util.Collection<Long> postIds);
}
