package com.eagga.mybatisfieldsync.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TypeMappingUtilTest {

    @Test
    void shouldPreferTypeHandlerJdbcTypeOverride() {
        TypeMappingUtil.ResolvedTypeMapping mapping = TypeMappingUtil.resolve(
                "java.time.LocalDateTime",
                "java.time.LocalDateTime=DATE",
                "java.time.LocalDateTime=com.demo.LocalDateTimeHandler,jdbcType=TIMESTAMP",
                "OTHER");

        assertEquals("TIMESTAMP", mapping.jdbcType());
        assertEquals("com.demo.LocalDateTimeHandler", mapping.typeHandler());
    }

    @Test
    void shouldKeepLegacyJdbcTypeWhenTypeHandlerDoesNotOverride() {
        TypeMappingUtil.ResolvedTypeMapping mapping = TypeMappingUtil.resolve(
                "com.demo.Money",
                "com.demo.Money=DECIMAL",
                "com.demo.Money=com.demo.MoneyTypeHandler",
                "OTHER");

        assertEquals("DECIMAL", mapping.jdbcType());
        assertEquals("com.demo.MoneyTypeHandler", mapping.typeHandler());
    }

    @Test
    void shouldReturnFallbackWhenNoCustomMappingExists() {
        TypeMappingUtil.ResolvedTypeMapping mapping = TypeMappingUtil.resolve(
                "com.demo.Unknown",
                "",
                "",
                "VARCHAR");

        assertEquals("VARCHAR", mapping.jdbcType());
        assertNull(mapping.typeHandler());
    }
}
