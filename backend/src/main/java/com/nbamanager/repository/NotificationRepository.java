package com.nbamanager.repository;

import com.nbamanager.domain.Notification;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    boolean existsByUserIdAndTypeAndRelatedIdAndIsReadFalse(Long userId, String type, Long relatedId);

    Page<Notification> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);

    long countByUserIdAndIsReadFalse(Long userId);

    List<Notification> findByUserIdAndIsReadFalse(Long userId);

    List<Notification> findByUserIdAndIsReadTrue(Long userId);
}
