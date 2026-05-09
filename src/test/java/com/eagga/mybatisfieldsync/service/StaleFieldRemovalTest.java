package com.eagga.mybatisfieldsync.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaleFieldRemovalTest {

    private final FieldSyncService service = new FieldSyncService(null);

    @Test
    void removeColumnEntry_shouldRemoveColumnWithTrailingComma() {
        String body = """
                user_name,
                age,
                email,
                """;
        String result = service.removeColumnEntry(body, "age");
        assertFalse(result.contains("age"));
        assertTrue(result.contains("user_name"));
        assertTrue(result.contains("email"));
    }

    @Test
    void removeColumnEntry_shouldRemoveLastColumnWithoutComma() {
        String body = "user_name, age, email";
        String result = service.removeColumnEntry(body, "email");
        assertFalse(result.contains("email"));
        assertTrue(result.contains("user_name"));
        assertTrue(result.contains("age"));
    }

    @Test
    void removeColumnEntry_shouldHandleBacktickWrappedColumn() {
        String body = """
                `user_name`,
                `age`,
                `email`,
                """;
        String result = service.removeColumnEntry(body, "age");
        assertFalse(result.contains("age"));
        assertTrue(result.contains("user_name"));
        assertTrue(result.contains("email"));
    }

    @Test
    void removeValueEntry_shouldRemovePlaceholderWithJdbcType() {
        String body = """
                #{userName,jdbcType=VARCHAR},
                #{age,jdbcType=INTEGER},
                #{email,jdbcType=VARCHAR},
                """;
        String result = service.removeValueEntry(body, "age");
        assertFalse(result.contains("age"));
        assertTrue(result.contains("userName"));
        assertTrue(result.contains("email"));
    }

    @Test
    void removeValueEntry_shouldRemoveSimplePlaceholder() {
        String body = "#{userName}, #{age}, #{email}";
        String result = service.removeValueEntry(body, "age");
        assertFalse(result.contains("age"));
        assertTrue(result.contains("userName"));
        assertTrue(result.contains("email"));
    }

    @Test
    void removeAssignmentEntry_shouldRemoveSetClause() {
        String body = """
                user_name = #{userName,jdbcType=VARCHAR},
                age = #{age,jdbcType=INTEGER},
                email = #{email,jdbcType=VARCHAR},
                """;
        String result = service.removeAssignmentEntry(body, "age", "age");
        assertFalse(result.contains("age"));
        assertTrue(result.contains("user_name"));
        assertTrue(result.contains("email"));
    }

    @Test
    void removeConditionEntry_shouldRemoveWhereConditionWithAnd() {
        String body = """
                and user_name = #{userName}
                and age = #{age}
                and email = #{email}
                """;
        String result = service.removeConditionEntry(body, "age", "age");
        assertFalse(result.contains("age"));
        assertTrue(result.contains("user_name"));
        assertTrue(result.contains("email"));
    }

    @Test
    void removeConditionEntry_shouldRemoveConditionWithoutAnd() {
        String body = """
                user_name = #{userName}
                and age = #{age}
                """;
        String result = service.removeConditionEntry(body, "userName", "user_name");
        assertFalse(result.contains("user_name"));
        assertTrue(result.contains("age"));
    }

    @Test
    void isCommonSqlKeyword_shouldRecognizeKeywords() {
        assertTrue(FieldSyncService.isCommonSqlKeyword("select"));
        assertTrue(FieldSyncService.isCommonSqlKeyword("values"));
        assertTrue(FieldSyncService.isCommonSqlKeyword("where"));
        assertFalse(FieldSyncService.isCommonSqlKeyword("user_name"));
        assertFalse(FieldSyncService.isCommonSqlKeyword("age"));
    }
}
