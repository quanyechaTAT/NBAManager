package com.nbamanager.event;

import java.time.ZonedDateTime;

public record AdminLoginEvent(String username, ZonedDateTime loggedInAt) {}
