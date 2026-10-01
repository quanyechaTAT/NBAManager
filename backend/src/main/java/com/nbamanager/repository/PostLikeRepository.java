package com.nbamanager.repository;

import com.nbamanager.domain.PostLike;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

    boolean existsByPostIdAndUserId(Long postId, Long userId);

    Optional<PostLike> findByPostIdAndUserId(Long postId, Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM PostLike pl WHERE pl.postId = :postId AND pl.userId = :userId")
    int deleteByPostIdAndUserId(@Param("postId") Long postId, @Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT IGNORE INTO post_likes (post_id, user_id, create_time)
            VALUES (:postId, :userId, CURRENT_TIMESTAMP)
            """, nativeQuery = true)
    int insertIgnore(@Param("postId") Long postId, @Param("userId") Long userId);

    long countByPostId(Long postId);

    List<PostLike> findByUserId(Long userId);

    /**
     * 列表装饰用：一次取出"当前用户 + 当前这一页帖子"的点赞集合。
     *
     * 原先装饰列表时取的是 {@code findByUserId(userId)}（该用户的<b>全部</b>点赞），
     * 并且每次都把整表捞进内存；这里收窄到本页 id，既少扫行也让缓存命中路径只有一条小查询。
     */
    @Query("SELECT pl.postId FROM PostLike pl WHERE pl.userId = :userId AND pl.postId IN :postIds")
    List<Long> findLikedPostIds(@Param("userId") Long userId,
                                @Param("postIds") java.util.Collection<Long> postIds);

    /** 删除帖子时清理其点赞（不加载实体，避免并发下的陈旧行异常）。 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM PostLike pl WHERE pl.postId = :postId")
    int deleteByPostId(@Param("postId") Long postId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM PostLike pl WHERE pl.postId IN :postIds")
    int deleteByPostIds(@Param("postIds") java.util.Collection<Long> postIds);
}
