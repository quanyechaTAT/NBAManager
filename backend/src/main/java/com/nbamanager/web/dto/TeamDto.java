package com.nbamanager.web.dto;

public record TeamDto(Long id, String name, String city, String conference, Integer wins, Integer losses, String logoUrl) {}
