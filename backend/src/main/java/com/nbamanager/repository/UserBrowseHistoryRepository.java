package com.nbamanager.repository;

import com.nbamanager.domain.UserBrowseHistory;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserBrowseHistoryRepository extends JpaRepository<UserBrowseHistory, Long> {

    List<UserBrowseHistory> findByUserIdAndTargetTypeOrderByBrowseTimeDesc(Long userId, String targetType);

    boolean existsByUserIdAndTargetTypeAndTargetId(Long userId, String targetType, Long targetId);

    void deleteByUserIdAndTargetTypeAndTargetId(Long userId, String targetType, Long targetId);

    void deleteByTargetTypeAndTargetId(String targetType, Long targetId);

    @Modifying
    @Query("DELETE FROM UserBrowseHistory h WHERE h.targetType = :targetType AND h.targetId IN :targetIds")
    int deleteNewsTargetsByIds(
            @Param("targetType") String targetType,
            @Param("targetIds") Collection<Long> targetIds);

    /** 语义化别名：按 targetType + 一批 targetId 删除（帖子删除级联用）。 */
    default int deleteByTargetTypeAndTargetIdIn(String targetType, Collection<Long> targetIds) {
        return deleteNewsTargetsByIds(targetType, targetIds);
    }
}
