package com.nbamanager.service.provider;

public class ReplayOperationStaleException extends RuntimeException {

    public ReplayOperationStaleException(String gameId, String operationId) {
        super("Replay operation is no longer current for gameId=" + gameId + ", operationId=" + operationId);
    }
}
