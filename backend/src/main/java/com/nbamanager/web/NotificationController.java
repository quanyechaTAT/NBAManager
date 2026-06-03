package com.nbamanager.web;

import com.nbamanager.security.UserPrincipal;
import com.nbamanager.service.NotificationService;
import com.nbamanager.web.dto.NotificationDto;
import com.nbamanager.web.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public PageResponse<NotificationDto> list(
            @AuthenticationPrincipal UserPrincipal user,
            @PageableDefault(size = 20, sort = "createTime", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.from(notificationService.list(user.getId(), pageable));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public java.util.Map<String, Integer> unreadCount(@AuthenticationPrincipal UserPrincipal user) {
        return java.util.Map.of("count", notificationService.unreadCount(user.getId()));
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public void markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
    }

    @PutMapping("/read-all")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public void markAllAsRead(@AuthenticationPrincipal UserPrincipal user) {
        notificationService.markAllAsRead(user.getId());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public void delete(@PathVariable Long id) {
        notificationService.delete(id);
    }

    @DeleteMapping("/clear-read")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public void clearRead(@AuthenticationPrincipal UserPrincipal user) {
        notificationService.clearRead(user.getId());
    }
}
