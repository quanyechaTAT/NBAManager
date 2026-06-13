package com.nbamanager.web;

import com.nbamanager.security.UserPrincipal;
import com.nbamanager.service.CommentService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/post/{postId}")
    public List<CommentDto> listByPost(@PathVariable Long postId) {
        Long userId = getCurrentUserId();
        return commentService.listByPost(postId, userId);
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
            return commentService.createForGame(user.getId(), request);
        }
        return commentService.create(user.getId(), request.postId(), request);
    }

    @PostMapping("/{id}/like")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public java.util.Map<String, Boolean> toggleLike(@PathVariable Long id,
                                                     @AuthenticationPrincipal UserPrincipal user) {
        boolean liked = commentService.toggleLike(id, user.getId());
        return java.util.Map.of("liked", liked);
    }

    @PostMapping("/{id}/pin")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public void togglePin(@PathVariable Long id,
                          @AuthenticationPrincipal UserPrincipal user) {
        commentService.togglePin(id, user.getId());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public void delete(@PathVariable Long id,
                       @AuthenticationPrincipal UserPrincipal user) {
        commentService.delete(id, user.getId());
    }

    private Long getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetails ud) {
            return ((UserPrincipal) ud).getId();
        }
        return null;
    }
}
