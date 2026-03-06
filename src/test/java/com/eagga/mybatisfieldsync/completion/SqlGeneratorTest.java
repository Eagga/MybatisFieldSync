package com.eagga.mybatisfieldsync.completion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlGeneratorTest {

    @Test
    void shouldGenerateExistsSqlWithCaseWhen() {
        MethodNameParser.ParseResult result = MethodNameParser.parse("existsByUserName");

        String sql = SqlGenerator.generateSql(result, "user", List.of());

        assertEquals("SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END FROM user WHERE user_name = #{userName}", sql);
    }

    @Test
    void shouldGenerateBetweenSqlWithStartAndEndParams() {
        MethodNameParser.ParseResult result = MethodNameParser.parse("findByCreatedAtBetween");

        String sql = SqlGenerator.generateSql(result, "orders", List.of());

        assertEquals("SELECT * FROM orders WHERE created_at BETWEEN #{createdAtStart} AND #{createdAtEnd}", sql);
    }

    @Test
    void shouldGenerateInSqlWithForeach() {
        MethodNameParser.ParseResult result = MethodNameParser.parse("findByStatusIn");

        String sql = SqlGenerator.generateSql(result, "orders", List.of());

        assertTrue(sql.contains("status IN <foreach collection=\"status\" item=\"item\""));
        assertTrue(sql.contains("#{item}"));
    }

    @Test
    void shouldFallbackToTruePredicateWhenNoConditions() {
        MethodNameParser.ParseResult result = MethodNameParser.parse("findBy");

        String sql = SqlGenerator.generateSql(result, "orders", List.of());

        assertEquals("SELECT * FROM orders WHERE 1 = 1", sql);
    }
}
