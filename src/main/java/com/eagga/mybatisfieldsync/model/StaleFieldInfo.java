package com.eagga.mybatisfieldsync.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 描述 XML 中已失效（在实体类中不再存在）的字段引用。
 */
public record StaleFieldInfo(@NotNull String property,
                             @Nullable String column,
                             @NotNull String statementId,
                             @NotNull StaleType type) {

    public enum StaleType {
        /** resultMap 中的 result/id 标签 */
        RESULT_MAPPING,
        /** insert 列名 */
        INSERT_COLUMN,
        /** insert 值占位符 */
        INSERT_VALUE,
        /** update set 赋值 */
        UPDATE_ASSIGNMENT,
        /** where 条件 */
        WHERE_CONDITION,
        /** base_column_list 列名 */
        BASE_COLUMN
    }

    @Override
    public String toString() {
        String desc = switch (type) {
            case RESULT_MAPPING -> "resultMap";
            case INSERT_COLUMN -> "insert(column)";
            case INSERT_VALUE -> "insert(value)";
            case UPDATE_ASSIGNMENT -> "update(set)";
            case WHERE_CONDITION -> "where";
            case BASE_COLUMN -> "base_column_list";
        };
        String col = column != null ? column : property;
        return col + " [" + desc + " in " + statementId + "]";
    }
}
