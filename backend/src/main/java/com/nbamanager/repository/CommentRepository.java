package com.nbamanager.repository;

import com.nbamanager.domain.Comment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @Modifying
    @Query("UPDATE Comment c SET c.likeCount = COALESCE(c.likeCount, 0) + :delta WHERE c.id = :id")
    int bumpLikeCount(@Param("id") Long id, @Param("delta") int delta);


    List<Comment> findByPostIdOrderByCreateTimeAsc(Long postId);


    List<Comment> findByPostIdAndParentIdIsNullOrderByCreateTimeAsc(Long postId);

    List<Comment> findByGameIdOrderByCreateTimeAsc(String gameId);

    long countByPostId(Long postId);

    long countByGameId(String gameId);

    long countByUserId(Long userId);

    void deleteByPostId(Long postId);

    /** 帖子删除级联：批量清理评论。显式 @Modifying 避免逐行加载实体。 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Comment c WHERE c.postId IN :postIds")
    int deleteByPostIdIn(@Param("postIds") java.util.Collection<Long> postIds);

    /** 删除评论前需要先拿到评论 id 以清理评论点赞。 */
    @Query("SELECT c.id FROM Comment c WHERE c.postId IN :postIds")
    List<Long> findIdsByPostIdIn(@Param("postIds") java.util.Collection<Long> postIds);

    void deleteByGameId(String gameId);

    Optional<Comment> findTopByPostIdOrderByCreateTimeDesc(Long postId);

    /**
     * 列表场景的"最后回复时间"：一条 GROUP BY MAX(createTime) 取代逐行查询。
     *
     * posts.last_reply_time 为空的帖子才需要回退到评论表（老数据没有该字段），
     * 因此这里只对缺失的行做一次批量补齐。
     */
    @Query("""
            SELECT c.postId AS postId, MAX(c.createTime) AS lastReplyTime FROM Comment c
            WHERE c.postId IN :postIds
            GROUP BY c.postId
            """)
    List<com.nbamanager.repository.projection.PostLastReply> findLastReplyTimesByPostIds(
            @Param("postIds") java.util.Collection<Long> postIds);
}
