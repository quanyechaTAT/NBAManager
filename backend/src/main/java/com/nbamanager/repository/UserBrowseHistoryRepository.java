package com.nbamanager.repository;

import com.nbamanager.domain.UserBrowseHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBrowseHistoryRepository extends JpaRepository<UserBrowseHistory, Long> {

    List<UserBrowseHistory> findByUserIdAndTargetTypeOrderByBrowseTimeDesc(Long userId, String targetType);

    boolean existsByUserIdAndTargetTypeAndTargetId(Long userId, String targetType, Long targetId);

    void deleteByUserIdAndTargetTypeAndTargetId(Long userId, String targetType, Long targetId);

    void deleteByTargetTypeAndTargetId(String targetType, Long targetId);
}
