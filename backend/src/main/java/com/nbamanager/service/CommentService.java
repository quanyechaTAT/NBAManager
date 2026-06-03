package com.nbamanager.service;

import com.nbamanager.domain.Comment;
import com.nbamanager.domain.CommentLike;
import com.nbamanager.domain.Post;
import com.nbamanager.domain.UserAccount;
import com.nbamanager.exception.ApiException;
import com.nbamanager.repository.CommentLikeRepository;
import com.nbamanager.repository.CommentRepository;
import com.nbamanager.repository.PostRepository;
import com.nbamanager.repository.UserAccountRepository;
import com.nbamanager.web.dto.CommentDto;
import com.nbamanager.web.dto.CommentRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final PostRepository postRepository;
    private final UserAccountRepository userAccountRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<CommentDto> listByPost(Long postId, Long currentUserId) {
        List<Comment> all = commentRepository.findByPostIdOrderByCreateTimeAsc(postId);

        // 获取帖子作者ID
        Post post = postRepository.findById(postId).orElse(null);
        Long postAuthorId = post != null ? post.getUserId() : null;

        // 批量获取用户名
        Map<Long, String> usernameMap = loadUsernames(all);

        // 获取帖子作者点赞过的评论ID（使用findByUserId替代findAll）
        Set<Long> authorLikedIds = new java.util.HashSet<>();
        if (postAuthorId != null) {
            authorLikedIds = commentLikeRepository.findByUserId(postAuthorId).stream()
                    .map(CommentLike::getCommentId)
                    .collect(Collectors.toSet());
        }

        // 获取帖子作者回复过的评论ID（作者的评论的parentId）
        Set<Long> authorRepliedIds = new java.util.HashSet<>();
        if (postAuthorId != null) {
            authorRepliedIds = all.stream()
                    .filter(c -> c.getUserId().equals(postAuthorId) && c.getParentId() != null)
                    .map(Comment::getParentId)
                    .collect(Collectors.toSet());
        }

        Map<Long, List<Comment>> grouped = all.stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() == null ? c.getId() : c.getParentId()));
        List<Comment> topLevel = all.stream()
                .filter(c -> c.getParentId() == null)
                .collect(Collectors.toList());
        List<CommentDto> result = new ArrayList<>();
        for (Comment top : topLevel) {
            result.add(buildTree(top, grouped, currentUserId, postAuthorId, usernameMap, authorLikedIds, authorRepliedIds));
        }
        // 置顶评论排前面
        result.sort((a, b) -> {
            if (a.pinned() && !b.pinned()) return -1;
            if (!a.pinned() && b.pinned()) return 1;
            return 0;
        });
        return result;
    }

    private CommentDto buildTree(Comment comment, Map<Long, List<Comment>> grouped, Long currentUserId,
                                  Long postAuthorId, Map<Long, String> usernameMap,
                                  Set<Long> authorLikedIds, Set<Long> authorRepliedIds) {
        List<Comment> children = grouped.getOrDefault(comment.getId(), List.of());
        List<CommentDto> replies = children.stream()
                .filter(c -> !c.getId().equals(comment.getId()))
                .map(c -> buildTree(c, grouped, currentUserId, postAuthorId, usernameMap, authorLikedIds, authorRepliedIds))
                .collect(Collectors.toList());
        boolean liked = currentUserId != null && commentLikeRepository.existsByCommentIdAndUserId(comment.getId(), currentUserId);
        boolean likedByAuthor = postAuthorId != null && authorLikedIds.contains(comment.getId());
        boolean repliedByAuthor = postAuthorId != null && authorRepliedIds.contains(comment.getId());
        return new CommentDto(
                comment.getId(),
                comment.getPostId(),
                comment.getUserId(),
                usernameMap.getOrDefault(comment.getUserId(), "用户#" + comment.getUserId()),
                comment.getParentId(),
                comment.getContent(),
                comment.getLikeCount(),
                liked,
                likedByAuthor,
                repliedByAuthor,
                comment.getPinned(),
                comment.getCreateTime(),
                replies);
    }

    private Map<Long, String> loadUsernames(List<Comment> comments) {
        Set<Long> userIds = comments.stream().map(Comment::getUserId).collect(Collectors.toSet());
        return userAccountRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getUsername));
    }

    @Transactional
    public boolean toggleLike(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "评论不存在: " + commentId));
        if (commentLikeRepository.existsByCommentIdAndUserId(commentId, userId)) {
            commentLikeRepository.deleteByCommentIdAndUserId(commentId, userId);
            comment.setLikeCount(comment.getLikeCount() - 1);
            commentRepository.save(comment);
            return false;
        } else {
            CommentLike like = new CommentLike();
            like.setCommentId(commentId);
            like.setUserId(userId);
            commentLikeRepository.save(like);
            comment.setLikeCount(comment.getLikeCount() + 1);
            commentRepository.save(comment);
            // 通知评论作者
            String senderName = userAccountRepository.findById(userId)
                    .map(UserAccount::getUsername).orElse("用户");
            notificationService.notifyCommentAuthor(
                    comment.getUserId(), userId, senderName, comment.getContent(), comment.getPostId());
            return true;
        }
    }

    @CacheEvict(value = {"posts", "dashboard"}, allEntries = true)
    @Transactional
    public CommentDto create(Long userId, Long postId, CommentRequest req) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "帖子不存在: " + postId));
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setContent(req.content());
        comment.setParentId(req.parentId());
        Comment saved = commentRepository.save(comment);
        post.setCommentCount(post.getCommentCount() + 1);
        postRepository.save(post);

        // 发送通知
        String senderName = userAccountRepository.findById(userId)
                .map(UserAccount::getUsername).orElse("用户");

        if (req.parentId() != null) {
            // 回复评论：通知被回复的评论作者
            Comment parentComment = commentRepository.findById(req.parentId()).orElse(null);
            if (parentComment != null) {
                notificationService.notifyCommentAuthorReply(
                        parentComment.getUserId(), userId, senderName, post.getTitle(), postId);
            }
        } else {
            // 新评论：通知帖子作者
            notificationService.notifyPostAuthorNewComment(
                    post.getUserId(), userId, senderName, post.getTitle(), postId);
        }

        return toDto(saved);
    }

    @CacheEvict(value = {"posts", "dashboard"}, allEntries = true)
    @Transactional
    public void togglePin(Long id, Long userId) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "评论不存在: " + id));
        Post post = postRepository.findById(comment.getPostId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "帖子不存在"));
        if (!post.getUserId().equals(userId) && !isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "只有帖子作者可以置顶评论");
        }
        comment.setPinned(!comment.getPinned());
        commentRepository.save(comment);
    }

    @CacheEvict(value = {"posts", "dashboard"}, allEntries = true)
    @Transactional
    public void delete(Long id, Long userId) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "评论不存在: " + id));
        boolean isCommentAuthor = comment.getUserId().equals(userId);
        boolean isPostAuthor = false;
        if (!isCommentAuthor && !isAdmin()) {
            Post post = postRepository.findById(comment.getPostId()).orElse(null);
            isPostAuthor = post != null && post.getUserId().equals(userId);
        }
        if (!isCommentAuthor && !isPostAuthor && !isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "无权删除此评论");
        }
        commentRepository.deleteById(id);
        Post post = postRepository.findById(comment.getPostId()).orElse(null);
        if (post != null) {
            post.setCommentCount(Math.max(0, post.getCommentCount() - 1));
            postRepository.save(post);
        }
    }

    private CommentDto toDto(Comment comment) {
        return new CommentDto(
                comment.getId(),
                comment.getPostId(),
                comment.getUserId(),
                "用户",
                comment.getParentId(),
                comment.getContent(),
                comment.getLikeCount(),
                false,
                false,
                false,
                comment.getPinned(),
                comment.getCreateTime(),
                List.of());
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            return auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        }
        return false;
    }
}
