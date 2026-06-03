package com.nbamanager.service;

import com.nbamanager.domain.UserBrowseHistory;
import com.nbamanager.repository.UserBrowseHistoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BrowseHistoryService {

    private final UserBrowseHistoryRepository userBrowseHistoryRepository;

    @Transactional
    public void recordBrowse(Long userId, String targetType, Long targetId) {
        if (userBrowseHistoryRepository.existsByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId)) {
            userBrowseHistoryRepository.deleteByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId);
        }
        UserBrowseHistory history = new UserBrowseHistory();
        history.setUserId(userId);
        history.setTargetType(targetType);
        history.setTargetId(targetId);
        userBrowseHistoryRepository.save(history);
    }

    @Transactional(readOnly = true)
    public List<Long> getBrowsedNewsIds(Long userId) {
        return userBrowseHistoryRepository.findByUserIdAndTargetTypeOrderByBrowseTimeDesc(userId, "NEWS").stream()
                .map(UserBrowseHistory::getTargetId)
                .limit(50)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> getBrowsedPostIds(Long userId) {
        return userBrowseHistoryRepository.findByUserIdAndTargetTypeOrderByBrowseTimeDesc(userId, "POST").stream()
                .map(UserBrowseHistory::getTargetId)
                .limit(50)
                .toList();
    }

    @Transactional
    public void deleteSingle(Long userId, String targetType, Long targetId) {
        userBrowseHistoryRepository.deleteByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId);
    }

    @Transactional
    public void clearAll(Long userId, String targetType) {
        List<UserBrowseHistory> records = userBrowseHistoryRepository.findByUserIdAndTargetTypeOrderByBrowseTimeDesc(userId, targetType);
        userBrowseHistoryRepository.deleteAll(records);
    }
}
