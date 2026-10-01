package com.nbamanager.service;

/**
 * 同步方法执行结果：供 Worker 判断是否真正成功
 */
public record SyncExecResult(
        boolean providerSucceeded,
        boolean partial,
        int fetched,
        int inserted,
        int updated,
        String errorCode,
        String error
) {
    public static SyncExecResult success(int fetched, int inserted, int updated) {
        return new SyncExecResult(true, false, fetched, inserted, updated, null, null);
    }

    public static SyncExecResult partial(int fetched, int inserted, int updated, String error) {
        return new SyncExecResult(true, true, fetched, inserted, updated,
                "PROVIDER_PARTIAL", error);
    }

    public static SyncExecResult failed(String error) {
        return failed("PROVIDER_FAILED", error);
    }

    public static SyncExecResult failed(String errorCode, String error) {
        return new SyncExecResult(false, false, 0, 0, 0, errorCode, error);
    }

    public static SyncExecResult noGames() {
        return new SyncExecResult(true, false, 0, 0, 0, null, null);
    }
}
