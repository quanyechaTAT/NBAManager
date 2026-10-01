package com.nbamanager.service;

/**
 * 部分同步失败：数据集一侧为空
 */
public class PartialSyncException extends RuntimeException {
    public PartialSyncException(String message) {
        super(message);
    }
}
