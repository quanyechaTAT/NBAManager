package com.nbamanager.web.dto;

import java.util.List;

public record SyncOverviewResponse(
        String currentSeason,
        List<SyncModuleOverview> modules
) {}
