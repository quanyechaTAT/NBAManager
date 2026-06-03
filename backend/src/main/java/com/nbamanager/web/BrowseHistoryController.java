package com.nbamanager.web;

import com.nbamanager.security.UserPrincipal;
import com.nbamanager.service.BrowseHistoryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/browse-history")
@RequiredArgsConstructor
public class BrowseHistoryController {

    private final BrowseHistoryService browseHistoryService;

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public void recordBrowse(@AuthenticationPrincipal UserPrincipal user,
                             @RequestParam String targetType,
                             @RequestParam Long targetId) {
        browseHistoryService.recordBrowse(user.getId(), targetType, targetId);
    }

    @GetMapping("/news")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public List<Long> getBrowsedNewsIds(@AuthenticationPrincipal UserPrincipal user) {
        return browseHistoryService.getBrowsedNewsIds(user.getId());
    }

    @GetMapping("/posts")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public List<Long> getBrowsedPostIds(@AuthenticationPrincipal UserPrincipal user) {
        return browseHistoryService.getBrowsedPostIds(user.getId());
    }

    @DeleteMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public void deleteSingle(@AuthenticationPrincipal UserPrincipal user,
                             @RequestParam String targetType,
                             @RequestParam Long targetId) {
        browseHistoryService.deleteSingle(user.getId(), targetType, targetId);
    }

    @DeleteMapping("/clear")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public void clearAll(@AuthenticationPrincipal UserPrincipal user,
                         @RequestParam String targetType) {
        browseHistoryService.clearAll(user.getId(), targetType);
    }
}
