package com.nbamanager.exception;

import org.springframework.http.HttpStatus;

/**
 * 同步任务冲突：锁被占用、队列满、Redis不可用等
 */
public class SyncJobConflictException extends ApiException {

    private final String errorCode;

    public SyncJobConflictException(String errorCode, String message) {
        super(HttpStatus.CONFLICT, message);
        this.errorCode = errorCode;
    }

    public SyncJobConflictException(HttpStatus status, String errorCode, String message) {
        super(status, message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
