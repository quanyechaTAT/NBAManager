package com.nbamanager.repository;

import com.nbamanager.domain.Post;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    // 原子计数更新：并发点赞/评论下读改写会丢失计数（第三轮压测确认的竞态模式）
    @Modifying
    @Query("UPDATE Post p SET p.likeCount = COALESCE(p.likeCount, 0) + :delta WHERE p.id = :id")
    int bumpLikeCount(@Param("id") Long id, @Param("delta") int delta);

    @Modifying
    @Query("UPDATE Post p SET p.commentCount = COALESCE(p.commentCount, 0) + :delta WHERE p.id = :id")
    int bumpCommentCount(@Param("id") Long id, @Param("delta") int delta);

    /**
     * 原子更新"最后回复时间"。
     *
     * 新增评论时不能再 {@code findById} 出来改字段再 {@code save}：
     * 那会把内存里过期的 commentCount 整行 merge 回数据库，
     * 抹掉刚刚由 {@link #bumpCommentCount} 完成的自增（安全报告 §十 10.7：
     * 发 3 条评论后 comment_count 仍为 0，而 last_reply_time 正常更新）。
     */
    @Modifying
    @Query("UPDATE Post p SET p.lastReplyTime = CURRENT_TIMESTAMP WHERE p.id = :id")
    int touchLastReplyTime(@Param("id") Long id);

    /**
     * 原子自增浏览量（安全报告 §十二 V6 / 压测 P3）。
     *
     * 详情页读改写有两个问题：并发丢计数（与点赞/评论同型竞态），
     * 且配合 {@code @CacheEvict(allEntries=true)} 让匿名详情流量清零列表缓存。
     * {@code clearAutomatically=true}：PostService.get 在同一事务里先自增再
     * findById，清掉一级缓存才能让后续读看到自增后的值，而不是被实体缓存
     * 污染的旧值。
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Post p SET p.viewCount = COALESCE(p.viewCount, 0) + 1 WHERE p.id = :id")
    int bumpViewCount(@Param("id") Long id);

    /** 批量增量（第 8 轮 P0-2 写合并）：ViewCountBuffer 定时按窗口聚合落库。 */
    @Modifying
    @Query("UPDATE Post p SET p.viewCount = COALESCE(p.viewCount, 0) + :delta WHERE p.id = :id")
    int bumpViewCountBy(@Param("id") Long id, @Param("delta") long delta);

    Page<Post> findByCategory(String category, Pageable pageable);

    Page<Post> findByCategoryAndTitleContainingIgnoreCase(String category, String title, Pageable pageable);

    List<Post> findByIsTopTrueOrderByCreateTimeDesc();

    Page<Post> findByOrderByLikeCountDesc(Pageable pageable);

    Page<Post> findByOrderByViewCountDesc(Pageable pageable);

    Page<Post> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    long countByUserId(Long userId);

    @Query("SELECT p FROM Post p WHERE p.category = :category AND p.tags IS NOT NULL AND p.tags <> ''")
    List<Post> findByCategoryWithTags(@Param("category") String category);

    @Query("SELECT p FROM Post p WHERE p.tags IS NOT NULL AND p.tags <> ''")
    List<Post> findAllWithTags();

    @Query("SELECT p FROM Post p WHERE p.category = :category")
    List<Post> findByCategoryAll(@Param("category") String category);

    @Query("SELECT p FROM Post p")
    List<Post> findAllPosts();
}
