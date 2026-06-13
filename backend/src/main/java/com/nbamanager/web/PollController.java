package com.nbamanager.web;

import com.nbamanager.security.UserPrincipal;
import com.nbamanager.service.PollService;
import com.nbamanager.web.dto.PollDto;
import com.nbamanager.web.dto.PollRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/polls")
@RequiredArgsConstructor
public class PollController {

    private final PollService pollService;

    @GetMapping("/post/{postId}")
    public PollDto getByPostId(@PathVariable Long postId,
                               @AuthenticationPrincipal UserPrincipal user) {
        Long userId = user != null ? user.getId() : null;
        return pollService.getByPostId(postId, userId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public PollDto create(@AuthenticationPrincipal UserPrincipal user,
                          @Valid @RequestBody PollRequest request) {
        return pollService.create(user.getId(), request);
    }

    @PostMapping("/{id}/vote")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public PollDto vote(@PathVariable Long id,
                        @AuthenticationPrincipal UserPrincipal user,
                        @RequestBody java.util.Map<String, Integer> body) {
        return pollService.vote(id, user.getId(), body.get("optionIndex"));
    }
}
