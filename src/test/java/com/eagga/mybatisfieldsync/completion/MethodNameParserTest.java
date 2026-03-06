package com.eagga.mybatisfieldsync.completion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MethodNameParserTest {

    @Test
    void shouldParseAndDecapitalizeFields() {
        MethodNameParser.ParseResult result = MethodNameParser.parse("findByUserNameAndAgeGreaterThan");

        assertEquals("findBy", result.prefix);
        assertEquals(2, result.conditions.size());
        assertEquals("userName", result.conditions.get(0).field);
        assertEquals("Equals", result.conditions.get(0).operator);
        assertEquals("age", result.conditions.get(1).field);
        assertEquals("GreaterThan", result.conditions.get(1).operator);
        assertEquals("And", result.conditions.get(1).logic);
    }

    @Test
    void shouldPreferLongestOperatorMatch() {
        MethodNameParser.ParseResult result = MethodNameParser.parse("countByAgeLessThanEqual");

        assertEquals("countBy", result.prefix);
        assertEquals(1, result.conditions.size());
        assertEquals("age", result.conditions.get(0).field);
        assertEquals("LessThanEqual", result.conditions.get(0).operator);
    }

    @Test
    void shouldParseIsNullWithoutFieldSuffixDamage() {
        MethodNameParser.ParseResult result = MethodNameParser.parse("existsByDeletedAtIsNull");

        assertEquals("existsBy", result.prefix);
        assertEquals(1, result.conditions.size());
        assertEquals("deletedAt", result.conditions.get(0).field);
        assertEquals("IsNull", result.conditions.get(0).operator);
    }

    @Test
    void shouldHandleUnknownPrefixWithoutConditions() {
        MethodNameParser.ParseResult result = MethodNameParser.parse("queryByName");

        assertEquals(0, result.conditions.size());
        assertNotNull(result.conditions);
    }
}
