package com.eagga.mybatisfieldsync.database;

import com.eagga.mybatisfieldsync.util.JdbcTypeUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 数据库类型映射工具，用于将数据库列类型映射到 Java 类型和 JdbcType。
 * 支持常见数据库（MySQL、PostgreSQL、Oracle、SQL Server）。
 */
public final class DatabaseTypeMapper {

    private static final Map<String, String> DB_TYPE_TO_JAVA_TYPE = new HashMap<>();
    private static final Map<String, String> DB_TYPE_TO_JDBC_TYPE = new HashMap<>();

    static {
        // MySQL / MariaDB
        DB_TYPE_TO_JAVA_TYPE.put("TINYINT", "Integer");
        DB_TYPE_TO_JAVA_TYPE.put("SMALLINT", "Integer");
        DB_TYPE_TO_JAVA_TYPE.put("MEDIUMINT", "Integer");
        DB_TYPE_TO_JAVA_TYPE.put("INT", "Integer");
        DB_TYPE_TO_JAVA_TYPE.put("INTEGER", "Integer");
        DB_TYPE_TO_JAVA_TYPE.put("BIGINT", "Long");
        DB_TYPE_TO_JAVA_TYPE.put("FLOAT", "Float");
        DB_TYPE_TO_JAVA_TYPE.put("DOUBLE", "Double");
        DB_TYPE_TO_JAVA_TYPE.put("DECIMAL", "java.math.BigDecimal");
        DB_TYPE_TO_JAVA_TYPE.put("NUMERIC", "java.math.BigDecimal");
        DB_TYPE_TO_JAVA_TYPE.put("CHAR", "String");
        DB_TYPE_TO_JAVA_TYPE.put("VARCHAR", "String");
        DB_TYPE_TO_JAVA_TYPE.put("TEXT", "String");
        DB_TYPE_TO_JAVA_TYPE.put("TINYTEXT", "String");
        DB_TYPE_TO_JAVA_TYPE.put("MEDIUMTEXT", "String");
        DB_TYPE_TO_JAVA_TYPE.put("LONGTEXT", "String");
        DB_TYPE_TO_JAVA_TYPE.put("DATE", "java.time.LocalDate");
        DB_TYPE_TO_JAVA_TYPE.put("TIME", "java.time.LocalTime");
        DB_TYPE_TO_JAVA_TYPE.put("DATETIME", "java.time.LocalDateTime");
        DB_TYPE_TO_JAVA_TYPE.put("TIMESTAMP", "java.time.LocalDateTime");
        DB_TYPE_TO_JAVA_TYPE.put("YEAR", "Integer");
        DB_TYPE_TO_JAVA_TYPE.put("BLOB", "byte[]");
        DB_TYPE_TO_JAVA_TYPE.put("TINYBLOB", "byte[]");
        DB_TYPE_TO_JAVA_TYPE.put("MEDIUMBLOB", "byte[]");
        DB_TYPE_TO_JAVA_TYPE.put("LONGBLOB", "byte[]");
        DB_TYPE_TO_JAVA_TYPE.put("BINARY", "byte[]");
        DB_TYPE_TO_JAVA_TYPE.put("VARBINARY", "byte[]");
        DB_TYPE_TO_JAVA_TYPE.put("BIT", "Boolean");
        DB_TYPE_TO_JAVA_TYPE.put("BOOLEAN", "Boolean");
        DB_TYPE_TO_JAVA_TYPE.put("JSON", "String");

        // PostgreSQL
        DB_TYPE_TO_JAVA_TYPE.put("INT2", "Integer");
        DB_TYPE_TO_JAVA_TYPE.put("INT4", "Integer");
        DB_TYPE_TO_JAVA_TYPE.put("INT8", "Long");
        DB_TYPE_TO_JAVA_TYPE.put("FLOAT4", "Float");
        DB_TYPE_TO_JAVA_TYPE.put("FLOAT8", "Double");
        DB_TYPE_TO_JAVA_TYPE.put("BOOL", "Boolean");
        DB_TYPE_TO_JAVA_TYPE.put("BYTEA", "byte[]");
        DB_TYPE_TO_JAVA_TYPE.put("JSONB", "String");
        DB_TYPE_TO_JAVA_TYPE.put("UUID", "java.util.UUID");

        // Oracle
        DB_TYPE_TO_JAVA_TYPE.put("NUMBER", "java.math.BigDecimal");
        DB_TYPE_TO_JAVA_TYPE.put("VARCHAR2", "String");
        DB_TYPE_TO_JAVA_TYPE.put("NVARCHAR2", "String");
        DB_TYPE_TO_JAVA_TYPE.put("CLOB", "String");
        DB_TYPE_TO_JAVA_TYPE.put("NCLOB", "String");
        DB_TYPE_TO_JAVA_TYPE.put("RAW", "byte[]");

        // SQL Server
        DB_TYPE_TO_JAVA_TYPE.put("NCHAR", "String");
        DB_TYPE_TO_JAVA_TYPE.put("NVARCHAR", "String");
        DB_TYPE_TO_JAVA_TYPE.put("NTEXT", "String");
        DB_TYPE_TO_JAVA_TYPE.put("IMAGE", "byte[]");
        DB_TYPE_TO_JAVA_TYPE.put("MONEY", "java.math.BigDecimal");
        DB_TYPE_TO_JAVA_TYPE.put("SMALLMONEY", "java.math.BigDecimal");
        DB_TYPE_TO_JAVA_TYPE.put("UNIQUEIDENTIFIER", "java.util.UUID");

        // JdbcType 映射
        DB_TYPE_TO_JDBC_TYPE.put("TINYINT", "TINYINT");
        DB_TYPE_TO_JDBC_TYPE.put("SMALLINT", "SMALLINT");
        DB_TYPE_TO_JDBC_TYPE.put("MEDIUMINT", "INTEGER");
        DB_TYPE_TO_JDBC_TYPE.put("INT", "INTEGER");
        DB_TYPE_TO_JDBC_TYPE.put("INTEGER", "INTEGER");
        DB_TYPE_TO_JDBC_TYPE.put("BIGINT", "BIGINT");
        DB_TYPE_TO_JDBC_TYPE.put("FLOAT", "FLOAT");
        DB_TYPE_TO_JDBC_TYPE.put("DOUBLE", "DOUBLE");
        DB_TYPE_TO_JDBC_TYPE.put("DECIMAL", "DECIMAL");
        DB_TYPE_TO_JDBC_TYPE.put("NUMERIC", "NUMERIC");
        DB_TYPE_TO_JDBC_TYPE.put("CHAR", "CHAR");
        DB_TYPE_TO_JDBC_TYPE.put("VARCHAR", "VARCHAR");
        DB_TYPE_TO_JDBC_TYPE.put("TEXT", "LONGVARCHAR");
        DB_TYPE_TO_JDBC_TYPE.put("TINYTEXT", "VARCHAR");
        DB_TYPE_TO_JDBC_TYPE.put("MEDIUMTEXT", "LONGVARCHAR");
        DB_TYPE_TO_JDBC_TYPE.put("LONGTEXT", "LONGVARCHAR");
        DB_TYPE_TO_JDBC_TYPE.put("DATE", "DATE");
        DB_TYPE_TO_JDBC_TYPE.put("TIME", "TIME");
        DB_TYPE_TO_JDBC_TYPE.put("DATETIME", "TIMESTAMP");
        DB_TYPE_TO_JDBC_TYPE.put("TIMESTAMP", "TIMESTAMP");
        DB_TYPE_TO_JDBC_TYPE.put("YEAR", "INTEGER");
        DB_TYPE_TO_JDBC_TYPE.put("BLOB", "BLOB");
        DB_TYPE_TO_JDBC_TYPE.put("TINYBLOB", "BLOB");
        DB_TYPE_TO_JDBC_TYPE.put("MEDIUMBLOB", "BLOB");
        DB_TYPE_TO_JDBC_TYPE.put("LONGBLOB", "BLOB");
        DB_TYPE_TO_JDBC_TYPE.put("BINARY", "BINARY");
        DB_TYPE_TO_JDBC_TYPE.put("VARBINARY", "VARBINARY");
        DB_TYPE_TO_JDBC_TYPE.put("BIT", "BIT");
        DB_TYPE_TO_JDBC_TYPE.put("BOOLEAN", "BOOLEAN");
        DB_TYPE_TO_JDBC_TYPE.put("JSON", "VARCHAR");
        DB_TYPE_TO_JDBC_TYPE.put("JSONB", "VARCHAR");
        DB_TYPE_TO_JDBC_TYPE.put("UUID", "VARCHAR");
        DB_TYPE_TO_JDBC_TYPE.put("VARCHAR2", "VARCHAR");
        DB_TYPE_TO_JDBC_TYPE.put("NVARCHAR2", "NVARCHAR");
        DB_TYPE_TO_JDBC_TYPE.put("NVARCHAR", "NVARCHAR");
        DB_TYPE_TO_JDBC_TYPE.put("CLOB", "CLOB");
        DB_TYPE_TO_JDBC_TYPE.put("NCLOB", "NCLOB");
    }

