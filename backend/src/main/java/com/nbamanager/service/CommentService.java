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
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class CommentService {

    public record CommentTarget(Long postId, String gameId) {}

    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final PostRepository postRepository;
    private final UserAccountRepository userAccountRepository;
    private final NotificationService notificationService;
    private final PlatformTransactionManager transactionManager;

    /** 同评论并发切换的最大尝试次数（首次 + 重试）。翻转语义下重试安全。 */
    private static final int TOGGLE_MAX_ATTEMPTS = 3;

    @Transactional(readOnly = true)
    public CommentTarget getTarget(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "评论不存在: " + commentId));
        return new CommentTarget(comment.getPostId(), comment.getGameId());
    }

    @Transactional(readOnly = true)
    public List<CommentDto> listByPost(Long postId, Long currentUserId) {
        List<Comment> all = commentRepository.findByPostIdOrderByCreateTimeAsc(postId);

        // 获取帖子作者ID
        Post post = postRepository.findById(postId).orElse(null);
        Long postAuthorId = post != null ? post.getUserId() : null;

        // 第 8 轮 P1-1：当前用户对这批评论的点赞状态一次 IN 查询取回
        //（此前 buildTree 内每行一次 existsByCommentIdAndUserId，N+1）
        Set<Long> likedIds = (currentUserId == null || all.isEmpty())
                ? Set.of()
                : new java.util.HashSet<>(commentLikeRepository.findLikedCommentIds(
                        currentUserId, all.stream().map(Comment::getId).collect(java.util.stream.Collectors.toList())));

        return buildCommentTree(all, currentUserId, postAuthorId, likedIds);
    }

    @Transactional(readOnly = true)
    public List<CommentDto> listByGameId(String gameId, Long currentUserId) {
        List<Comment> all = commentRepository.findByGameIdOrderByCreateTimeAsc(gameId);

        Set<Long> likedIds = (currentUserId == null || all.isEmpty())
                ? Set.of()
                : new java.util.HashSet<>(commentLikeRepository.findLikedCommentIds(
                        currentUserId, all.stream().map(Comment::getId).collect(java.util.stream.Collectors.toList())));

        return buildCommentTree(all, currentUserId, null, likedIds);
    }

    private List<CommentDto> buildCommentTree(List<Comment> all, Long currentUserId, Long postAuthorId,
                                              Set<Long> likedIds) {
        // 批量加载用户身份（用户名 + 头像）
        Map<Long, UserAccount> identityMap = loadIdentities(all);

        // 获取帖子作者点赞过的评论ID
        Set<Long> authorLikedIds = new java.util.HashSet<>();
        if (postAuthorId != null) {
            authorLikedIds = commentLikeRepository.findByUserId(postAuthorId).stream()
                    .map(CommentLike::getCommentId)
                    .collect(Collectors.toSet());
        }

        // 获取帖子作者回复过的评论ID
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
            result.add(buildTree(top, grouped, currentUserId, postAuthorId, identityMap, authorLikedIds, authorRepliedIds, likedIds));
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
                                  Long postAuthorId, Map<Long, UserAccount> identityMap,
                                  Set<Long> authorLikedIds, Set<Long> authorRepliedIds, Set<Long> likedIds) {
        List<Comment> children = grouped.getOrDefault(comment.getId(), List.of());
        List<CommentDto> replies = children.stream()
                .filter(c -> !c.getId().equals(comment.getId()))
                .map(c -> buildTree(c, grouped, currentUserId, postAuthorId, identityMap, authorLikedIds, authorRepliedIds, likedIds))
                .collect(Collectors.toList());
        boolean liked = currentUserId != null && likedIds.contains(comment.getId());
        boolean likedByAuthor = postAuthorId != null && authorLikedIds.contains(comment.getId());
        boolean repliedByAuthor = postAuthorId != null && authorRepliedIds.contains(comment.getId());
        UserAccount account = identityMap.get(comment.getUserId());
        String username = account != null ? account.getUsername() : "用户#" + comment.getUserId();
        String avatarUrl = account != null ? account.getAvatarUrl() : null;
        return new CommentDto(
                comment.getId(), comment.getPostId(), comment.getUserId(),
                username, avatarUrl, comment.getParentId(), comment.getContent(),
                comment.getLikeCount(), liked, likedByAuthor, repliedByAuthor,
                comment.getPinned(), comment.getCreateTime(), replies);
    }

    private Map<Long, UserAccount> loadIdentities(List<Comment> comments) {
        Set<Long> userIds = comments.stream().map(Comment::getUserId).collect(Collectors.toSet());
        return userAccountRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, u -> u));
    }

    /**
     * 评论点赞 / 取消（翻转语义）。
     *
     * 与 PostService.toggleLike 同一形态：DELETE + INSERT IGNORE + 原子计数。
     * 锁按【评论】串行化（而非 (评论,用户)），保证同一评论的所有切换排队，
     * 避免不同用户并发切换同一评论时锁顺序交错导致 InnoDB 死锁；
     * 并用 TransactionTemplate 在【新事务】里做有限重试兜底。
     */
    public boolean toggleLike(Long commentId, Long userId) {
        PessimisticLockingFailureException lastFailure = null;
        for (int attempt = 1; attempt <= TOGGLE_MAX_ATTEMPTS; attempt++) {
            try {
                return new TransactionTemplate(transactionManager)
                        .execute(status -> doToggleLike(commentId, userId));
            } catch (PessimisticLockingFailureException e) {
                lastFailure = e;
                log.warn("评论点赞切换遇到锁冲突，第 {}/{} 次尝试: commentId={}, userId={}, error={}",
                        attempt, TOGGLE_MAX_ATTEMPTS, commentId, userId, e.getMessage());
            }
        }
        log.error("评论点赞切换重试 {} 次仍失败: commentId={}, userId={}",
                TOGGLE_MAX_ATTEMPTS, commentId, userId, lastFailure);
        throw new ApiException(HttpStatus.CONFLICT, "点赞状态正在更新，请稍后重试");
    }

    /** 单次评论点赞切换（在独立事务中执行）。 */
    private boolean doToggleLike(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "评论不存在: " + commentId));
        return com.nbamanager.service.support.KeyedMutex.execute(
                "comment-like:" + commentId,
                () -> {
                    int deleted = commentLikeRepository.deleteByCommentIdAndUserId(commentId, userId);
                    if (deleted > 0) {
                        commentRepository.bumpLikeCount(commentId, -1);
                        return false;
                    }

                    int inserted = commentLikeRepository.insertIgnore(commentId, userId);
                    if (inserted > 0) {
                        commentRepository.bumpLikeCount(commentId, +1);
                        // 通知评论作者
                        String senderName = userAccountRepository.findById(userId)
                                .map(UserAccount::getUsername).orElse("用户");
                        notificationService.notifyCommentAuthor(
                                comment.getUserId(), userId, senderName, comment.getContent(),
                                commentId, comment.getPostId());
                        return true;
                    }

                    throw new ApiException(HttpStatus.CONFLICT, "点赞状态正在更新，请稍后重试");
                });
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
        // 两条字段各自原子 UPDATE：绝不能在这里再 postRepository.save(post)。
        // JPA 的 save 对已存在实体会 merge，会把内存中过期的 commentCount（进入本方法时读到的值）
        // 整行写回数据库，抹掉下面这行的自增 —— 安全报告 §十 10.7 的 VULN
        //（实测发 3 条评论后 comment_count 仍为 0，而 last_reply_time 却是新的）。
        postRepository.bumpCommentCount(postId, +1);
        postRepository.touchLastReplyTime(postId);

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

    @Transactional
    public CommentDto createForGame(Long userId, CommentRequest req) {
        String gameId = req.gameId();
        if (gameId == null || gameId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "比赛ID不能为空");
        }
        Comment comment = new Comment();
        comment.setGameId(gameId);
        comment.setUserId(userId);
        comment.setContent(req.content());
        comment.setParentId(req.parentId());
        Comment saved = commentRepository.save(comment);

        // 通知被回复的评论作者
        if (req.parentId() != null) {
            String senderName = userAccountRepository.findById(userId)
                    .map(UserAccount::getUsername).orElse("用户");
            Comment parentComment = commentRepository.findById(req.parentId()).orElse(null);
            if (parentComment != null) {
                notificationService.notifyCommentAuthorReply(
                        parentComment.getUserId(), userId, senderName, "比赛评论", null);
            }
        }

        return toDto(saved);
    }

    @CacheEvict(value = {"posts", "dashboard"}, allEntries = true)
    @Transactional
    public CommentDto update(Long id, Long userId, CommentRequest req) {
        Comment comment = commentRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "评论不存在: " + id));
        if (!comment.getUserId().equals(userId) && !isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "无权修改此评论");
        }
        comment.setContent(req.content());
        return toDto(commentRepository.save(comment));
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
            if (comment.getPostId() != null) {
                Post post = postRepository.findById(comment.getPostId()).orElse(null);
                isPostAuthor = post != null && post.getUserId().equals(userId);
            }
        }
        if (!isCommentAuthor && !isPostAuthor && !isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "无权删除此评论");
        }
        // 先清从属数据再删主记录：comment_likes 没有外键级联，
        // 此前只删评论行会留下孤儿点赞（压测 §九 P4 实测残留 1 行，
        // comments 维度 SUM(like_count)=3 vs COUNT(comment_likes)=5）。
        commentLikeRepository.deleteByCommentIdIn(List.of(id));
        commentRepository.deleteById(id);
        if (comment.getPostId() != null) {
            postRepository.bumpCommentCount(comment.getPostId(), -1);
        }
    }

    private CommentDto toDto(Comment comment) {
        UserAccount account = userAccountRepository.findById(comment.getUserId()).orElse(null);
        String username = account != null ? account.getUsername() : "用户";
        String avatarUrl = account != null ? account.getAvatarUrl() : null;
        return new CommentDto(
                comment.getId(), comment.getPostId(), comment.getUserId(),
                username, avatarUrl, comment.getParentId(), comment.getContent(),
                comment.getLikeCount(), false, false, false,
                comment.getPinned(), comment.getCreateTime(), List.of());
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
