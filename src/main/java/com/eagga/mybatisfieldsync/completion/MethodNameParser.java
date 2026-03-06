package com.eagga.mybatisfieldsync.completion;

import java.beans.Introspector;
import java.util.*;

public class MethodNameParser {
    private static final Set<String> PREFIXES = Set.of("findBy", "countBy", "deleteBy", "existsBy");
    private static final List<String> OPERATORS = List.of(
        "GreaterThanEqual", "LessThanEqual",
        "IsNotNull", "GreaterThan", "LessThan",
        "NotLike", "NotIn", "Equals", "Like", "In", "Between", "IsNull"
    );
    
    public static class ParseResult {
        public String prefix;
        public List<Condition> conditions = new ArrayList<>();
        
        public static class Condition {
            public String field;
            public String operator;
            public String logic; // "And" or "Or"
        }
    }
    
    public static ParseResult parse(String methodName) {
        ParseResult result = new ParseResult();
        
        for (String prefix : PREFIXES) {
            if (methodName.startsWith(prefix)) {
                result.prefix = prefix;
                String remaining = methodName.substring(prefix.length());
                parseConditions(remaining, result);
                break;
            }
        }
        
        return result;
    }
    
    private static void parseConditions(String text, ParseResult result) {
        if (text == null || text.isBlank()) {
            return;
        }

        int cursor = 0;
        while (cursor < text.length()) {
            int nextConnector = findNextConnector(text, cursor);
            String part = nextConnector >= 0 ? text.substring(cursor, nextConnector) : text.substring(cursor);
            ParseResult.Condition condition = new ParseResult.Condition();

            if (part.startsWith("And")) {
                condition.logic = "And";
                part = part.substring(3);
            } else if (part.startsWith("Or")) {
                condition.logic = "Or";
                part = part.substring(2);
            }

            for (String op : OPERATORS) {
                if (part.endsWith(op)) {
                    condition.operator = op;
                    condition.field = part.substring(0, part.length() - op.length());
                    break;
                }
            }
            
            if (condition.operator == null) {
                condition.operator = "Equals";
                condition.field = part;
            }

            if (condition.field != null && !condition.field.isEmpty()) {
                condition.field = Introspector.decapitalize(condition.field);
                result.conditions.add(condition);
            }

            if (nextConnector < 0) {
                break;
            }
            cursor = nextConnector;
        }
    }

    private static int findNextConnector(String text, int from) {
        for (int i = Math.max(from + 1, 1); i < text.length() - 2; i++) {
            if (text.startsWith("And", i) || text.startsWith("Or", i)) {
                int len = text.startsWith("And", i) ? 3 : 2;
                int nextPos = i + len;
                if (nextPos < text.length() && Character.isUpperCase(text.charAt(nextPos))) {
                    return i;
                }
            }
        }
        return -1;
    }
}
