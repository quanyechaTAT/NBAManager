package com.nbamanager.service;

public record SyncCommand(String module, String triggerType, String requestedBy) {

    public static SyncCommand manual(String module, String user) {
        return new SyncCommand(module, "MANUAL", user);
    }

    public static SyncCommand scheduled(String module) {
        return new SyncCommand(module, "SCHEDULED", "scheduler");
    }

    public static SyncCommand loginCatchup(String module, String user) {
        return new SyncCommand(module, "LOGIN_CATCHUP", user);
    }
}
