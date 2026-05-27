package com.nbamanager.web.dto;

import com.nbamanager.domain.Role;

public record LoginResponse(String token, String username, Role role) {}
