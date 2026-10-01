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

    /**
     * 投票（安全报告 §十五 M4）：请求体缺 optionIndex 时必须 400，
     * 而不是让 null 进入服务层拆箱成 NPE → 500。
     */
    @PostMapping("/{id}/vote")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public PollDto vote(@PathVariable Long id,
                        @AuthenticationPrincipal UserPrincipal user,
                        @RequestBody java.util.Map<String, Integer> body) {
        Integer optionIndex = body == null ? null : body.get("optionIndex");
        if (optionIndex == null) {
            throw new com.nbamanager.exception.ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "缺少 optionIndex");
        }
        return pollService.vote(id, user.getId(), optionIndex);
    }
}
