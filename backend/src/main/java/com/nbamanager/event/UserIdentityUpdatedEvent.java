package com.nbamanager.event;

public record UserIdentityUpdatedEvent(
        Long userId,
        String username,
        String avatarUrl) {}
