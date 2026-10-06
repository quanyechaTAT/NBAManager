package com.nbamanager.web.dto;

public record TeamDto(Long id, String name, String nameEn, String abbreviation, String city, String conference, String division, Integer wins, Integer losses, String logoUrl, DataMeta dataMeta) {
    public TeamDto(Long id, String name, String nameEn, String abbreviation, String city, String conference,
                   String division, Integer wins, Integer losses, String logoUrl) {
        this(id, name, nameEn, abbreviation, city, conference, division, wins, losses, logoUrl, DataMeta.unverified());
    }
}
