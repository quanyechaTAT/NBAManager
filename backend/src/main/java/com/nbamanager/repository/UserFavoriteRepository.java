package com.nbamanager.repository;

import com.nbamanager.domain.UserFavorite;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserFavoriteRepository extends JpaRepository<UserFavorite, Long> {

    List<UserFavorite> findByUserId(Long userId);

    List<UserFavorite> findByUserIdAndTargetType(Long userId, String targetType);

    List<UserFavorite> findByTargetTypeAndTargetId(String targetType, Long targetId);

    boolean existsByUserIdAndTargetTypeAndTargetId(Long userId, String targetType, Long targetId);

    /** 显式批量 DELETE：不加载实体，避免并发下的 StaleObjectStateException。 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            DELETE FROM UserFavorite f
            WHERE f.userId = :userId AND f.targetType = :targetType AND f.targetId = :targetId
            """)
    int deleteByUserIdAndTargetTypeAndTargetId(
            @Param("userId") Long userId,
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId);

    /** INSERT IGNORE：唯一键冲突时返回 0，由调用方判定并发，而不是抛 500。 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT IGNORE INTO user_favorites (user_id, target_type, target_id, create_time)
            VALUES (:userId, :targetType, :targetId, CURRENT_TIMESTAMP)
            """, nativeQuery = true)
    int insertIgnore(
            @Param("userId") Long userId,
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId);

    @Modifying
    @Query("DELETE FROM UserFavorite f WHERE f.targetType = :targetType AND f.targetId = :targetId")
    int deleteByTargetTypeAndTargetId(
            @Param("targetType") String targetType, @Param("targetId") Long targetId);

    @Modifying
    @Query("DELETE FROM UserFavorite f WHERE f.targetType = :targetType AND f.targetId IN :targetIds")
    int deleteByTargetTypeAndTargetIdIn(
            @Param("targetType") String targetType,
            @Param("targetIds") Collection<Long> targetIds);

    long countByTargetTypeAndTargetId(String targetType, Long targetId);

    /**
     * 列表场景的收藏计数：一条 GROUP BY 取代"每行一次 COUNT"（N+1）。
     *
     * 帖子/资讯列表每页 10 行，原先就要发 10 条 COUNT；缓存未命中的冷启动尤其明显。
     */
    @Query("""
            SELECT f.targetId AS targetId, COUNT(f) AS cnt FROM UserFavorite f
            WHERE f.targetType = :targetType AND f.targetId IN :targetIds
            GROUP BY f.targetId
            """)
    List<com.nbamanager.repository.projection.TargetCount> countGroupedByTargetIds(
            @Param("targetType") String targetType,
            @Param("targetIds") Collection<Long> targetIds);

    /** 列表装饰用：一次取出某用户在这批目标上的收藏（替代逐行 exists 查询）。 */
    @Query("""
            SELECT f.targetId FROM UserFavorite f
            WHERE f.userId = :userId AND f.targetType = :targetType AND f.targetId IN :targetIds
            """)
    List<Long> findTargetIdsByUserIdAndTargetTypeAndTargetIdIn(
            @Param("userId") Long userId,
            @Param("targetType") String targetType,
            @Param("targetIds") Collection<Long> targetIds);

    @Modifying
    @Query("DELETE FROM UserFavorite f WHERE f.targetType = :targetType AND f.targetId IN :targetIds")
    int deleteNewsTargetsByIds(
            @Param("targetType") String targetType,
            @Param("targetIds") Collection<Long> targetIds);
}
