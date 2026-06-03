package com.nbamanager.repository;

import com.nbamanager.domain.CommentLike;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {

    boolean existsByCommentIdAndUserId(Long commentId, Long userId);

    void deleteByCommentIdAndUserId(Long commentId, Long userId);

    List<CommentLike> findByUserId(Long userId);
}
