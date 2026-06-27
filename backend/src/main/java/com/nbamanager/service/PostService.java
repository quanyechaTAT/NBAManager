package com.nbamanager.service;

import com.nbamanager.domain.Poll;
import com.nbamanager.domain.Post;
import com.nbamanager.domain.PostLike;
import com.nbamanager.exception.ApiException;
import com.nbamanager.repository.CommentRepository;
import com.nbamanager.repository.PollRepository;
import com.nbamanager.repository.PostLikeRepository;
import com.nbamanager.repository.PostRepository;
import com.nbamanager.repository.UserBrowseHistoryRepository;
import com.nbamanager.repository.UserFavoriteRepository;
import com.nbamanager.web.dto.PostDto;
import com.nbamanager.web.dto.PostRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final CommentRepository commentRepository;
    private final UserFavoriteRepository userFavoriteRepository;
    private final UserBrowseHistoryRepository userBrowseHistoryRepository;
    private final NotificationService notificationService;
    private final PollRepository pollRepository;
    private final com.nbamanager.repository.UserAccountRepository userAccountRepository;

    @Transactional(readOnly = true)
    public Page<PostDto> list(String category, String keyword, Pageable pageable) {
        boolean hasCategory = category != null && !category.isBlank();
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        Page<Post> page;
        if (hasCategory && hasKeyword) {
            page = postRepository.findByCategoryAndTitleContainingIgnoreCase(category.trim(), keyword.trim(), pageable);
        } else if (hasCategory) {
            page = postRepository.findByCategory(category.trim(), pageable);
        } else if (hasKeyword) {
            page = postRepository.findByTitleContainingIgnoreCase(keyword.trim(), pageable);
        } else {
            page = postRepository.findAll(pageable);
        }
        Long currentUserId = getCurrentUserId();

        // 批量预加载当前用户的点赞和收藏状态，避免N+1查询
        final Set<Long> likedPostIds;
        final Set<Long> favoritedPostIds;
        if (currentUserId != null) {
            likedPostIds = postLikeRepository.findByUserId(currentUserId).stream()
                    .map(PostLike::getPostId)
                    .collect(Collectors.toSet());
            favoritedPostIds = userFavoriteRepository.findByUserIdAndTargetType(currentUserId, "POST").stream()
                    .map(f -> f.getTargetId())
                    .collect(Collectors.toSet());
        } else {
            likedPostIds = new java.util.HashSet<>();
            favoritedPostIds = new java.util.HashSet<>();
        }

        return page.map(p -> toDtoOptimized(p, currentUserId, likedPostIds, favoritedPostIds));
    }

    @CacheEvict(value = "posts", allEntries = true)
    @Transactional
    public PostDto get(Long id) {
        Post post = postRepository.findById(id).orElseThrow(() -> notFound(id));
        post.setViewCount(post.getViewCount() + 1);
        postRepository.save(post);
        Long currentUserId = getCurrentUserId();
        return toDto(post, currentUserId);
    }

    @CacheEvict(value = {"posts", "dashboard"}, allEntries = true)
    @Transactional
    public PostDto create(Long userId, PostRequest req) {
        return create(userId, req, null);
    }

    @CacheEvict(value = {"posts", "dashboard"}, allEntries = true)
    @Transactional
    public PostDto create(Long userId, PostRequest req, com.nbamanager.web.dto.PollRequest pollReq) {
        Post post = new Post();
        post.setUserId(userId);
        post.setTitle(req.title());
        post.setContent(req.content());
        post.setCategory(req.category());
        post.setTags(req.tags());
        if (pollReq != null) {
            post.setHasPoll(true);
        }
        Post saved = postRepository.save(post);
        if (pollReq != null) {
            Poll poll = new Poll();
            poll.setPostId(saved.getId());
            poll.setQuestion(pollReq.question());
            poll.setOption1(pollReq.option1());
            poll.setOption2(pollReq.option2());
            poll.setOption3(pollReq.option3());
            poll.setOption4(pollReq.option4());
            pollRepository.save(poll);
        }
        return toDto(saved, userId);
    }

    @CacheEvict(value = {"posts", "dashboard"}, allEntries = true)
    @Transactional
    public PostDto update(Long id, Long userId, PostRequest req) {
        Post post = postRepository.findById(id).orElseThrow(() -> notFound(id));
        if (!post.getUserId().equals(userId) && !isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "无权修改此帖子");
        }
        post.setTitle(req.title());
        post.setContent(req.content());
        post.setCategory(req.category());
        post.setTags(req.tags());
        return toDto(postRepository.save(post), userId);
    }

    @CacheEvict(value = {"posts", "dashboard"}, allEntries = true)
    @Transactional
    public void delete(Long id, Long userId) {
        Post post = postRepository.findById(id).orElseThrow(() -> notFound(id));
        if (!post.getUserId().equals(userId) && !isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "无权删除此帖子");
        }
        // 如果是管理员删除他人帖子，通知帖子作者
        if (!post.getUserId().equals(userId) && isAdmin()) {
            notificationService.notifyPostDeleted(post.getUserId(), post.getTitle());
        }
        postRepository.deleteById(id);
    }

    @Transactional
    public boolean toggleLike(Long postId, Long userId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> notFound(postId));
        if (postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            postLikeRepository.deleteByPostIdAndUserId(postId, userId);
            post.setLikeCount(post.getLikeCount() - 1);
            postRepository.save(post);
            return false;
        } else {
            PostLike like = new PostLike();
            like.setPostId(postId);
            like.setUserId(userId);
            postLikeRepository.save(like);
            post.setLikeCount(post.getLikeCount() + 1);
            postRepository.save(post);
            // 通知帖子作者
            String senderName = getSenderName(userId);
            notificationService.notifyPostAuthor(post.getUserId(), userId, senderName, post.getTitle(), postId);
            return true;
        }
    }

    @Transactional(readOnly = true)
    public Page<PostDto> hot(Pageable pageable) {
        Page<Post> page = postRepository.findByOrderByViewCountDesc(pageable);
        Long currentUserId = getCurrentUserId();
        return page.map(p -> toDto(p, currentUserId));
    }

    /** 清理超出100条的旧帖子，同步删除浏览历史和收藏 */
    @CacheEvict(value = {"posts", "dashboard"}, allEntries = true)
    @Transactional
    public int cleanupOldPosts(int keepCount) {
        List<Post> allPosts = postRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createTime"));
        if (allPosts.size() <= keepCount) return 0;

        List<Post> toDelete = allPosts.subList(keepCount, allPosts.size());
        int deleted = 0;
        for (Post post : toDelete) {
            Long postId = post.getId();
            // 删除相关的浏览历史
            try {
                userBrowseHistoryRepository.deleteByTargetTypeAndTargetId("POST", postId);
            } catch (Exception e) { /* ignore */ }
            // 删除相关的收藏
            try {
                userFavoriteRepository.deleteByTargetTypeAndTargetId("POST", postId);
            } catch (Exception e) { /* ignore */ }
            // 删除相关的评论
            try {
                commentRepository.deleteByPostId(postId);
            } catch (Exception e) { /* ignore */ }
            postRepository.delete(post);
            deleted++;
        }
        return deleted;
    }

    private PostDto toDto(Post post, Long currentUserId) {
        boolean likedByMe = currentUserId != null && postLikeRepository.existsByPostIdAndUserId(post.getId(), currentUserId);
        long favoriteCount = userFavoriteRepository.countByTargetTypeAndTargetId("POST", post.getId());
        String username = userAccountRepository.findById(post.getUserId()).map(u -> u.getUsername()).orElse(null);
        java.time.LocalDateTime lastReply = post.getLastReplyTime();
        if (lastReply == null) {
            lastReply = commentRepository.findTopByPostIdOrderByCreateTimeDesc(post.getId())
                    .map(c -> c.getCreateTime()).orElse(null);
        }
        return new PostDto(
                post.getId(),
                post.getUserId(),
                username,
                post.getTitle(),
                post.getContent(),
                post.getCategory(),
                post.getTags(),
                post.getViewCount(),
                post.getLikeCount(),
                post.getCommentCount(),
                (int) favoriteCount,
                post.getIsTop(),
                post.getIsFeatured(),
                post.getHasPoll(),
                likedByMe,
                lastReply,
                post.getCreateTime());
    }

    /**
     * 优化版toDto - 使用预加载的点赞和收藏状态，避免N+1查询
     */
    private PostDto toDtoOptimized(Post post, Long currentUserId, Set<Long> likedPostIds, Set<Long> favoritedPostIds) {
        boolean likedByMe = currentUserId != null && likedPostIds.contains(post.getId());
        long favoriteCount = userFavoriteRepository.countByTargetTypeAndTargetId("POST", post.getId());
        String username = userAccountRepository.findById(post.getUserId()).map(u -> u.getUsername()).orElse(null);
        java.time.LocalDateTime lastReply = post.getLastReplyTime();
        if (lastReply == null) {
            lastReply = commentRepository.findTopByPostIdOrderByCreateTimeDesc(post.getId())
                    .map(c -> c.getCreateTime()).orElse(null);
        }
        return new PostDto(
                post.getId(),
                post.getUserId(),
                username,
                post.getTitle(),
                post.getContent(),
                post.getCategory(),
                post.getTags(),
                post.getViewCount(),
                post.getLikeCount(),
                post.getCommentCount(),
                (int) favoriteCount,
                post.getIsTop(),
                post.getIsFeatured(),
                post.getHasPoll(),
                likedByMe,
                lastReply,
                post.getCreateTime());
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetails ud) {
            return ((com.nbamanager.security.UserPrincipal) ud).getId();
        }
        return null;
    }

    private String getSenderName(Long userId) {
        return userAccountRepository.findById(userId)
                .map(u -> u.getUsername())
                .orElse("用户");
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            return auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        }
        return false;
    }

    private static ApiException notFound(Long id) {
        return new ApiException(HttpStatus.NOT_FOUND, "帖子不存在: " + id);
    }
}
