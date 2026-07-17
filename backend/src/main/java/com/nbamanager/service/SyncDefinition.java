package com.nbamanager.service;

import java.time.ZonedDateTime;
import java.util.List;

public record SyncDefinition(
        String module,
        ModuleSyncWorker worker,
        int fullSyncOrder,
        List<SyncSchedulePolicy> schedules
) {
    public boolean isDue(ZonedDateTime lastSuccess, ZonedDateTime now) {
        return schedules.stream().anyMatch(policy -> policy.isDue(lastSuccess, now));
    }
}
