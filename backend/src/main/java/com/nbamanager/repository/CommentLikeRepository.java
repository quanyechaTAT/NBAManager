package com.nbamanager.repository;

import com.nbamanager.domain.CommentLike;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {

    boolean existsByCommentIdAndUserId(Long commentId, Long userId);

    /** 第 8 轮 P1-1：列表路径批量查询当前用户对一批评论的点赞（替代每行一次 exists 的 N+1）。 */
    @org.springframework.data.jpa.repository.Query(
            "SELECT cl.commentId FROM CommentLike cl WHERE cl.userId = :userId AND cl.commentId IN :commentIds")
    java.util.List<Long> findLikedCommentIds(@org.springframework.data.repository.query.Param("userId") Long userId,
                                             @org.springframework.data.repository.query.Param("commentIds") java.util.Collection<Long> commentIds);

    /**
     * 显式批量 DELETE：与派生的 deleteBy 不同，它不会把实体载入持久化上下文，
     * 因此并发下不会出现"提交时发现行已被其他事务删掉"的
     * StaleObjectStateException（压测 R4 点赞风暴 500 的根因）。
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM CommentLike cl WHERE cl.commentId = :commentId AND cl.userId = :userId")
    int deleteByCommentIdAndUserId(@Param("commentId") Long commentId, @Param("userId") Long userId);

    /** INSERT IGNORE：并发重复点赞返回 0，而不是抛唯一键冲突异常。 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT IGNORE INTO comment_likes (comment_id, user_id, create_time)
            VALUES (:commentId, :userId, CURRENT_TIMESTAMP)
            """, nativeQuery = true)
    int insertIgnore(@Param("commentId") Long commentId, @Param("userId") Long userId);

    List<CommentLike> findByUserId(Long userId);

    /** 删除评论时清理其点赞。 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM CommentLike cl WHERE cl.commentId IN :commentIds")
    int deleteByCommentIdIn(@Param("commentIds") java.util.Collection<Long> commentIds);
}
