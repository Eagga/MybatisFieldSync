package com.eagga.mybatisfieldsync.database;

import com.eagga.mybatisfieldsync.model.FieldInfo;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库字段增强服务，用于将数据库表结构信息与实体类字段关联。
 * 提供更准确的类型映射和字段补全建议。
 */
public final class DatabaseFieldEnhancer {

    private final Project project;
    private final DatabaseConnectionService dbService;

    public DatabaseFieldEnhancer(@NotNull Project project) {
        this.project = project;
        this.dbService = project.getService(DatabaseConnectionService.class);
    }

    /**
     * 增强字段信息，从数据库表结构中获取更准确的类型和注释
     */
    public @NotNull List<FieldInfo> enhanceFields(@NotNull PsiClass entityClass,
                                                    @NotNull List<FieldInfo> fields) {
        if (!dbService.isDatabasePluginAvailable()) {
            return fields;
        }

        String entityName = entityClass.getName();
        if (entityName == null) {
            return fields;
        }

        DatabaseConnectionService.TableInfo tableInfo = dbService.findTableByEntityName(entityName);
        if (tableInfo == null) {
            return fields;
        }

        Map<String, DatabaseConnectionService.ColumnInfo> columnMap = buildColumnMap(tableInfo);
        List<FieldInfo> enhancedFields = new ArrayList<>();

        for (FieldInfo field : fields) {
            String columnName = camelToSnake(field.name());
            DatabaseConnectionService.ColumnInfo columnInfo = columnMap.get(columnName.toLowerCase());

            if (columnInfo != null) {
                // 使用数据库类型映射更准确的 JdbcType
                String enhancedJdbcType = DatabaseTypeMapper.mapToJdbcType(columnInfo.dataType());
                enhancedFields.add(new FieldInfo(
                        field.psiField(),
                        field.name(),
                        field.type(),
                        enhancedJdbcType,
                        field.ownerClass(),
                        field.inherited()
                ));
            } else {
                enhancedFields.add(field);
            }
        }

        return enhancedFields;
    }

    /**
     * 获取数据库表中缺失的字段建议（表中有但实体类中没有的字段）
     */
    public @NotNull List<FieldSuggestion> getMissingFieldSuggestions(@NotNull PsiClass entityClass,
                                                                       @NotNull List<FieldInfo> existingFields) {
        if (!dbService.isDatabasePluginAvailable()) {
            return List.of();
        }

        String entityName = entityClass.getName();
        if (entityName == null) {
            return List.of();
        }

        DatabaseConnectionService.TableInfo tableInfo = dbService.findTableByEntityName(entityName);
        if (tableInfo == null) {
            return List.of();
        }

        Map<String, FieldInfo> fieldMap = new HashMap<>();
        for (FieldInfo field : existingFields) {
            fieldMap.put(camelToSnake(field.name()).toLowerCase(), field);
        }

        List<FieldSuggestion> suggestions = new ArrayList<>();
        for (DatabaseConnectionService.ColumnInfo column : tableInfo.columns()) {
            String columnName = column.columnName().toLowerCase();
            if (!fieldMap.containsKey(columnName)) {
                suggestions.add(new FieldSuggestion(
                        snakeToCamel(column.columnName()),
                        DatabaseTypeMapper.mapToJavaType(column.dataType()),
                        DatabaseTypeMapper.mapToJdbcType(column.dataType()),
                        column.columnName(),
                        column.comment(),
                        column.isPrimaryKey(),
                        column.isNullable()
                ));
            }
        }

        return suggestions;
    }

    /**
     * 验证字段与数据库列的类型是否匹配
     */
    public @Nullable String validateFieldType(@NotNull PsiField field, @NotNull String tableName) {
        if (!dbService.isDatabasePluginAvailable()) {
            return null;
        }

        String columnName = camelToSnake(field.getName());

        for (DatabaseConnectionService.TableInfo tableInfo : dbService.findTablesByName(tableName)) {
            for (DatabaseConnectionService.ColumnInfo column : tableInfo.columns()) {
                if (column.columnName().equalsIgnoreCase(columnName)) {
                    String expectedJavaType = DatabaseTypeMapper.mapToJavaType(column.dataType());
                    String actualJavaType = field.getType().getPresentableText();

                    if (!isTypeCompatible(expectedJavaType, actualJavaType)) {
                        return String.format("字段类型不匹配：数据库列 %s 类型为 %s，建议使用 Java 类型 %s",
                                column.columnName(), column.dataType(), expectedJavaType);
                    }
                }
            }
        }

        return null;
    }

    private @NotNull Map<String, DatabaseConnectionService.ColumnInfo> buildColumnMap(
            @NotNull DatabaseConnectionService.TableInfo tableInfo) {
        Map<String, DatabaseConnectionService.ColumnInfo> map = new HashMap<>();
        for (DatabaseConnectionService.ColumnInfo column : tableInfo.columns()) {
            map.put(column.columnName().toLowerCase(), column);
        }
        return map;
    }

    private boolean isTypeCompatible(@NotNull String expectedType, @NotNull String actualType) {
        // 简单类型名称匹配
        String expectedSimple = expectedType.substring(expectedType.lastIndexOf('.') + 1);
        String actualSimple = actualType.substring(actualType.lastIndexOf('.') + 1);

        if (expectedSimple.equals(actualSimple)) {
            return true;
        }

        // 兼容性检查
        return switch (expectedSimple) {
            case "Integer" -> actualSimple.equals("int") || actualSimple.equals("Long") || actualSimple.equals("long");
            case "Long" -> actualSimple.equals("long") || actualSimple.equals("Integer") || actualSimple.equals("int");
            case "Double" -> actualSimple.equals("double") || actualSimple.equals("Float") || actualSimple.equals("float");
            case "Float" -> actualSimple.equals("float") || actualSimple.equals("Double") || actualSimple.equals("double");
            case "Boolean" -> actualSimple.equals("boolean");
            default -> false;
        };
    }

    private @NotNull String camelToSnake(@NotNull String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    private @NotNull String snakeToCamel(@NotNull String snakeCase) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;

        for (char c : snakeCase.toCharArray()) {
            if (c == '_') {
                capitalizeNext = true;
            } else {
                result.append(capitalizeNext ? Character.toUpperCase(c) : Character.toLowerCase(c));
                capitalizeNext = false;
            }
        }

        return result.toString();
    }

    /**
     * 字段建议记录
     */
    public record FieldSuggestion(
            @NotNull String fieldName,
            @NotNull String javaType,
            @NotNull String jdbcType,
            @NotNull String columnName,
            @Nullable String comment,
            boolean isPrimaryKey,
            boolean isNullable
    ) {
    }
}
