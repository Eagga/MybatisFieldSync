package com.eagga.mybatisfieldsync.model;

/**
 * 同步流程使用的领域异常。
 */
public class SyncException extends Exception {
    public SyncException(String message) {
        super(message);
    }
}
