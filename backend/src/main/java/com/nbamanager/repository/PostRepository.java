package com.nbamanager.repository;

import com.nbamanager.domain.Post;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {

    Page<Post> findByCategory(String category, Pageable pageable);

    Page<Post> findByCategoryAndTitleContainingIgnoreCase(String category, String title, Pageable pageable);

    List<Post> findByIsTopTrueOrderByCreateTimeDesc();

    Page<Post> findByOrderByLikeCountDesc(Pageable pageable);

    Page<Post> findByOrderByViewCountDesc(Pageable pageable);

    Page<Post> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    long countByUserId(Long userId);
}
