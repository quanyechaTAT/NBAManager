package com.nbamanager.repository;

import com.nbamanager.domain.UserFavorite;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFavoriteRepository extends JpaRepository<UserFavorite, Long> {

    List<UserFavorite> findByUserId(Long userId);

    List<UserFavorite> findByUserIdAndTargetType(Long userId, String targetType);

    List<UserFavorite> findByTargetTypeAndTargetId(String targetType, Long targetId);

    boolean existsByUserIdAndTargetTypeAndTargetId(Long userId, String targetType, Long targetId);

    void deleteByUserIdAndTargetTypeAndTargetId(Long userId, String targetType, Long targetId);

    void deleteByTargetTypeAndTargetId(String targetType, Long targetId);

    long countByTargetTypeAndTargetId(String targetType, Long targetId);
}
