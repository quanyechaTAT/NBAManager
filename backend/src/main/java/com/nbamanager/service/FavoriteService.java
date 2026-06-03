package com.nbamanager.service;

import com.nbamanager.domain.GameNews;
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

    @CacheEvict(value = {"favorites", "dashboard"}, allEntries = true)
    @Transactional
    public boolean toggle(Long userId, String targetType, Long targetId) {
        if (!"TEAM".equals(targetType) && !"PLAYER".equals(targetType) && !"POST".equals(targetType) && !"NEWS".equals(targetType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "targetType 必须是 TEAM、PLAYER、POST 或 NEWS");
        }
        if (userFavoriteRepository.existsByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId)) {
            userFavoriteRepository.deleteByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId);
            if ("NEWS".equals(targetType)) {
                updateNewsFavoriteCount(targetId, -1);
            }
            return false;
        } else {
            UserFavorite fav = new UserFavorite();
            fav.setUserId(userId);
            fav.setTargetType(targetType);
            fav.setTargetId(targetId);
            userFavoriteRepository.save(fav);
            if ("NEWS".equals(targetType)) {
                updateNewsFavoriteCount(targetId, 1);
            }
            // 通知帖子作者被收藏
            if ("POST".equals(targetType)) {
                notifyPostFavorited(userId, targetId);
            }
            return true;
        }
    }

    private void notifyPostFavorited(Long userId, Long postId) {
        postRepository.findById(postId).ifPresent(post -> {
            String senderName = userAccountRepository.findById(userId)
                    .map(u -> u.getUsername()).orElse("用户");
            notificationService.notifyFavorited(post.getUserId(), userId, senderName, post.getTitle(), postId);
        });
    }

    private void updateNewsFavoriteCount(Long newsId, int delta) {
        GameNews news = gameNewsRepository.findById(newsId).orElse(null);
        if (news != null) {
            news.setFavoriteCount(Math.max(0, news.getFavoriteCount() + delta));
            gameNewsRepository.save(news);
        }
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
