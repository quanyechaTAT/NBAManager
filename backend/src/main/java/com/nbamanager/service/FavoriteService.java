package com.nbamanager.service;

import com.nbamanager.domain.UserFavorite;
import com.nbamanager.exception.ApiException;
import com.nbamanager.repository.GameNewsRepository;
import com.nbamanager.repository.UserFavoriteRepository;
import com.nbamanager.web.dto.UserFavoriteDto;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final UserFavoriteRepository userFavoriteRepository;
    private final GameNewsRepository gameNewsRepository;
    private final NotificationService notificationService;
    private final com.nbamanager.repository.PostRepository postRepository;
    private final com.nbamanager.repository.UserAccountRepository userAccountRepository;

    @Transactional(readOnly = true)
    public List<UserFavoriteDto> list(Long userId) {
        return userFavoriteRepository.findByUserId(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * 收藏 / 取消收藏。
     *
     * 缓存：收藏 POST 会改变帖子列表基座里的 favoriteCount，因此必须连"posts"一起失效
     * （@CacheEvict 的 condition 是整条注解级别，不能只对 POST 生效又不影响 favorites/dashboard，
     * 所以这里对四种 targetType 统一失效；TEAM/PLAYER/NEWS 收藏是多一次可接受的空刷）。
     */
    @CacheEvict(value = {"favorites", "dashboard", "posts"}, allEntries = true)
    @Transactional
    public boolean toggle(Long userId, String targetType, Long targetId) {
        if (!"TEAM".equals(targetType) && !"PLAYER".equals(targetType) && !"POST".equals(targetType) && !"NEWS".equals(targetType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "targetType 必须是 TEAM、PLAYER、POST 或 NEWS");
        }

        // 同一 (userId, targetType, targetId) 串行化：DELETE + INSERT 并发交错会死锁
        return com.nbamanager.service.support.KeyedMutex.execute(
                "favorite:" + userId + ":" + targetType + ":" + targetId,
                () -> {
                    int deleted = userFavoriteRepository.deleteByUserIdAndTargetTypeAndTargetId(
                            userId, targetType, targetId);
                    if (deleted > 0) {
                        if ("NEWS".equals(targetType)) {
                            updateNewsFavoriteCount(targetId, -1);
                        }
                        return false;
                    }

                    int inserted = userFavoriteRepository.insertIgnore(userId, targetType, targetId);
                    if (inserted <= 0) {
                        throw new ApiException(HttpStatus.CONFLICT, "收藏状态正在更新，请稍后重试");
                    }

                    if ("NEWS".equals(targetType)) {
                        updateNewsFavoriteCount(targetId, 1);
                    }
                    // 通知帖子作者被收藏
                    if ("POST".equals(targetType)) {
                        notifyPostFavorited(userId, targetId);
                    }
                    return true;
                });
    }

    private void notifyPostFavorited(Long userId, Long postId) {
        postRepository.findById(postId).ifPresent(post -> {
            String senderName = userAccountRepository.findById(userId)
                    .map(u -> u.getUsername()).orElse("用户");
            notificationService.notifyFavorited(post.getUserId(), userId, senderName, post.getTitle(), postId);
        });
    }

    private void updateNewsFavoriteCount(Long newsId, int delta) {
        // 原子 UPDATE：并发收藏/取消时读改写会丢失计数
        gameNewsRepository.bumpFavoriteCount(newsId, delta);
    }

    @Transactional(readOnly = true)
    public List<Long> getFollowedPlayers(Long userId) {
        return userFavoriteRepository.findByUserIdAndTargetType(userId, "PLAYER").stream()
                .map(UserFavorite::getTargetId)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Long> getFollowedTeams(Long userId) {
        return userFavoriteRepository.findByUserIdAndTargetType(userId, "TEAM").stream()
                .map(UserFavorite::getTargetId)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Long> getFavoritedPosts(Long userId) {
        return userFavoriteRepository.findByUserIdAndTargetType(userId, "POST").stream()
                .map(UserFavorite::getTargetId)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Long> getFavoritedNews(Long userId) {
        return userFavoriteRepository.findByUserIdAndTargetType(userId, "NEWS").stream()
                .map(UserFavorite::getTargetId)
                .collect(Collectors.toList());
    }

    private UserFavoriteDto toDto(UserFavorite fav) {
        return new UserFavoriteDto(fav.getId(), fav.getTargetType(), fav.getTargetId(), fav.getCreateTime());
    }
}
