package com.nbamanager.web;

import com.nbamanager.security.UserPrincipal;
import com.nbamanager.service.FavoriteService;
import com.nbamanager.web.dto.FavoriteRequest;
import com.nbamanager.web.dto.UserFavoriteDto;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public List<UserFavoriteDto> list(@AuthenticationPrincipal UserPrincipal user) {
        return favoriteService.list(user.getId());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public java.util.Map<String, Boolean> toggle(@AuthenticationPrincipal UserPrincipal user,
                                                 @Valid @RequestBody FavoriteRequest request) {
        boolean favorited = favoriteService.toggle(user.getId(), request.targetType(), request.targetId());
        return java.util.Map.of("favorited", favorited);
    }

    @GetMapping("/players")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public List<Long> followedPlayers(@AuthenticationPrincipal UserPrincipal user) {
        return favoriteService.getFollowedPlayers(user.getId());
    }

    @GetMapping("/teams")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public List<Long> followedTeams(@AuthenticationPrincipal UserPrincipal user) {
        return favoriteService.getFollowedTeams(user.getId());
    }

    @GetMapping("/posts")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public List<Long> favoritedPosts(@AuthenticationPrincipal UserPrincipal user) {
        return favoriteService.getFavoritedPosts(user.getId());
    }

    @GetMapping("/news")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public List<Long> favoritedNews(@AuthenticationPrincipal UserPrincipal user) {
        return favoriteService.getFavoritedNews(user.getId());
    }
}
