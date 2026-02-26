package com.eagga.mybatisfieldsync.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从现有 XML 文本中探测缩进风格，保证写回格式一致。
 */
public final class IndentUtil {
    private static final Pattern INDENT_PATTERN = Pattern.compile("(?m)^(\\s+)\\S");

    private IndentUtil() {
    }

    /**
     * 返回一个缩进单位：制表符、4 空格或 2 空格。
     */
    public static String detectIndentUnit(String text) {
        Matcher matcher = INDENT_PATTERN.matcher(text);
        if (matcher.find()) {
            String indent = matcher.group(1);
            if (indent.contains("\t")) {
                return "\t";
            }
            return indent.length() >= 4 ? "    " : "  ";
        }
        return "    ";
    }
}
