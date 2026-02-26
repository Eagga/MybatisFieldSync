package com.eagga.mybatisfieldsync.util;

import java.util.Map;

/**
 * Java 类型到 MyBatis JdbcType 的映射工具。
 */
public final class JdbcTypeUtil {
    private static final Map<String, String> JDBC_TYPE_MAP = Map.ofEntries(
            Map.entry("byte", "TINYINT"),
            Map.entry("java.lang.Byte", "TINYINT"),
            Map.entry("short", "SMALLINT"),
            Map.entry("java.lang.Short", "SMALLINT"),
            Map.entry("int", "INTEGER"),
            Map.entry("java.lang.Integer", "INTEGER"),
            Map.entry("long", "BIGINT"),
            Map.entry("java.lang.Long", "BIGINT"),
            Map.entry("float", "FLOAT"),
            Map.entry("java.lang.Float", "FLOAT"),
            Map.entry("double", "DOUBLE"),
            Map.entry("java.lang.Double", "DOUBLE"),
            Map.entry("boolean", "BOOLEAN"),
            Map.entry("java.lang.Boolean", "BOOLEAN"),
            Map.entry("char", "CHAR"),
            Map.entry("java.lang.Character", "CHAR"),
            Map.entry("java.lang.String", "VARCHAR"),
            Map.entry("java.math.BigDecimal", "DECIMAL"),
            Map.entry("java.util.Date", "TIMESTAMP"),
            Map.entry("java.time.LocalDate", "DATE"),
            Map.entry("java.time.LocalDateTime", "TIMESTAMP"),
            Map.entry("java.time.LocalTime", "TIME"),
            Map.entry("byte[]", "BLOB")
    );

    private JdbcTypeUtil() {
    }

    /**
     * 按 Java 全限定类型名解析 JdbcType，未命中时回退为 OTHER。
     */
    public static String resolveJdbcType(String javaType) {
        return JDBC_TYPE_MAP.getOrDefault(javaType, "OTHER");
    }
}
