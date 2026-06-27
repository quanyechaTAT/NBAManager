package com.nbamanager.web.dto;

public record TeamDto(Long id, String name, String nameEn, String abbreviation, String city, String conference, String division, Integer wins, Integer losses, String logoUrl) {}
