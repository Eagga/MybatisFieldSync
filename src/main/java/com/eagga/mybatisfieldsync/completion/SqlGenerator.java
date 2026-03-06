package com.eagga.mybatisfieldsync.completion;

import com.eagga.mybatisfieldsync.util.NameUtil;
import java.util.List;
import java.util.stream.Collectors;

public class SqlGenerator {
    
    public static String generateSql(MethodNameParser.ParseResult parseResult, String tableName, List<String> allColumns) {
        String prefix = parseResult.prefix;
        
        if ("findBy".equals(prefix)) {
            return generateSelect(parseResult, tableName, allColumns);
        } else if ("countBy".equals(prefix)) {
            return generateCount(parseResult, tableName);
        } else if ("deleteBy".equals(prefix)) {
            return generateDelete(parseResult, tableName);
        } else if ("existsBy".equals(prefix)) {
            return generateExists(parseResult, tableName);
        }
        
        return "";
    }
    
    private static String generateSelect(MethodNameParser.ParseResult parseResult, String tableName, List<String> columns) {
        String columnList = columns.isEmpty() ? "*" : String.join(", ", columns);
        String where = buildWhereClause(parseResult.conditions);
        return String.format("SELECT %s FROM %s WHERE %s", columnList, tableName, where);
    }
    
    private static String generateCount(MethodNameParser.ParseResult parseResult, String tableName) {
        String where = buildWhereClause(parseResult.conditions);
        return String.format("SELECT COUNT(*) FROM %s WHERE %s", tableName, where);
    }
    
    private static String generateDelete(MethodNameParser.ParseResult parseResult, String tableName) {
        String where = buildWhereClause(parseResult.conditions);
        return String.format("DELETE FROM %s WHERE %s", tableName, where);
    }
    
    private static String generateExists(MethodNameParser.ParseResult parseResult, String tableName) {
        String where = buildWhereClause(parseResult.conditions);
        return String.format("SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END FROM %s WHERE %s", tableName, where);
    }
    
    private static String buildWhereClause(List<MethodNameParser.ParseResult.Condition> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return "1 = 1";
        }
        return conditions.stream()
            .map(SqlGenerator::buildCondition)
            .collect(Collectors.joining(" "));
    }
    
    private static String buildCondition(MethodNameParser.ParseResult.Condition condition) {
        String column = NameUtil.camelToSnake(condition.field);
        String logic = condition.logic != null ? condition.logic.toUpperCase() + " " : "";
        String op = getOperator(condition.operator, column, condition.field);
        return logic + op;
    }
    
    private static String getOperator(String operator, String column, String field) {
        return switch (operator) {
            case "Equals" -> column + " = #{" + field + "}";
            case "GreaterThan" -> column + " > #{" + field + "}";
            case "LessThan" -> column + " < #{" + field + "}";
            case "GreaterThanEqual" -> column + " >= #{" + field + "}";
            case "LessThanEqual" -> column + " <= #{" + field + "}";
            case "Like" -> column + " LIKE #{" + field + "}";
            case "NotLike" -> column + " NOT LIKE #{" + field + "}";
            case "In" ->
                column + " IN "
                + "<foreach collection=\"" + field + "\" item=\"item\" open=\"(\" separator=\",\" close=\")\">"
                + "#{item}"
                + "</foreach>";
            case "NotIn" ->
                column + " NOT IN "
                + "<foreach collection=\"" + field + "\" item=\"item\" open=\"(\" separator=\",\" close=\")\">"
                + "#{item}"
                + "</foreach>";
            case "Between" -> column + " BETWEEN #{" + field + "Start} AND #{" + field + "End}";
            case "IsNull" -> column + " IS NULL";
            case "IsNotNull" -> column + " IS NOT NULL";
            default -> column + " = #{" + field + "}";
        };
    }
}
