package com.eagga.mybatisfieldsync.completion;

import java.util.*;

public class MethodNameParser {
    private static final Set<String> PREFIXES = Set.of("findBy", "countBy", "deleteBy", "existsBy");
    private static final Set<String> OPERATORS = Set.of(
        "Equals", "GreaterThan", "LessThan", "GreaterThanEqual", "LessThanEqual",
        "Like", "NotLike", "In", "NotIn", "Between", "IsNull", "IsNotNull"
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
        String[] parts = text.split("(?=And|Or)");
        for (String part : parts) {
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
            
            if (!condition.field.isEmpty()) {
                result.conditions.add(condition);
            }
        }
    }
}
