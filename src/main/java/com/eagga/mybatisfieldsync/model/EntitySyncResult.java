package com.eagga.mybatisfieldsync.model;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 单个实体同步流程的结果快照，供单项同步与批量向导复用。
 */
public record EntitySyncResult(@NotNull String entityName,
                               @NotNull Status status,
                               @NotNull String xmlPath,
                               int selectedFieldCount,
                               @NotNull List<String> successStatementIds,
                               @NotNull List<String> failedStatements,
                               @NotNull String detailMessage) {

    public EntitySyncResult {
        successStatementIds = List.copyOf(successStatementIds);
        failedStatements = List.copyOf(failedStatements);
    }

    public boolean hasSuccessfulStatements() {
        return !successStatementIds.isEmpty();
    }

    public int successCount() {
        return successStatementIds.size();
    }

    public static @NotNull EntitySyncResult success(@NotNull String entityName,
            @NotNull String xmlPath,
            int selectedFieldCount,
            @NotNull List<String> successStatementIds) {
        return new EntitySyncResult(entityName,
                Status.SUCCESS,
                xmlPath,
                selectedFieldCount,
                successStatementIds,
                List.of(),
                "");
    }

    public static @NotNull EntitySyncResult partial(@NotNull String entityName,
            @NotNull String xmlPath,
            int selectedFieldCount,
            @NotNull List<String> successStatementIds,
            @NotNull List<String> failedStatements) {
        return new EntitySyncResult(entityName,
                Status.PARTIAL_SUCCESS,
                xmlPath,
                selectedFieldCount,
                successStatementIds,
                failedStatements,
                String.join("; ", failedStatements));
    }

    public static @NotNull EntitySyncResult failed(@NotNull String entityName, @NotNull String detailMessage) {
        return new EntitySyncResult(entityName,
                Status.FAILED,
                "",
                0,
                List.of(),
                List.of(),
                detailMessage);
    }

    public static @NotNull EntitySyncResult skipped(@NotNull String entityName, @NotNull String detailMessage) {
        return new EntitySyncResult(entityName,
                Status.SKIPPED,
                "",
                0,
                List.of(),
                List.of(),
                detailMessage);
    }

    public enum Status {
        SUCCESS,
        PARTIAL_SUCCESS,
        FAILED,
        SKIPPED
    }
}