    private DatabaseTypeMapper() {
    }

    /**
     * 将数据库列类型映射到 Java 类型
     */
    public static @NotNull String mapToJavaType(@NotNull String dbType) {
        String upperType = dbType.toUpperCase();
        // 移除括号内容（如 VARCHAR(255) -> VARCHAR）
        int parenIndex = upperType.indexOf('(');
        if (parenIndex > 0) {
            upperType = upperType.substring(0, parenIndex).trim();
        }

        return DB_TYPE_TO_JAVA_TYPE.getOrDefault(upperType, "String");
    }

    /**
     * 将数据库列类型映射到 JdbcType
     */
    public static @NotNull String mapToJdbcType(@NotNull String dbType) {
        String upperType = dbType.toUpperCase();
        int parenIndex = upperType.indexOf('(');
        if (parenIndex > 0) {
            upperType = upperType.substring(0, parenIndex).trim();
        }

        return DB_TYPE_TO_JDBC_TYPE.getOrDefault(upperType, "VARCHAR");
    }

    /**
     * 根据列信息推断 JdbcType（优先使用数据库类型映射）
     */
    public static @NotNull String inferJdbcType(@NotNull Project project,
                                                 @Nullable String javaType,
                                                 @Nullable String dbType) {
        if (dbType != null && !dbType.isBlank()) {
            return mapToJdbcType(dbType);
        }

        if (javaType != null && !javaType.isBlank()) {
            return JdbcTypeUtil.resolveJdbcType(project, javaType);
        }

        return "VARCHAR";
    }
}
