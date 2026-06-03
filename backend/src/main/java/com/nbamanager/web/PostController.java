package com.nbamanager.web;

import com.nbamanager.security.UserPrincipal;
import com.nbamanager.service.PostService;
import com.nbamanager.web.dto.PageResponse;
import com.nbamanager.web.dto.PostDto;
import com.nbamanager.web.dto.PostRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping
    public PageResponse<PostDto> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 10, sort = "createTime", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(postService.list(category, q, pageable));
    }

    @GetMapping("/hot")
    public PageResponse<PostDto> hot(
            @PageableDefault(size = 10) Pageable pageable) {
        return PageResponse.from(postService.hot(pageable));
    }

    @GetMapping("/{id}")
    public PostDto get(@PathVariable Long id) {
        return postService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public PostDto create(@AuthenticationPrincipal UserPrincipal user,
                          @Valid @RequestBody PostRequest request) {
        return postService.create(user.getId(), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public PostDto update(@PathVariable Long id,
                          @AuthenticationPrincipal UserPrincipal user,
                          @Valid @RequestBody PostRequest request) {
        return postService.update(id, user.getId(), request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public void delete(@PathVariable Long id,
                       @AuthenticationPrincipal UserPrincipal user) {
        postService.delete(id, user.getId());
    }

    @PostMapping("/{id}/like")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public java.util.Map<String, Boolean> toggleLike(@PathVariable Long id,
                                                     @AuthenticationPrincipal UserPrincipal user) {
        boolean liked = postService.toggleLike(id, user.getId());
        return java.util.Map.of("liked", liked);
    }
}
