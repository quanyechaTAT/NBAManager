package com.nbamanager.web.dto;

import com.nbamanager.domain.Role;
import java.time.Instant;
import java.util.List;

public record ChatPresenceResponse(
        String gameId,
        int onlineCount,
        List<Member> members,
        Instant updatedAt
) {
    public record Member(
            Long userId,
            String username,
            String avatarUrl,
            Role role
    ) {}
}
