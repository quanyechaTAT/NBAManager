package com.nbamanager.web;

import com.nbamanager.security.UserPrincipal;
import com.nbamanager.service.CommentService;
import com.nbamanager.service.CommentRealtimePublisher;
import com.nbamanager.web.dto.CommentDto;
import com.nbamanager.web.dto.CommentRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final CommentRealtimePublisher realtimePublisher;

    @GetMapping("/post/{postId}")
    public List<CommentDto> listByPost(@PathVariable Long postId) {
        Long userId = getCurrentUserId();
        return commentService.listByPost(postId, userId);
    }

    /** 由评论 id 反查所属帖子 id：COMMENT_LIKE 通知（relatedId=commentId）跳转用。 */
    @GetMapping("/{id}/post-id")
    public java.util.Map<String, Long> getPostId(@PathVariable Long id) {
        return java.util.Map.of("postId", commentService.getTarget(id).postId());
    }

    @GetMapping("/game/{gameId}")
    public List<CommentDto> listByGame(@PathVariable String gameId) {
        Long userId = getCurrentUserId();
        return commentService.listByGameId(gameId, userId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public CommentDto create(@AuthenticationPrincipal UserPrincipal user,
                             @Valid @RequestBody CommentRequest request) {
        if (request.gameId() != null && !request.gameId().isBlank()) {
            CommentDto created = commentService.createForGame(user.getId(), request);
            realtimePublisher.publish(new CommentService.CommentTarget(null, request.gameId()), "CREATED");
            return created;
        }
        CommentDto created = commentService.create(user.getId(), request.postId(), request);
        realtimePublisher.publish(new CommentService.CommentTarget(request.postId(), null), "CREATED");
        return created;
    }

    @PostMapping("/{id}/like")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public java.util.Map<String, Boolean> toggleLike(@PathVariable Long id,
                                                     @AuthenticationPrincipal UserPrincipal user) {
        var target = commentService.getTarget(id);
        boolean liked = commentService.toggleLike(id, user.getId());
        realtimePublisher.publish(target, "LIKED");
        return java.util.Map.of("liked", liked);
    }

    @PostMapping("/{id}/pin")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public void togglePin(@PathVariable Long id,
                          @AuthenticationPrincipal UserPrincipal user) {
        var target = commentService.getTarget(id);
        commentService.togglePin(id, user.getId());
        realtimePublisher.publish(target, "PINNED");
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public CommentDto update(@PathVariable Long id,
                             @AuthenticationPrincipal UserPrincipal user,
                             @Valid @RequestBody CommentRequest request) {
        var target = commentService.getTarget(id);
        CommentDto updated = commentService.update(id, user.getId(), request);
        realtimePublisher.publish(target, "UPDATED");
        return updated;
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public void delete(@PathVariable Long id,
                       @AuthenticationPrincipal UserPrincipal user) {
        var target = commentService.getTarget(id);
        commentService.delete(id, user.getId());
        realtimePublisher.publish(target, "DELETED");
    }

    private Long getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetails ud) {
            return ((UserPrincipal) ud).getId();
        }
        return null;
    }
}
