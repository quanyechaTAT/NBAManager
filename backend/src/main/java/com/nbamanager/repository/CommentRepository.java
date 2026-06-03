package com.nbamanager.repository;

import com.nbamanager.domain.Comment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByPostIdOrderByCreateTimeAsc(Long postId);

    List<Comment> findByPostIdAndParentIdIsNullOrderByCreateTimeAsc(Long postId);

    long countByPostId(Long postId);

    long countByUserId(Long userId);

    void deleteByPostId(Long postId);
}
